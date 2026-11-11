package metaticket.plugin.mailtemplate

import metaticket.plugin.mailtemplate.pojo.PluginMailMetaData
import metaticket.plugin.mailtemplate.pojo.db.MailTemplateDocument
import metaticket.plugin.mailtemplate.pojo.db.PluginMailOrderData
import java.text.NumberFormat
import java.util.*


/**
 *
 * @author dongcatchay
 */

class PluginMailTemplateBasic(): PluginMailTemplate {

    private val currencyFormat: () -> NumberFormat = {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"))
    }

    override suspend fun buildSubject(data: Any): String {
        return "Xác nhận vé tham dự sự kiện $data"
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
            .replace("{{pay-method}}", paymentInfo?.payMethod ?:"")
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

        mailBody = if (!customField.isNullOrEmpty()) {
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
}
