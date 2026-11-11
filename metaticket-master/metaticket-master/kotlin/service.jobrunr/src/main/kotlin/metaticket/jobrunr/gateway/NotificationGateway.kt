package metaticket.jobrunr.gateway

import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt


/**
 *
 * @author dongcatchay
 */
class NotificationGateway(stub: JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub) : JobExecutorGateway(stub)