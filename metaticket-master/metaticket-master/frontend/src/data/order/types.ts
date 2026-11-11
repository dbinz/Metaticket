import { string, z } from 'zod'
import { TicketDocReservationDetails } from '@/data/ticket'

enum OrderStatus {
  Created = 'Created',
  WaitingForPayment = 'WaitingForPayment',
  PaymentProcessing = 'PaymentProcessing',
  PaymentSucceed = 'PaymentSucceed',
  PaymentFailed = 'PaymentFailed',
  PaymentExpired = 'PaymentExpired',
  PaymentPending = 'PaymentPending',
}

// set custom error message
const customErrorMap: z.ZodErrorMap = (issue, ctx) => {
  if (issue.code === z.ZodIssueCode.invalid_type) {
    if (issue.received === 'undefined') {
      return { message: '' }
    }
  }

  if (issue.code === z.ZodIssueCode.too_small) {
    return { message: '' }
  }

  return { message: ctx.defaultError }
}
z.setErrorMap(customErrorMap)

enum OrderCustomFieldType {
  String = 'String',
  Number = 'Number',
  Date = 'Date',
  DataCollector = "DataCollector",
  List = "List",
  Boolean = "Boolean"
}

const OrderCustomFieldConstraints = z.object({
  require: z.boolean().optional().default(false),
})

type OrderCustomFieldNumberConstraints = z.infer<typeof OrderCustomFieldNumberConstraints>
const OrderCustomFieldNumberConstraints = OrderCustomFieldConstraints.extend({
  min: z.number().nullable().optional(),
  max: z.number().nullable().optional(),
  
})

type OrderCustomFieldStringConstraints = z.infer<typeof OrderCustomFieldStringConstraints>
const OrderCustomFieldStringConstraints = OrderCustomFieldConstraints.extend({
  regex: z.any().nullable().optional(),
  minLength: z.number().nullable().optional(),
  maxLength: z.number().nullable().optional(),
  
})

type OrderCustomFieldBooleanConstraints = z.infer<typeof OrderCustomFieldBooleanConstraints>
const OrderCustomFieldBooleanConstraints = OrderCustomFieldConstraints.extend({
  
})

type OrderCustomFieldDataCollectorConstraints = z.infer<typeof OrderCustomFieldDataCollectorConstraints>
const OrderCustomFieldDataCollectorConstraints = OrderCustomFieldConstraints.extend({
  
})

type OrderCustomFieldDateConstraints = z.infer<typeof OrderCustomFieldDateConstraints>
const OrderCustomFieldDateConstraints = OrderCustomFieldConstraints.extend({
  minDate: z.string().nullable().optional(),
  maxDate: z.string().nullable().optional(),
})

type OrderCustomFieldTypeListOptionConstraint = z.infer<typeof OrderCustomFieldTypeListOptionConstraint>
const OrderCustomFieldTypeListOptionConstraint = z.object({
  name: z.string(),
  value: z.string()
})

type OrderCustomFieldTypeListConstraints = z.infer<typeof OrderCustomFieldTypeListConstraints>
const OrderCustomFieldTypeListConstraints = OrderCustomFieldConstraints.extend({
  placeHolder: z.string().nullable().optional(),
  options: z.array(OrderCustomFieldTypeListOptionConstraint)
})


type OrderCustomField = z.infer<typeof OrderCustomField>
const OrderCustomField = z.object({
  id: z.string(),
  type: z.nativeEnum(OrderCustomFieldType).optional().nullable(),
  displayName: z.string().optional().nullable(),
  name: z.string().optional().nullable(),
  constraints: z.string().optional().nullable(),
  value: z.string().default(""),
  note: z.string().optional().nullable(),
  error: z.boolean().default(false).optional(),
  errorMessage: z.string().default("").optional()
})

type OrderCustomFields = z.infer<typeof OrderCustomFields>
const OrderCustomFields = z.array(OrderCustomField)

