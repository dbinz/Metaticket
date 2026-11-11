package metaticket.notification.processor

import io.ktor.server.application.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Semaphore
import metaticket.common.logger.Logging
import metaticket.notification.cache.ImageCache
import metaticket.notification.cache.MailTemplateCache
import metaticket.notification.mail.client.MailClient
import metaticket.notification.mail.grpc.MailMessage
import metaticket.notification.mail.grpc.MailMessage.SendMailOrderRequest
import metaticket.notification.plugin.template.service.PluginMailTemplateService
import metaticket.plugin.mailtemplate.pojo.PluginMailMetaData
import metaticket.plugin.mailtemplate.pojo.db.AccessoryMailLine
import metaticket.plugin.mailtemplate.pojo.db.AccessoryMailPayload
import metaticket.plugin.mailtemplate.pojo.db.PluginMailOrderData

/**
 * A processor for sending emails, managing concurrent requests using a Semaphore and Channel.
 */
class SendMailProcessor(
    private val env: ApplicationEnvironment,
    private val mailClient: MailClient,
    private val pluginMailTemplateService: PluginMailTemplateService,
    private val mailTemplateCache: MailTemplateCache,
    private val imageCache: ImageCache,
) : Logging {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private val sender = env.config.property("mailer.sender").getString()
    private val supporter = env.config.property("mailer.supporter").getString()
    private val hotline = env.config.property("mailer.hotline").getString()
    private val endPoint = env.config.property("mailer.endPoint").getString()
    private val maxConcurrentRequests = env.config.property("mailProcessor.maxProcessor").getString().toInt()
    private val waitTimeToFullInS = env.config.property("ticketQRCode.waitTimeToFullInS").getString().toLong()

    private val mailChannel = Channel<Pair<CompletableDeferred<Unit>, Any>>(Channel.BUFFERED)
    private val semaphore = Semaphore(maxConcurrentRequests)

    /**
     * Submits a mail sending request to the channel.
     * @param reqData The request data.
     * @return A Deferred object representing the completion of the request.
     */
    fun submit(reqData: Any): Deferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        val result = mailChannel.trySend(deferred to reqData)
        if (result.isFailure) {
            val exception = result.exceptionOrNull()
            if (exception == null) {
                deferred.completeExceptionally(Exception("Đầy bộ nhớ"))
            } else {
                deferred.completeExceptionally(exception)
            }
        }
        return deferred
    }

    /**
     * Starts the mail processing coroutines.
     */
    fun start() = scope.launch {
        logger.info("Starting SendMailProcessor with $maxConcurrentRequests concurrent requests.")
        repeat(maxConcurrentRequests) {
            launch { processMailQueue(mailChannel) }
        }
    }

    /**
     * Stops the mail processing.
     */
    fun stop() {
        mailChannel.close()
        scope.cancel()
    }

    /**
     * Processes the mail channel, sending emails concurrently.
     * @param messages ReceiveChannel contains email request to process
     */
    private suspend fun processMailQueue(messages: ReceiveChannel<Pair<CompletableDeferred<Unit>, Any>>) = coroutineScope {
        messages.consumeEach { (deferred, request) ->
            launch {
                semaphore.acquire()
                try {
                    if (request is SendMailOrderRequest) sendMailOrder(request)
                    else if (request is MailMessage.SendMailOTPRequest) sendMailOTP(request)
                    else if (request is MailMessage.SendMailAccessoryOrderRequest) sendMailAccessoryOrder(request)
                    deferred.complete(Unit)
                } catch (e: Throwable) {
                    logger.error("Failed to send mail... ", e)
                    deferred.completeExceptionally(e)
                } finally {
                    semaphore.release()
                }
            }
        }
    }

    private suspend fun sendMailOTP(request: MailMessage.SendMailOTPRequest) {
        val mailPlugin = request.mailPlugin
        val mailTemplate = request.mailTemplate
        val email = request.email
        logger.info("[{}] Sending mail otp to $email", request.reqId)
        val plugin = pluginMailTemplateService.getPlugin(mailPlugin)
            ?: run {
                logger.error("Mail plugin not found: $mailPlugin")
                throw RuntimeException("Mail plugin not found: $mailPlugin")
            }
        val templateDoc = mailTemplateCache.get(mailTemplate)
            ?: run {
                logger.error("Mail template not found: $mailTemplate")
                throw RuntimeException("Mail template not found: $mailTemplate")
            }
        mailClient.sendMail(
            fromAddress = sender,
            toAddress = email,
            subject = plugin.buildSubject(request),
            bodyHtml = plugin.buildBody(PluginMailMetaData(endPoint, supporter, hotline), request, templateDoc),
            attachment = plugin.buildAttachment(
                PluginMailMetaData(endPoint, supporter, hotline, imageCache::get),
                request,
                templateDoc
            )
        )
        logger.info("[{}] Mail otp sent successfully to $email", request.reqId)
    }

    /**
     * Sends an email using the provided request data.
     * @param request The mail sending request.
     */
    private suspend fun sendMailOrder(request: SendMailOrderRequest) {
        val mailPluginTemplate = request.mailPluginTemplate
        val orderSummary = request.orderSummary

        logger.info("Sending mail order to ${orderSummary.email}")

        val plugin = pluginMailTemplateService.getPlugin(mailPluginTemplate.mailPlugin)
            ?: run {
                logger.error("Mail plugin not found: ${mailPluginTemplate.mailPlugin}")
                throw RuntimeException("Mail plugin not found: ${mailPluginTemplate.mailPlugin}")
            }
        val templateDoc = mailTemplateCache.get(mailPluginTemplate.mailTemplate)
            ?: run {
                logger.error("Mail template not found: ${mailPluginTemplate.mailTemplate}")
                throw RuntimeException("Mail template not found: ${mailPluginTemplate.mailTemplate}")
            }

        val mailData = PluginMailOrderData(
            showId = request.showId,
            sessionId = request.sessionId,
            orderSummary = orderSummary,
            eventSquareImageUrl = request.eventSquareImageUrl,
            eventNameImageUrl = request.eventNameImageUrl,
            timeoutToFullInS = waitTimeToFullInS,
            // hasQrcodeEmail() chỉ cho biết field có được gửi; phải đọc giá trị, nếu không suất tắt QR vẫn bị đính kèm.
            qrcodeEmail = request.hasQrcodeEmail() && request.qrcodeEmail
        )

        mailClient.sendMail(
            fromAddress = sender,
            toAddress = orderSummary.email,
            subject = plugin.buildSubject(orderSummary.orderDetail.eventName),
            bodyHtml = plugin.buildBody(PluginMailMetaData(endPoint, supporter, hotline), mailData, templateDoc),
            attachment = plugin.buildAttachment(
                PluginMailMetaData(endPoint, supporter, hotline, imageCache::get),
                mailData,
                templateDoc
            )
        )
        logger.info("Mail order sent successfully to ${orderSummary.email}")
    }

    /**
     * Sends the accessory-order confirmation email.
     *
     * Maps the dedicated [MailMessage.SendMailAccessoryOrderRequest] into the plugin's `data`
     * argument (an [AccessoryMailPayload]) and runs it through the same plugin pipeline as
     * [sendMailOrder]. The ticket flow and its plugins are untouched.
     */
    private suspend fun sendMailAccessoryOrder(request: MailMessage.SendMailAccessoryOrderRequest) {
        val mailPlugin = request.mailPlugin
        val mailTemplate = request.mailTemplate
        val email = request.recipientEmail

        logger.info("accessory order {} processing mail to {}", request.orderCode, email)

        val plugin = pluginMailTemplateService.getPlugin(mailPlugin)
            ?: run {
                logger.error("Mail plugin not found: $mailPlugin")
                throw RuntimeException("Mail plugin not found: $mailPlugin")
            }
        val templateDoc = mailTemplateCache.get(mailTemplate)
            ?: run {
                logger.error("Mail template not found: $mailTemplate")
                throw RuntimeException("Mail template not found: $mailTemplate")
            }

        val payload = AccessoryMailPayload(
            orderCode = request.orderCode,
            customerName = request.customerName,
            customerEmail = email,
            customerPhone = request.customerPhone,
            showName = request.showName,
            lines = request.linesList.map { line ->
                AccessoryMailLine(
                    name = line.productName,
                    // empty proto string means the line has no variant (single-SKU) -> null
                    variantLabel = line.variantLabel.takeIf { it.isNotEmpty() },
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    lineTotal = line.lineTotal,
                )
            },
            subtotal = request.subtotal,
            discountCode = if (request.hasDiscount) request.discountCode.takeIf { it.isNotEmpty() } else null,
            discountAmount = if (request.hasDiscount) request.discountAmount else 0,
            total = request.total,
            pickupNote = request.pickupNote.takeIf { it.isNotEmpty() },
        )

        mailClient.sendMail(
            fromAddress = sender,
            toAddress = email,
            subject = plugin.buildSubject(payload),
            bodyHtml = plugin.buildBody(PluginMailMetaData(endPoint, supporter, hotline), payload, templateDoc),
            attachment = plugin.buildAttachment(
                PluginMailMetaData(endPoint, supporter, hotline, imageCache::get),
                payload,
                templateDoc
            )
        )
        logger.info("accessory order {} success sent mail to {}", request.orderCode, email)
    }
}