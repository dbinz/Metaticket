import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../api'
import {
  CreatePaymentRequest,
  CreatePaymentResponse,
  StartPaymentRequest,
  StartPaymentResponse,
  LoadPaymentStatusResponse,
  UpdatePaymentFromResultRequest,
  UpdatePaymentFromResultResponse
} from './types'
import { PAYMENT_REFETCH_INTERVAL } from '../constants'

const useCreatePaymentMutation = () => {
  return useMutation<CreatePaymentResponse, Error, CreatePaymentRequest>({
    mutationFn: (data) =>
      api
        .post('/order/payment/create', CreatePaymentRequest.parse(data))
        .then((response) => response.data)
        .then(CreatePaymentResponse.parse),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useStartPaymentMutation = () => {
  return useMutation<StartPaymentResponse, Error, StartPaymentRequest>({
    mutationFn: (data) =>
      api
        .post('/payment/start', StartPaymentRequest.parse(data))
        .then((response) => response.data)
        .then(StartPaymentResponse.parse),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const usePaymentStatusQuery = (
  paymentId: string | undefined,
  enabled: boolean
) => {
  return useQuery({
    queryKey: ['payment/status', paymentId],
    queryFn: () =>
      api
        .get('/payment/fetch-status', { params: { id: paymentId } })
        .then((response) => response.data)
        .then(LoadPaymentStatusResponse.parse),
    enabled: paymentId !== undefined && enabled,
    refetchInterval: PAYMENT_REFETCH_INTERVAL,
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useUpdateNinePayPaymentResultMutation = () => {
  return useMutation<UpdatePaymentFromResultResponse, Error, String>({
    mutationFn: (data) =>
      api
        .get('/payment/return/result/ninepay/' + data)
        .then((response) => response.data),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useUpdateVnPayPaymentResultMutation = () => {
  return useMutation<UpdatePaymentFromResultResponse, Error, String>({
    mutationFn: (data) =>
      api
        .get('/payment/return/result/vnpay/' + data)
        .then((response) => response.data),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useUpdateOnePayPaymentResultMutation = () => {
  return useMutation<UpdatePaymentFromResultResponse, Error, String>({
    mutationFn: (data) =>
        api
            .get('/payment/return/result/onepay/' + data)
            .then((response) => response.data),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

export {
  useCreatePaymentMutation,
  useStartPaymentMutation,
  usePaymentStatusQuery,
  useUpdateNinePayPaymentResultMutation,
  useUpdateVnPayPaymentResultMutation,
  useUpdateOnePayPaymentResultMutation,
}
