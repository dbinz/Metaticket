package meta.metasearch.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service


@Service
class ApiKeyAuthenticationService(
        @Value("\${api.key}") private val apiKey: String) {

    fun isValidApiKey(providedApiKey: String): Boolean {
        return apiKey == providedApiKey
    }

}