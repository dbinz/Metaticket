package metaticket.jobrunr.gateway

import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt

class PaymentGateway(stub: JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub) : JobExecutorGateway(stub)