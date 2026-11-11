package metaticket.plugin.mailtemplate

import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.font.FontProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.plugin.mailtemplate.pojo.PluginMailMetaData
import metaticket.plugin.mailtemplate.pojo.db.MailTemplateDocument
import metaticket.plugin.mailtemplate.pojo.db.PluginMailOrderData
import metaticket.qrcode.grpc.QRCodeServiceGrpcKt
import metaticket.qrcode.grpc.QrcodeMessage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.util.*


class PluginMailTemplateQRCode : KoinComponent, PluginMailTemplate, Logging {
    private val qrcodeStub: QRCodeServiceGrpcKt.QRCodeServiceCoroutineStub by inject()

    private val currencyFormat: () -> NumberFormat = {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"))
    }

    private val _fontProvider: FontProvider = DefaultFontProvider(true, true, true).apply {
        addStandardPdfFonts()
        addSystemFonts()
        addFont("./conf/font/Montserrat-Regular.ttf")
        addFont("./conf/font/Montserrat-Bold.ttf")
    }


    // Set up converter properties
    /**
     * To avoid the error "Pdf indirect object belongs to other PDF document"
     * The converterProperties must be re-created for each PDF file to avoid sharing the same properties,
     * as well as the font provider instance, across different documents.
     *
     * By turning `converterProperties` into a function, we ensure that a new `ConverterProperties` object is instantiated for each PDF,
     * which resolves the issue of object reuse and ensures that each document is handled independently.
     */
    private val converterProperties: () -> ConverterProperties = {
        ConverterProperties().apply {
            setFontProvider(_fontProvider)
        }
    }

    override suspend fun buildSubject(data: Any): String {
        return "Xác nhận mua vé tham dự sự kiện $data"
    }

    override suspend fun buildBody(metaData: PluginMailMetaData, data: Any, document: MailTemplateDocument): String {
        data as PluginMailOrderData
        val orderData = data.orderSummary
        val mailTemplate = document.templates["root"]!!
        val priceTemplate = document.templates["priceItem"]!!

        val paymentInfo = if(orderData.hasPaymentInfo()) orderData.paymentInfo else null
        val orderDetail = orderData.orderDetail
        val recipientInfo = if (orderData.hasRecipientInfo()) orderData.recipientInfo else null
        val buyerInfo = if (orderData.hasBuyerInfo()) orderData.buyerInfo else null
        val customField =  orderData.customFieldsList

        var mailBody = mailTemplate
            .replace("{{event-name}}", orderDetail.eventName)
            .replace("{{pay-time}}", paymentInfo?.payTime ?: "")
            .replace("{{order-code}}", orderData.orderCode)
            .replace("{{pay-method}}", paymentInfo?.payMethod ?: "")
            .replace("{{pay-status}}", paymentInfo?.payStatus ?: "")
            .replace("{{event-name}}", orderDetail.eventName)
            .replace("{{event-time}}", orderDetail.eventTime)
            .replace("{{event-venue}}", orderDetail.venue)
            .replace("{{total}}", currencyFormat().format(orderDetail.total))
            .replace("{{customer-name}}", buyerInfo?.name ?: "")
            .replace("{{customer-phone}}", buyerInfo?.phone ?: "")
            .replace("{{customer-email}}", buyerInfo?.email ?: "")
            .replace("{{supporter}}", metaData.supporter)
            .replace("{{hotline}}", metaData.hotline)
            .replace("{{end-point}}", metaData.endPoint)

        mailBody = if (recipientInfo != null) {
            mailBody
                .replace("{{display-delivery}}", "block")
                .replace("{{delivery-name}}", recipientInfo.name)
                .replace("{{delivery-phone}}", recipientInfo.phone)
                .replace("{{delivery-address}}", recipientInfo.address)
        } else {
            mailBody
                .replace("{{display-delivery}}", "none")
        }

        (if (orderDetail.hasOrderNote()) orderDetail.orderNote else null)?.let {
            mailBody = mailBody.replace("{{display-order-note}}", "block")
            mailBody = mailBody.replace("{{order-note}}", it)
        } ?: run {
            mailBody = mailBody.replace("{{display-order-note}}", "none")
        }

        var priceItems = ""
        orderDetail.pricesList.forEach { o ->
            priceItems = priceItems.plus(
                priceTemplate
                    .replace("{{price-name}}", o.name)
                    .replace("{{quantity}}", o.quantity.toString())
                    .replace("{{price}}", currencyFormat().format(o.price))
            )
        }
        mailBody = mailBody.replace("{{ticket-item}}", priceItems)

        mailBody = if (orderDetail.promotionsList.isNotEmpty()) {
            val promotionTemplate = document.templates["promotionItem"]!!
            var promotionItems = ""
            orderDetail.promotionsList.forEach { p ->
                promotionItems = promotionItems.plus(
                    promotionTemplate
                        .replace("{{promotion-name}}", p.name)
                        .replace("{{promotion-price}}", (-p.price).toString())
                )
            }
            mailBody = mailBody.replace("{{promotion-item}}", promotionItems)
            mailBody.replace("{{display-promotion}}", "block")
        } else {
            mailBody.replace("{{display-promotion}}", "none")
        }

        mailBody = if (!customField.isNullOrEmpty() && !document.templates["customFieldItem"].isNullOrBlank()) {
            val customFieldTemplate = document.templates["customFieldItem"]!!
            var customFieldItems = ""
            customField.forEach { f ->
                customFieldItems = customFieldItems.plus(
                    customFieldTemplate
                        .replace("{{customField-name}}", f.name)
                        .replace("{{customField-value}}", f.value.toString())
                )
            }
            mailBody
                .replace("{{display-custom-field}}", "block")
                .replace("{{custom-field-item}}", customFieldItems)
        } else {
            mailBody.replace("{{display-custom-field}}", "none")
        }

        return mailBody
    }

