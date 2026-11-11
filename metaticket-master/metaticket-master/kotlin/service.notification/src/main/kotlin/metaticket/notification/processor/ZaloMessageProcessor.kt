package metaticket.notification.processor

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Semaphore

import metaticket.common.logger.Logging
import metaticket.notification.gateway.ZaloNotificationServiceGateway
import metaticket.notification.plugin.template.service.PluginZNSTemplateService
import metaticket.plugin.zns.PluginZNS
import metaticket.plugin.zns.model.ZaloMessageRequest
import metaticket.plugin.zns.model.ZaloMessageResponse

/**
 * ZaloMessageProcessor lên lịch gửi tin nhắn đến Zalo, xử lý tối đa [maxConcurrentRequests] yêu cầu đồng thời.
 */
class ZaloMessageProcessor(
    private val pluginZNSTemplateService: PluginZNSTemplateService,
    private val znsGateway: ZaloNotificationServiceGateway,
    private val mapper: ObjectMapper
) : Logging {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private val maxConcurrentRequests: Int = 5
    private val messageChannel = Channel<ZaloMessageRequest>(Channel.BUFFERED) // Sử dụng Channel làm hàng đợi
    private val semaphore = Semaphore(maxConcurrentRequests) // Semaphore để giới hạn đồng thời
    private var isStopping = false

    /**
     * Gửi tin nhắn đến ZaloMessageSender với backpressure.
     *
     * @param message Tin nhắn cần gửi.
     */
    fun sendMessage(message: ZaloMessageRequest) {
        if (isStopping) {
            logger.warn("Zalo message [{}] rejected, service is stopping", message.metadata.ref_id)
            message.respond.complete(ZaloMessageResponse(1, "Hệ thống đang tạm thời gián đoạn, vui lòng thử lại sau"))
            return
        }
        val result = messageChannel.trySend(message)
        if (result.isFailure) {
            val exception = result.exceptionOrNull()
            if (exception == null) {
                message.respond.complete(ZaloMessageResponse(1, "Hệ thống đang tạm thời gián đoạn, vui lòng thử lại sau"))
                logger.warn("Zalo message [{}] rejected due to channel full (backpressure)", message.metadata.ref_id)
            } else {
                message.respond.complete(ZaloMessageResponse(1, "Lỗi không xác định từ máy chủ, vui lòng liên hệ hỗ trợ!", exception.message))
                logger.error("Zalo message [{}] failed to send with exception... ", message.metadata.ref_id, exception)
            }
        }
    }

    /**
     * Bắt đầu vòng lặp xử lý tin nhắn.
     */
    fun start() {
        scope.launch {
            processMessages(messageChannel)
        }
    }

    /**
     * Dừng vòng lặp xử lý tin nhắn.
     */
    fun stop() {
        isStopping = true
        messageChannel.close()
        scope.cancel()
        logger.info("Zalo message processor stopped")
    }

    /**
     * Xử lý tin nhắn từ Channel.
     *
     * @param messages ReceiveChannel chứa các tin nhắn cần xử lý.
     */
    private suspend fun processMessages(messages: ReceiveChannel<ZaloMessageRequest>) = coroutineScope {
        messages.consumeEach { message ->
            launch { // Khởi chạy coroutine cho mỗi tin nhắn
                semaphore.acquire() // Lấy semaphore trước khi xử lý
                try {
                    val pluginZNS: PluginZNS = pluginZNSTemplateService.getPlugin(message.metadata.plugin)
                        ?: run {
                            logger.error("Plugin not found for message: {}", message.metadata)
                            throw RuntimeException("Plugin not found for message: ${message.metadata.ref_id}")
                        }
                    val templateData = pluginZNS.buildTemplateData(message.metadata, message.data)
                    val smsMsg = pluginZNS.buildSMSTemplateMsg(message.metadata, templateData)
                    val rsp = znsGateway.sendMessage(message.metadata, templateData, smsMsg)
                    if (rsp.code == 1) message.respond.complete(ZaloMessageResponse(0, rsp.message, mapper.writeValueAsString(rsp.data)))
                    else if (rsp.code == -12 || rsp.code == -13) message.respond.complete(ZaloMessageResponse(1, rsp.message, mapper.writeValueAsString(rsp.data)))
                    else message.respond.complete(ZaloMessageResponse(1, "Lỗi hệ thống, vui lòng thử lại sau", mapper.writeValueAsString(rsp.data)))
                    logger.info("Zalo message [{}] sent successfully {}", message.metadata.ref_id, rsp)
                } catch (e: Throwable) {
                    logger.error("Zalo message [{}] send exception... ", message.metadata.ref_id, e)
                    message.respond.complete(ZaloMessageResponse(1, "Lỗi không xác định từ máy chủ, vui lòng thử lại sau!", e.message))
                } finally {
                    semaphore.release() // Giải phóng semaphore
                }
            }
        }
    }
}