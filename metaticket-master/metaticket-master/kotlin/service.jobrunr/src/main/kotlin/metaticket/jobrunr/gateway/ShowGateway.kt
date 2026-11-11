package metaticket.jobrunr.gateway

import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt


/**
 *
 * @author dongcatchay
 */
class ShowGateway(stub: JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub) : JobExecutorGateway(stub)