package metaticket.jobrunr.gateway

import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt


/**
 *
 * @author dongcatchay
 */
class TicketGateway(stub: JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub) : JobExecutorGateway(stub)