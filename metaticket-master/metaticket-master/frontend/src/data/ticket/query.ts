import { useMutation, useQuery } from '@tanstack/react-query'
import { api, getStatusCode } from '../api'
import z from 'zod'
import { TicketDocReservation, TicketDocuments } from './types'
import {
  DEFAULT_STALE_TIME,
  RESERVATION_REFETCH_INTERVAL,
  RESERVATION_TIMEOUT_COUNT,
  TICKET_REFETCH_INTERVAL,
} from '../constants'
import { useState } from 'react'
import { TicketStore } from './store'
import { TicketDocReservationDetails } from './types'
import { AxiosError } from 'axios'

type TicketsQueryParams = z.infer<typeof TicketsQueryParams>
const TicketsQueryParams = z.object({
  showId: z.string(),
  sessionId: z.string(),
})

type TicketsIncrementalQueryParams = z.infer<typeof TicketsIncrementalQueryParams>
const TicketsIncrementalQueryParams = z.object({
  showId: z.string(),
  sessionId: z.string(),
  startTime: z.number()
})

const useDefaultTicketMutation = () => {
  return useMutation<TicketDocuments, Error, TicketsQueryParams>({
    mutationFn: (data) =>
      api
        .post('/ticket/doc/fetch-all', TicketsQueryParams.parse(data))
        .then((response) => response.data)
        .then((data) => {
          TicketStore.setTickets(data, Date.now())
          return TicketDocuments.parse(data)
        }),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useDefaultTicketsQuery = ({ showId, sessionId }: TicketsQueryParams) => {
  const requestTime = Date.now()
  return useQuery({
    queryKey: ['ticket/doc/fetch-all', showId, sessionId],
    queryFn: () =>
      api
        .post('/ticket/doc/fetch-all', { showId, sessionId })
        .then((response) => response.data)
        .then(TicketDocuments.parse),
    staleTime: DEFAULT_STALE_TIME,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
    refetchOnReconnect: false,
    retry: false,
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
    onSuccess: (data) => {
      
      TicketStore.setTickets(data, requestTime)
    },
  })
}

const useIncrementalTicketsMutation = () => {
  return useMutation<TicketDocuments, Error, TicketsIncrementalQueryParams>({
    mutationFn: (data) =>
      api
        .post('/ticket/doc/fetch-time', TicketsIncrementalQueryParams.parse(data))
        .then((response) => response.data)
        .then((data) => {
          // TicketStore.setTickets(data, Date.now())
          TicketStore.updateTickets(data, Date.now())
          return TicketDocuments.parse(data)
        }),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useIncrementalTicketsQuery = ({
  showId,
  sessionId,
}: TicketsQueryParams) => {
  // need this to update each refetch call
  const lastUpdatedTime = TicketStore.getUpdatedTime()
  
  const [requestTime, setRequestTime] = useState<number>(0)
  return useQuery({
    // eslint-disable-next-line @tanstack/query/exhaustive-deps
    queryKey: ['ticket/doc/fetch-time', showId, sessionId],
    queryFn: () => {
      setRequestTime(Date.now())
      return api
        .post('/ticket/doc/fetch-time', {
          showId,
          sessionId,
          startTime: lastUpdatedTime,
        })
        .then((response) => response.data)
        .then(TicketDocuments.parse)
    },
    enabled: lastUpdatedTime > 0,
    refetchInterval: TICKET_REFETCH_INTERVAL,
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
    onSuccess: (data) => {
      TicketStore.updateTickets(data, requestTime)
    },
  })
}

type ReserveTicketsData = z.infer<typeof ReserveTicketsData>
const ReserveTicketsData = z.object({
  showId: z.string(),
  sessionId: z.string(),
  details: z.array(TicketDocReservationDetails),
})
type ReserveTicketsResponse = z.infer<typeof ReserveTicketsResponse>
const ReserveTicketsResponse = z.object({ reservationId: z.string() })

const useCreateReservationMutation = () => {
  return useMutation<ReserveTicketsResponse, Error, ReserveTicketsData>({
    mutationFn: (data) =>
      api
        .post('/ticket/reservation/create', data)
        .then((response) => response.data)
        .then(ReserveTicketsResponse.parse),
    onError: (error: any) => {
      console.error(error)
    },
  })
}

const useStartReservationMutation = () => {
  return useMutation<ReserveTicketsResponse, Error, string>({
    mutationFn: (id) =>
      api
        .get('/ticket/reservation/reserve', { params: { id } })
        .then((response) => response.data)
        .then(ReserveTicketsResponse.parse),
    onError: (error) => {
      // TODO: add error handling
      return 
      console.error(error)
    },
  })
}

const useReleaseReservationMutation = () => {
  return useMutation<null, Error, string>({
    mutationFn: (id) =>
      api.get('/ticket/reservation/release', { params: { id } }),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const getReservationQueryCall = (id?: string) => () =>
  api
    .get('/ticket/reservation/fetch', { params: { id } })
    .then((response) => response.data)
    .then(TicketDocReservation.parse)

const useReservationQueryOnce = (id?: string) => {
  return useQuery({
    queryKey: ['ticket/reservation/fetch', id],
    queryFn: getReservationQueryCall(id),
    enabled: id !== undefined,
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useReservationQueryPolling = (id?: string) => {
  return useQuery({
    queryKey: ['ticket/reservation/fetch', id],
    queryFn: getReservationQueryCall(id),
    enabled: id !== undefined,
    retry: (_failureCount, error) => {
      if (error instanceof AxiosError) {
        return getStatusCode(error) !== 404
      }
      return true
    },
    refetchInterval: (_data, query) =>
      query.state.dataUpdateCount < RESERVATION_TIMEOUT_COUNT
        ? RESERVATION_REFETCH_INTERVAL
        : false,
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

export {
  useDefaultTicketMutation,
  useDefaultTicketsQuery,
  useIncrementalTicketsMutation,
  useIncrementalTicketsQuery,
  useCreateReservationMutation,
  useStartReservationMutation,
  useReleaseReservationMutation,
  useReservationQueryOnce,
  useReservationQueryPolling,
}
