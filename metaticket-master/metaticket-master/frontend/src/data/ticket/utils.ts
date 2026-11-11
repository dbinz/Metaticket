import {
  TicketDocument,
  TicketDocReservationStatus,
  TicketDocReservation,
} from './types'

const isTicketDisabled = (ticket: TicketDocument) => {
  return ticket.remCap <= 0
}

const isReservationSuccess = (reservation: TicketDocReservation) => {
  return reservation.status === TicketDocReservationStatus.Reserved
}

const isReservationPending = (reservation: TicketDocReservation) => {
  return [TicketDocReservationStatus.Created, TicketDocReservationStatus.Reserving,].includes(reservation.status)
}

const isReservationExpired = (reservation: TicketDocReservation) => {
  return [TicketDocReservationStatus.OrderFailed, TicketDocReservationStatus.OrderCancelled, TicketDocReservationStatus.Removed, TicketDocReservationStatus.Expired].includes(reservation.status)
}

export {
  isTicketDisabled,
  isReservationPending,
  isReservationSuccess,
  isReservationExpired,
}