const phoneEmptyErrorMap: z.ZodErrorMap = (issue, ctx) => {
  if (issue.code === z.ZodIssueCode.invalid_type) {
    return { message: 'Số điện thoại không hợp lệ' }
  }

  if (issue.code === z.ZodIssueCode.too_small) {
    return { message: 'Số điện thoại không hợp lệ' }
  }

  return { message: ctx.defaultError }
}

// only allow Vietnam mobile number
const phoneSchema = z
  .string({errorMap: phoneEmptyErrorMap})
  .regex(
    new RegExp(/^(?:\+84|84|0)(32|33|34|35|36|37|38|39|86|96|97|98|81|82|83|84|85|88|91|94|70|76|77|78|79|89|90|93|52|56|58|92|59|99|87)[0-9]{7}$/),
    'Số điện thoại không hợp lệ'
  )

const DeliveryInfo = z.object({
  deliveryName: z.string().min(1),
  deliveryPhone: phoneSchema,
  provinceName: z.string().min(1),
  districtName: z.string().min(1),
  wardName: z.string().min(1),
  address: z.string().min(1),
})

const nameEmptyErrorMap: z.ZodErrorMap = (issue, ctx) => {
  if (issue.code === z.ZodIssueCode.invalid_type) {
    return { message: 'Bạn chưa nhập tên' }
  }

  if (issue.code === z.ZodIssueCode.too_small) {
    return { message: 'Bạn chưa nhập tên' }
  }

  return { message: ctx.defaultError }
}



const provinceNameEmptyErrorMap: z.ZodErrorMap = (issue, ctx) => {
  if (issue.code === z.ZodIssueCode.invalid_type) {
    return { message: 'Bạn chưa chọn tỉnh' }
  }

  if (issue.code === z.ZodIssueCode.too_small) {
    return { message: 'Bạn chưa chọn tỉnh' }
  }

  return { message: ctx.defaultError }
}

const districtNameEmptyErrorMap: z.ZodErrorMap = (issue, ctx) => {
  if (issue.code === z.ZodIssueCode.invalid_type) {
    return { message: 'Bạn chưa nhập quận / huyện' }
  }

  if (issue.code === z.ZodIssueCode.too_small) {
    return { message: 'Bạn chưa nhập quận / huyện' }
  }

  return { message: ctx.defaultError }
}

const wardNameEmptyErrorMap: z.ZodErrorMap = (issue, ctx) => {
  if (issue.code === z.ZodIssueCode.invalid_type) {
    return { message: 'Bạn chưa nhập phường / xã' }
  }

  if (issue.code === z.ZodIssueCode.too_small) {
    return { message: 'Bạn chưa nhập phường / xã' }
  }

  return { message: ctx.defaultError }
}

const addressEmptyErrorMap: z.ZodErrorMap = (issue, ctx) => {
  console.log(issue)
  if (issue.code === z.ZodIssueCode.invalid_type) {
    return { message: 'Bạn chưa nhập tên đường, số nhà' }
  }

  if (issue.code === z.ZodIssueCode.too_small) {
    return { message: 'Bạn chưa nhập tên đường, số nhà' }
  }

  return { message: ctx.defaultError }
}


type PurchaseInfo = z.infer<typeof PurchaseInfo>
const PurchaseInfo = z.object({
  name: z.string({errorMap: nameEmptyErrorMap}).min(1),
  phone: phoneSchema,
  email: z.string().email({message: "Email không đúng định dạng"}),
  delivery: z.object({
    deliveryName: z.string({errorMap: nameEmptyErrorMap}).min(1, {message: "Bạn chưa nhập tên"}),
    deliveryPhone: phoneSchema,
    provinceName: z.string({errorMap: provinceNameEmptyErrorMap}).min(1, {message: "Bạn chưa chọn tỉnh"}),
    districtName: z.string({errorMap: districtNameEmptyErrorMap}).min(1, {message: "Bạn chưa nhập quận / huyện"}),
    wardName: z.string({errorMap: wardNameEmptyErrorMap}).min(1, {message: "Bạn chưa nhập phường / xã"}),
    address: z.string({errorMap: addressEmptyErrorMap}).min(1, {message: "Bạn chưa nhập tên đường, số nhà"}),
  }).nullable(),
  customFields: z.array(OrderCustomField).optional().nullable(),
  // deliveryName: z.string().min(1),
  // deliveryPhone: phoneSchema,
  // provinceName: z.string().min(1),
  // districtName: z.string().min(1),
  // wardName: z.string().min(1),
  // address: z.string().min(1),

})


