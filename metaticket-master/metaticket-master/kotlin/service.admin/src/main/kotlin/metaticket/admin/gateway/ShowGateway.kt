package metaticket.admin.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.show.grpc.ShowMessage
import metaticket.show.grpc.ShowServiceGrpcKt

class ShowGateway constructor(
    private val stub: ShowServiceGrpcKt.ShowServiceCoroutineStub
): Logging {
    suspend fun getCity(cityId: String): ShowMessage.CityProto? {
        val requestBuilder = ShowMessage.GetCityRequest.newBuilder()
            .setCityId(cityId)
            .build()

        return try {
            withContext(Dispatchers.IO) {
                stub.getCity(requestBuilder)
            }
        } catch (t: Throwable) {
            logger.error("cannot get city {} ...", cityId, t)
            null
        }
    }

    suspend fun createSession(request: ShowMessage.CreateSessionRequest) : String? {
        return try {
            withContext(Dispatchers.IO) { stub.createSession(request) }.value
        } catch (t: Throwable) {
            logger.error("cannot create show {} ...", request, t)
            null
        }
    }

    /** Best-effort cleanup of superseded draft temp uploads on the show host. */
    suspend fun deleteTempImages(filePaths: Collection<String>): Int {
        val paths = filePaths.filter { it.isNotBlank() }.distinct()
        if (paths.isEmpty()) return 0
        return try {
            withContext(Dispatchers.IO) {
                stub.deleteTempImages(
                    ShowMessage.DeleteTempImagesRequest.newBuilder()
                        .addAllFilePaths(paths)
                        .build(),
                ).deletedCount
            }
        } catch (t: Throwable) {
            logger.error("cannot delete temp images {} ...", paths, t)
            0
        }
    }
}