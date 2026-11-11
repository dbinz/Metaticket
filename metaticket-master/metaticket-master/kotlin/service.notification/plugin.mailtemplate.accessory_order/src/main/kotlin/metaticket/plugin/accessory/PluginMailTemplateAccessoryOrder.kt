package metaticket.plugin.accessory

import metaticket.plugin.mailtemplate.PluginMailTemplate
import metaticket.plugin.mailtemplate.pojo.PluginMailMetaData
import metaticket.plugin.mailtemplate.pojo.db.AccessoryMailPayload
import metaticket.plugin.mailtemplate.pojo.db.MailTemplateDocument
import java.text.NumberFormat
import java.util.Locale

/**
 * Fills **order-derived** placeholders into an accessory confirmation HTML template.
 *
 * Show-specific copy (hero brand, hard-coded venues/dates, merch policy notes, colors)
 * stays in the Mongo template HTML — do **not** invent plugin tokens for those.
 *
 * Placeholders filled here (underscore style, matching existing Merch templates):
 * - `{{customer_name}}`, `{{customer_email}}`, `{{customer_phone}}`
 * - `{{order_code}}`, `{{show_name}}`
 * - `{{items_table}}` — HTML `<tr>…</tr>` rows for each line
 * - `{{subtotal}}`, `{{total}}`
 * - `{{discount_line}}` — one HTML `<tr>…</tr>` or empty when no discount
 * - `{{pickup_note}}`
 * - `{{hotline}}`, `{{supporter}}`, `{{end_point}}` — from notification meta
 */
class PluginMailTemplateAccessoryOrder : PluginMailTemplate {

    private val currencyFormat: () -> NumberFormat = {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"))
    }

    /** Subject uses order code + show name from the payload. */
    override suspend fun buildSubject(data: Any): String {
        data as AccessoryMailPayload
        return "Xác nhận đặt hàng — ${data.showName} (${data.orderCode})"
    }

    /** Replaces only order/meta tokens in the `root` HTML fragment. */
    override suspend fun buildBody(
        metaData: PluginMailMetaData,
        data: Any,
        document: MailTemplateDocument,
    ): String {
        data as AccessoryMailPayload
        val mailTemplate = document.templates["root"]
            ?: throw IllegalStateException("Mail template missing fragment 'root'")
        val fmt = currencyFormat()

        val itemsTable = data.lines.joinToString(separator = "") { line ->
            val nameCell = buildString {
                append(escapeHtml(line.name))
                val variant = line.variantLabel?.takeIf { it.isNotBlank() }
                if (variant != null) {
                    append("<br><span style=\"font-size:12px;color:#8A90A3;\">")
                    append(escapeHtml(variant))
                    append("</span>")
                }
            }
            """
            <tr>
              <td style="padding:12px 0; border-bottom:1px solid #EDEFF5; font-size:14px; color:#0E1432;">$nameCell</td>
              <td style="padding:12px 0; border-bottom:1px solid #EDEFF5; font-size:14px; color:#0E1432; text-align:center;">${line.quantity}</td>
              <td style="padding:12px 0; border-bottom:1px solid #EDEFF5; font-size:14px; color:#0E1432; text-align:right;">${fmt.format(line.unitPrice)}</td>
              <td style="padding:12px 0; border-bottom:1px solid #EDEFF5; font-size:14px; color:#0E1432; text-align:right;">${fmt.format(line.lineTotal)}</td>
            </tr>
            """.trimIndent()
        }

        val hasDiscount = !data.discountCode.isNullOrBlank() && data.discountAmount > 0
        val discountLine = if (hasDiscount) {
            val label = escapeHtml(data.discountCode!!)
            """
            <tr>
              <td colspan="3" style="padding:3px 0; text-align:right; font-size:13px; color:#6A7290;">Giảm giá ($label)</td>
              <td style="padding:3px 0; text-align:right; font-size:13px; color:#6A7290;">-${fmt.format(data.discountAmount)}</td>
            </tr>
            """.trimIndent()
        } else {
            ""
        }

        return mailTemplate
            .replace("{{customer_name}}", escapeHtml(data.customerName))
            .replace("{{customer_email}}", escapeHtml(data.customerEmail))
            .replace("{{customer_phone}}", escapeHtml(data.customerPhone))
            .replace("{{order_code}}", escapeHtml(data.orderCode))
            .replace("{{show_name}}", escapeHtml(data.showName))
            .replace("{{items_table}}", itemsTable)
            .replace("{{subtotal}}", fmt.format(data.subtotal))
            .replace("{{discount_line}}", discountLine)
            .replace("{{total}}", fmt.format(data.total))
            .replace("{{pickup_note}}", escapeHtml(data.pickupNote.orEmpty()))
            .replace("{{hotline}}", escapeHtml(metaData.hotline))
            .replace("{{supporter}}", escapeHtml(metaData.supporter))
            .replace("{{end_point}}", escapeHtml(metaData.endPoint))
    }

    /** Escapes HTML special characters in values interpolated into the template. */
    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
