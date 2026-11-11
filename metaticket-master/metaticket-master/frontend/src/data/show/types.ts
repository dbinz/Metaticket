import { z } from 'zod'
import {PaymentType} from "@/data/payment";

/**
 * referring to ShowDocument.kt in show svc
 */

enum ShowSessionStatus {
  Created = 'Created',
  ApprovalWait = 'ApprovalWait',
  Rejected = 'Rejected',
  OpeningWait = 'OpeningWait',
  Open = 'Open',
  Paused = 'Paused',
  Stopped = 'Stopped',
  Terminated = 'Terminated',
}

type ShowSessionDocument = z.infer<typeof ShowSessionDocument>
const ShowSessionDocument = z.object({
  id: z.string(),
  seatPlanId: z.string(),
  edType: z.string(),
  orderNote: z.string().nullable().optional(),
  venue: z.string().optional().nullable(),
  ledgendImageUrl: z.string().optional().nullable(),
  requireDelivery: z.boolean(),
  paymentOffline: z.boolean().optional().nullable(),
  paymentNote: z.string().nullable(),
  status: z.nativeEnum(ShowSessionStatus),
  paymentMethods: z.array(z.nativeEnum(PaymentType)),
})

type ShowDocument = z.infer<typeof ShowDocument>
const ShowDocument = z.object({
  id: z.string(),
  sessions: z.array(ShowSessionDocument).nonempty(),
  // TODO: remove optional
  showName: z.string().optional().nullable(),
  // TODO: remove optional
  website: z.string().optional().nullable(),
  bannerImageUrl: z.string().optional().nullable(),
  logoHostUrl: z.string().optional().nullable(),
  logoShowUrl: z.string().optional().nullable(),
  shipPrice: z.number()
})

export { ShowDocument, ShowSessionDocument, ShowSessionStatus }
