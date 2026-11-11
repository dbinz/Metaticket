// when DOMAIN = '' then the url is the relative path
export const API_DOMAIN = process.env.NEXT_PUBLIC_DOMAIN || ''

console.log('DOMAIN--', API_DOMAIN)
console.log('NODE_ENV--', process.env.NODE_ENV)

export const BASE_API_PATH = 'api/'

export const DEFAULT_STALE_TIME = 10 * 60 * 1000 // 10 minute (in ms)
export const TICKET_REFETCH_INTERVAL = 5 * 1000 // 5 seconds (in ms)
export const PAYMENT_REFETCH_INTERVAL = 1000 // 1 seconds (in ms)
export const RESERVATION_REFETCH_INTERVAL = 1000 // 1 seconds (in ms)
export const RESERVATION_TIMEOUT_COUNT = 60 // 60 times (60 seconds)