    override suspend fun buildAttachment(
        metaData: PluginMailMetaData,
        data: Any,
        document: MailTemplateDocument
    ): Map<String, Pair<String, ByteArray>> {
        data as PluginMailOrderData
        val showId = data.showId
        val sessionId = data.sessionId
        val orderData = data.orderSummary
        val orderDetail = if (orderData.hasOrderDetail()) orderData.orderDetail else null
        val buyerInfo = if (orderData.hasBuyerInfo()) orderData.buyerInfo else null
        val orderDate = Date(orderData.createdAt)

        val ticketPdf = document.attachments?.ticketPdf ?: return emptyMap()

        val attachments = HashMap<String, Pair<String, ByteArray>>()

        val eventSquareImage = metaData.getImage!!(data.eventSquareImageUrl!!)
        val eventNameImage = metaData.getImage!!(data.eventNameImageUrl!!)
        val metaticketLogoImage = metaData.getImage!!("${metaData.endPoint}/assets/metaticket/metaticket_logo.png")

        var template = ticketPdf.template
        ticketPdf.replacement.forEach { (k, v) ->
            template = template.replace("{{$k}}", v)
        }
        template = template.replace("{{event-square-img}}", eventSquareImage)
        template = template.replace("{{event-name-img}}", eventNameImage)
        template = template.replace("{{metaticket-logo}}", metaticketLogoImage)
        template = template.replace("{{order-code}}", orderData.orderCode)
        template = template.replace("{{customer-name}}", buyerInfo?.name ?: "")
        template = template.replace("{{order-date}}", orderDate.date.toString())
        template = template.replace("{{order-month}}", (orderDate.month + 1).toString())
        template = template.replace("{{order-year}}", (orderDate.year + 1900).toString())

        orderDetail?.pricesList?.forEach { ticket ->
            for (i in 0 until ticket.quantity) {
                val generateQRCodeRequest = QrcodeMessage.GenerateQRCodeRequest.newBuilder().apply {
                    this.showId = showId
                    this.sessionId = sessionId
                    orderId = orderData.orderId
                    orderCode = orderData.orderCode
                    eventName = orderDetail.eventName
                    priceCat = ticket.priceCat
                    sid = ticket.sid
                    no = i + 1
                }.build()
                val resp = try {
                    withContext(Dispatchers.IO) {
                        qrcodeStub.generateQRCode(generateQRCodeRequest)
                    }.value.toByteArray()
                } catch (t: Throwable) {
                    logger.error("session {}, order {} failed to generate qrcode... ", sessionId, orderData.orderCode, t)
                    throw t
                }
                val qrCodeBase64 = Base64.getEncoder().encodeToString(resp)
                var template1 = template.replace("{{order-qrcode}}", qrCodeBase64)
                template1 = template1.replace("{{ticket-cat}}", ticket.priceCat)
                template1 = template1.replace("{{ticket-sid}}", ticket.sid)
                val pdfFile = try {
                    itextHtml2PDF(template1, converterProperties())
                } catch (t: Throwable) {
                    logger.error("session {}, order {} failed to build pdf file... ", sessionId, orderData.orderCode, t)
                    throw t
                }
                val filename = "${ticketPdf.filename}_${ticket.priceCat}_${ticket.sid}_$i.pdf"

                attachments[filename] = Pair("application/pdf", pdfFile)
            }
        }

        attachments.putAll(super.buildAttachment(metaData, data, document))

        return attachments
    }
}

fun itextHtml2PDF(html: String, converterProperties: ConverterProperties): ByteArray {
    val ops = ByteArrayOutputStream()
    // Initialize the PDF writer and document
    val writer = com.itextpdf.kernel.pdf.PdfWriter(ops)
    val pdfDoc: PdfDocument = PdfDocument(writer)


    // Convert the HTML to PDF
    HtmlConverter.convertToPdf(html, pdfDoc, converterProperties)

    val byteArray = ops.toByteArray()
    pdfDoc.close()
    ops.close()

    return byteArray
}

fun itextHtml2PDFToFile(html: String, outputPath: String) {
    try {
        // Initialize the PDF writer and document
        val fileOutputStream = FileOutputStream(outputPath)
        val writer = PdfWriter(fileOutputStream)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)

        // Set up the FontProvider for custom font handling
        val fontProvider: FontProvider = DefaultFontProvider(true, true, true)
        fontProvider.addStandardPdfFonts()
        fontProvider.addSystemFonts()
        fontProvider.addFont("./conf/font/Montserrat-Regular.ttf")
        fontProvider.addFont("./conf/font/Montserrat-Bold.ttf")

        // Set up converter properties
        val converterProperties = ConverterProperties()
        converterProperties.setFontProvider(fontProvider)

        // Convert the HTML to PDF and write it to file
        HtmlConverter.convertToPdf(html, pdfDoc, converterProperties)

        // Close the document and writer
        pdfDoc.close()
        fileOutputStream.close()

        println("PDF saved successfully to $outputPath")
    } catch (e: IOException) {
        e.printStackTrace()
    }
}