// Tentative Price
type TentativePriceRequest = z.infer<typeof TentativePriceRequest>
const TentativePriceRequest = z.object({
  tickets: z.array(TicketDocReservationDetails),
  reservationId: z.string().optional().nullable(),
  showId: z.string(),
  sessionId: z.string(),
  promotionCode: z.string()
})

enum PromotionType {
  None = 'NONE',
  Combo = 'COMBO',
  Coupon = 'COUPON',
}


type PromotionDetailResponse = z.infer<typeof PromotionDetailResponse>
const PromotionDetailResponse = z.object({
  priceDiscount: z.number(),
  name: z.string(),
  type: z.nativeEnum(PromotionType),
  error: z.boolean(),
  errorMessage: z.string().nullable()
})

type TentativePriceResponse = z.infer<typeof TentativePriceResponse>
const TentativePriceResponse = z.object({
  priceDiscount: z.number(),
  priceTotal: z.number(),
  priceOriginal: z.number(),
  shipPrice: z.number().optional().nullable(),
  version: z.number().optional().nullable(),
  promotionDetails: z.array(PromotionDetailResponse)
})



// CreateOrderRequest
type CreateOrderRequest = z.infer<typeof CreateOrderRequest>
const CreateOrderRequest = PurchaseInfo.extend({
  reservationId: z.string(),
  promotionCode: z.string().optional().nullable(),
  customFields: z.array(OrderCustomField).optional().nullable(),
})

type UpdateOrderRequest = z.infer<typeof UpdateOrderRequest>
const UpdateOrderRequest = PurchaseInfo.extend({
  orderId: z.string(),
})

type CancelOrderRequest = z.infer<typeof CancelOrderRequest>
const CancelOrderRequest = z.object({
  orderId: z.string(),
})

// OrderResponse
type OrderResponse = z.infer<typeof OrderResponse>
const OrderResponse = z.object({
  id: z.string(),
  reservationId: z.string(),
  customerName: z.string(),
  customerPhone: z.string(),
  customerEmail: z.string().email(),
  status: z.nativeEnum(OrderStatus),
  delivery: z.object({
    deliveryName: z.string().min(1),
    deliveryPhone: phoneSchema,
    provinceName: z.string().min(1),
    districtName: z.string().min(1),
    wardName: z.string().min(1),
    address: z.string().min(1),
  }).nullable()
  // tickets: z.array(z.string()).optional(),
})

// Order Summary Response

// type OrderSummaryPaymentInfo = z.infer<typeof OrderSummaryPaymentInfo>
// const OrderSummaryPaymentInfo = DeliveryInfo.extend({
//   payTime: z.string(),
//   orderId: z.string(),
//   payMethod: z.string(),
//   payStatus: z.string(),
// })

// type OrderSummaryOrderDetail = z.infer<typeof OrderSummaryOrderDetail>
// const OrderSummaryOrderDetail = DeliveryInfo.extend({
//   eventName: z.string(),
//   time: z.string(),
//   venue: z.string(),
//   total: z.string(),
// })

type OrderSummaryBuyerInfo = z.infer<typeof OrderSummaryBuyerInfo>
const OrderSummaryBuyerInfo = z.object({
  name: z.string(),
  phone: z.string(),
  email: z.string(),
})

type OrderSummaryRecipientInfo = z.infer<typeof OrderSummaryRecipientInfo>
const OrderSummaryRecipientInfo = z.object({
  name: z.string(),
  phone: z.string(),
  address: z.string(),
})

type OrderSummaryCustomFields = z.infer<typeof OrderSummaryCustomFields>
const OrderSummaryCustomFields = z.array(z.object({
  name: z.string(),
  value: z.string()
}))

