import { TicketDocument } from './types'
import { StoreManager } from '../storeManager'

const store = StoreManager.getInstance()
// consolidate all ticket storage actions on client side using store manager
// TODO: clean up store when necessary
const TicketStore = {
  getUpdatedTime: () =>
    parseInt(store.getItem('lastUpdated') || '') || Date.now(),

  getTickets: (): TicketDocument[] => {
    return JSON.parse(store.getItem('tickets') || '[]')
  },

  setTickets: (tickets: TicketDocument[], requestTime: number) => {
    store.setItem('lastUpdated', requestTime.toString())
    store.setItem('tickets', JSON.stringify(tickets))
  },

  updateTickets: (updatedTickets: TicketDocument[], requestTime: number) => {
    const existingTickets = TicketStore.getTickets()
    const updatedTicketIds = new Set(updatedTickets.map((ticket) => ticket.id))
    const nonUpdatedTickets = existingTickets.filter(
      (ticket) => !updatedTicketIds.has(ticket.id)
    )

    TicketStore.setTickets(
      nonUpdatedTickets.concat(updatedTickets),
      requestTime
    )
  },
}

export { TicketStore }
