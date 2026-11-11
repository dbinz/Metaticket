import { z } from 'zod'

/**
 * Referring to
 *  - ticket.document.kt in order svc
 */

enum SeatType {
  SeatZone = 'SeatZone',
  Seat = 'Seat',
}

enum TicketStatus {
  Created = 'Created',
  Opened = 'Opened',
  Blocked = 'Blocked',
  Closed = 'Closed',
}

enum TicketIssuedStatus {
  Paid = 'Paid',
  CheckedIn = 'CheckedIn',
  Refunded = 'Refunded',
  Expired = 'Expired',
}

type TicketDocument = z.infer<typeof TicketDocument>
const TicketDocument = z.object({
  id: z.string(),
  sid: z.string(),
  dname: z.string().nullable().optional(),
  seatType: z.nativeEnum(SeatType),
  status: z.nativeEnum(TicketStatus),
  resCap: z.number().int().default(0),
  totCap: z.number().int().default(1),
  remCap: z.number().int().default(1),
  priceCat: z.string(),
  basePrice: z.number(),
})

type TicketDocuments = z.infer<typeof TicketDocuments>
const TicketDocuments = z.array(TicketDocument)

/**
 * Referring to
 *  - ticket.doc.reservation.kt in ticket svc
 */

enum TicketDocReservationStatus {
  Open = 'Open',
  Created = 'Created',
  Reserving = "Reserving",
  Reserved = 'Reserved',
  Expired = 'Expired',
  Removing = 'Removing',
  Removed = 'Removed',
  OrderPaying = 'OrderPaying',
  OrderSuccessful = 'OrderSuccessful',
  OrderFailed = 'OrderFailed',
  OrderCancelled = 'OrderCancelled',
}

type TicketDocReservationDetails = z.infer<typeof TicketDocReservationDetails>
const TicketDocReservationDetails = z.object({
  ticketId: z.string(),
  quantity: z.number().int(),
  priceCat: z.string(),
})

type TicketDocReservation = z.infer<typeof TicketDocReservation>
const TicketDocReservation = z.object({
  id: z.string(),
  details: z.array(TicketDocReservationDetails),
  status: z.nativeEnum(TicketDocReservationStatus),
  expireInS: z.number()
})

export {
  TicketStatus,
  TicketDocument,
  TicketDocuments,
  TicketDocReservation,
  TicketDocReservationStatus,
  TicketDocReservationDetails,
}