type OrderPrice = z.infer<typeof OrderPrice>
const OrderPrice = z.object({
  name: z.string(),
  price: z.number(),
  quantity: z.number(),
})


type OrderPromotion = z.infer<typeof OrderPrice>
const OrderPromotion = z.object({
  name: z.string(),
  price: z.number(),
})

type OrderSummaryResponse = z.infer<typeof OrderSummaryResponse>
const OrderSummaryResponse = z.object({
  orderId: z.string(),
  orderCode: z.string(),
  email: z.string(),
  paymentOffline: z.boolean().nullable(),
  paymentNote: z.string().nullable(),
  paymentInfo: z.object({
    paymentId: z.string().nullable(),
    payTime: z.string().nullable(),
    payMethod: z.string().nullable(),
    payStatus: z.string().nullable(),
  }).nullable(),
  orderDetail: z.object({
    eventName: z.string(),
    eventTime: z.string(),
    venue: z.string(),
    total: z.number(),
    prices: z.array(OrderPrice),
    promotions: z.array(OrderPromotion),
    orderNote: z.string().nullable(),
  }),
  buyerInfo: OrderSummaryBuyerInfo,
  recipientInfo: OrderSummaryRecipientInfo.nullable(),
  customFields: OrderSummaryCustomFields.nullable()
})

type OrderCustomFieldResponse = z.infer<typeof OrderCustomFieldResponse>
const OrderCustomFieldResponse = z.array(z.object({
  id: z.string(),
  type: z.nativeEnum(OrderCustomFieldType),
  displayName: z.string().nullable().optional(),
  note: z.string().nullable().optional(),
  constraints: z.string().nullable().optional(),
}))

type OrderCustomFieldRequest = z.infer<typeof OrderCustomFieldRequest>
const OrderCustomFieldRequest = z.object({
  showId: z.string(),
  sessionId: z.string(),
})

type OrderDataCollectorGenerateLinkResponse = z.infer<typeof OrderDataCollectorGenerateLinkResponse>
const OrderDataCollectorGenerateLinkResponse = z.string()

type OrderDataCollectorGenerateLinkRequest = z.infer<typeof OrderDataCollectorGenerateLinkRequest>
const OrderDataCollectorGenerateLinkRequest = z.object({
  resId: z.string(),
})

enum OrderDataCollectorStatus {
  empty = 'EMPTY',
  invalid = 'INVALID',
  valid = 'VALID',
}

type OrderDataCollectorIsValidLinkResponse = z.infer<typeof OrderDataCollectorIsValidLinkResponse>
const OrderDataCollectorIsValidLinkResponse = z.nativeEnum(OrderDataCollectorStatus)

type OrderDataCollectorIsValidLinkRequest = z.infer<typeof OrderDataCollectorIsValidLinkRequest>
const OrderDataCollectorIsValidLinkRequest = z.object({
  link: z.string(),
})

export {
  CreateOrderRequest,
  CancelOrderRequest,
  OrderResponse,
  PurchaseInfo,
  UpdateOrderRequest,
  TentativePriceRequest,
  TentativePriceResponse,
  PromotionDetailResponse,
  PromotionType,
  OrderSummaryResponse,
  OrderSummaryBuyerInfo,
  OrderSummaryRecipientInfo,
  OrderSummaryCustomFields,
  OrderCustomFieldRequest,
  OrderCustomFieldResponse,
  OrderCustomField,
  OrderCustomFields,
  OrderCustomFieldType,
  OrderDataCollectorStatus,
  OrderDataCollectorGenerateLinkResponse,
  OrderDataCollectorGenerateLinkRequest,
  OrderDataCollectorIsValidLinkResponse,
  OrderDataCollectorIsValidLinkRequest,
  OrderCustomFieldTypeListConstraints,
  OrderCustomFieldTypeListOptionConstraint,
  OrderCustomFieldStringConstraints,
  OrderCustomFieldDataCollectorConstraints,
  OrderCustomFieldNumberConstraints,
  OrderCustomFieldDateConstraints,
  OrderCustomFieldBooleanConstraints,
  OrderStatus
}
