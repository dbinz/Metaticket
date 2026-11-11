package metaticket.admin.peer

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.* // For ResponseException, NoTransformationFoundException
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.admin.configuration.ServiceExplorerConfig
import metaticket.admin.exception.PeerServiceException
import metaticket.admin.utils.buildRequest
import metaticket.common.logger.Logging
import metaticket.order.datastructure.response.InvoiceStatisticsByShow
import metaticket.order.datastructure.response.OrderResponse
import metaticket.order.pojo.db.CustomFieldTmpl
import metaticket.order.request.*
import java.io.IOException // For generic IO errors

class OrderClient(
    seConf: ServiceExplorerConfig,
    private val client: HttpClient,
) : Logging {
    private val peerConfig = seConf.orderS

    // Define constants for URL paths
    private companion object {
        const val PATH_LOAD_STATISTICS = "/internal/order/statistic/load"
        const val PATH_LOAD_REPORT = "/internal/order/report/load"
    }

    /**
     * Loads invoice statistics.
     * @throws IOException for other network/IO related issues.
     */
    suspend fun loadInvoiceStatistics(req: LoadInvoiceStatisticsRequest): List<InvoiceStatisticsByShow> {
        return executePostRequestAndParseBody(
            path = PATH_LOAD_STATISTICS,
            body = req,
            operationName = "loadInvoiceStatistics"
        )
    }

    /**
     * Loads an order report.
     * @throws IOException for other network/IO related issues.
     */
    suspend fun loadReport(req: LoadReportRequest): List<OrderResponse> {
        return executePostRequestAndParseBody(
            path = PATH_LOAD_REPORT,
            body = req,
            operationName = "loadReport"
        )
    }

    // --- Helper Functions ---

    /**
     * Executes a POST request, checks for success status, and parses the body.
     * @param T The expected type of the response body.
     * @param path The URL path for the request.
     * @param body The request body object.
     * @param operationName A descriptive name of the operation for logging/error messages.
     * @return The parsed response body of type T.
     */
    private suspend inline fun <reified T, reified B : Any> executePostRequestAndParseBody(
        path: String,
        body: B,
        operationName: String
    ): T {
        val requestBuilder= peerConfig.buildRequest {
            url.path(path)
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return try {
            logger.debug("Executing POST request for '$operationName' to path '$path'")
            val response = withContext(Dispatchers.IO) {
                client.request(requestBuilder) // Apply base config and specific builder
            }
            if (!response.status.isSuccess()) {
                val errorBody = runCatching { response.bodyAsText() }.getOrNull() ?: "[No Body]"
                logger.warn("Non-success status for '$operationName': ${response.status}. Body: $errorBody")
                throw PeerServiceException("Operation '$operationName' failed: ${response.status} response: $errorBody")
            }
            logger.debug("Request '{}' successful with status {}. Parsing body.", operationName, response.status)
            response.body<T>() // Let Ktor handle deserialization errors here
        } catch (e: ResponseException) {
            // Catch Ktor's specific exception for HTTP errors
            logger.error("HTTP error during '$operationName': ${e.response.status}", e)
            throw PeerServiceException("Order service request '$operationName' failed: ${e.response.status}", e)
        } catch (e: NoTransformationFoundException) {
            // Catch errors during body parsing specifically
            logger.error("Failed to parse response body during '$operationName': ${e.message}", e)
            throw PeerServiceException("Failed to parse response for '$operationName'", e)
        } catch (e: Exception) {
            // Catch other potential exceptions (network issues, serialization, etc.)
            logger.error("Error during '$operationName': ${e.message}", e)
            // Avoid wrapping OrderServiceException again if it was already thrown
            if (e is PeerServiceException) throw e
            throw PeerServiceException("Order service request '$operationName' failed: ${e.message}", e)
        }
    }

    /**
     * Executes a POST request and checks for a success status code. Does not parse the body.
     * @param path The URL path for the request.
     * @param body The request body object.
     * @param operationName A descriptive name of the operation for logging/error messages.
     */
    private suspend inline fun <reified B : Any> executePostRequestAndCheckStatus(
        path: String,
        body: B,
        operationName: String
    ) {
        val requestBuilder= peerConfig.buildRequest {
            url.path(path)
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        try {
            logger.debug("Executing POST request for '$operationName' to path '$path' (status check only)")
            val response = withContext(Dispatchers.IO) {
                client.request(requestBuilder) // Apply base config and specific builder
            }
            if (!response.status.isSuccess()) {
                val errorBody = runCatching { response.bodyAsText() }.getOrNull() ?: "[No Body]"
                logger.warn("Non-success status for '$operationName': ${response.status}. Body: $errorBody")
                throw ResponseException(response, "Operation '$operationName' failed: ${response.status} response: $errorBody")
            }
            // No explicit discard needed here in Ktor 2.x.
            logger.debug("Request '{}' successful with status {}.", operationName, response.status)
        } catch (e: ResponseException) {
            logger.error("HTTP error during '$operationName': ${e.response.status}", e)
            throw PeerServiceException("Order service request '$operationName' failed: ${e.response.status}", e)
        } catch (e: Exception) {
            logger.error("Error during '$operationName': ${e.message}", e)
            if (e is PeerServiceException) throw e
            throw PeerServiceException("Order service request '$operationName' failed: ${e.message}", e)
        }
    }
}