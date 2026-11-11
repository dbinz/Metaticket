package metaticket.notification.mail.client

import jakarta.mail.util.ByteArrayDataSource
import kotlinx.coroutines.future.await
import metaticket.common.logger.Logging
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.email.EmailBuilder
import java.util.concurrent.CompletableFuture

/**
 *
 * @author dongcatchay
 */
class MailClient(
    private val mailer: Mailer
): Logging {

    suspend fun sendMail(
        fromAddress: String,
        toAddress: String,
        subject: String,
        fromName: String? = null,
        toName: String? = null,
        bodyHtml: String? = null,
        bodyString: String? = null,
        attachment: Map<String, Pair<String, ByteArray>>? = null
    ): Unit {
        // Setting context class loader can be crucial for some libraries,
        // ensure this is the correct place and necessity for it.
        Thread.currentThread().setContextClassLoader(javaClass.getClassLoader())

        val emailBuilder = EmailBuilder.startingBlank()
            .from(fromName, fromAddress)
            .to(toName, toAddress)
            .withSubject(subject)

        bodyString?.let { emailBuilder.withPlainText(it) }
        bodyHtml?.let { emailBuilder.withHTMLText(it) }
        attachment?.forEach { (name, data) ->
            val (type, source) = data
            emailBuilder.withAttachment(name, ByteArrayDataSource(source, type))
        }

        val email = emailBuilder.buildEmail()

        val future: CompletableFuture<Void>? = mailer.sendMail(email, true)

        if (future == null) {
            // This is unexpected for async=true. Log and throw to indicate a serious issue.
            val errorMessage = "mailer.sendMail(email, true) returned null for recipient $toAddress. This indicates a problem with the Mailer configuration or the simple-java-mail library."
            logger.error(errorMessage)
            throw IllegalStateException(errorMessage)
        }

        future.await() // await() on CompletableFuture<Void> returns Unit
    }
}