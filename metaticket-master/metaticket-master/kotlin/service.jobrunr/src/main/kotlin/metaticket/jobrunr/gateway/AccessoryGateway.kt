package metaticket.jobrunr.gateway

import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt

class AccessoryGateway(stub: JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub) : JobExecutorGateway(stub)
