import { z } from 'zod'

enum PaymentType {
  QrCode = 'QrCode',
  InternationalCard = 'InternationalCard',
  DomesticCard = 'DomesticCard',
  VietQr = 'VietQr',
}

enum PaymentGateway {
  NinePay = 'NinePay',
  VNPay = 'VNPay',
  OnePay = 'OnePay',
}

enum PaymentStatus {
  Created = 'Created',
  Processing = 'Processing',
  Succeed = 'Succeed',
  Failed = 'Failed',
  Cancelled = 'Cancelled',
}

// create payment
type CreatePaymentRequest = z.infer<typeof CreatePaymentRequest>
const CreatePaymentRequest = z.object({
  orderId: z.string(),
})
type CreatePaymentResponse = z.infer<typeof CreatePaymentResponse>
const CreatePaymentResponse = z.object({
  paymentId: z.string(),
})

// start payment
type StartPaymentRequest = z.infer<typeof StartPaymentRequest>
const StartPaymentRequest = z.object({
  paymentId: z.string(),
  paymentType: z.nativeEnum(PaymentType),
})

type StartPaymentResponse = z.infer<typeof StartPaymentResponse>
const StartPaymentResponse = z.object({
  redirectUrl: z.string(),
})

// update pay payment status from result
type UpdatePaymentFromResultRequest = z.infer<
  typeof UpdatePaymentFromResultRequest
>
const UpdatePaymentFromResultRequest = z.object({
  url: z.string(),
})

type UpdatePaymentFromResultResponse = z.infer<typeof UpdatePaymentFromResultResponse>
const UpdatePaymentFromResultResponse = z.object({
  message: z.string(),
  code: z.number(), // 0 - success, 1 - failed, 3 - unknown
})

const LoadPaymentStatusResponse = z.object({
  status: z.nativeEnum(PaymentStatus),
})

export {
  CreatePaymentRequest,
  CreatePaymentResponse,
  PaymentType,
  PaymentGateway,
  PaymentStatus,
  StartPaymentRequest,
  StartPaymentResponse,
  LoadPaymentStatusResponse,
  UpdatePaymentFromResultRequest,
  UpdatePaymentFromResultResponse
}
