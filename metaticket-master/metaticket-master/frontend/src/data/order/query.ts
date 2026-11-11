import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../api'
import {
  CreateOrderRequest,
  OrderResponse,
  UpdateOrderRequest,
  TentativePriceRequest,
  TentativePriceResponse,
  OrderSummaryResponse,
  OrderCustomFieldRequest,
  OrderCustomFieldResponse,
  CancelOrderRequest,
  OrderDataCollectorGenerateLinkResponse,
  OrderDataCollectorGenerateLinkRequest,
  OrderDataCollectorIsValidLinkResponse,
  OrderDataCollectorIsValidLinkRequest
} from './types'
import axios from 'axios'

const useTentativePriceMutation = (version: number) => {
  return useMutation<TentativePriceResponse, Error, TentativePriceRequest>({
    mutationFn: (data) =>
      api
        .post('/order/price/tentative', TentativePriceRequest.parse(data))
        .then((response) => response.data)
        .then((data) => {
          var dataParsed = TentativePriceResponse.parse(data)
          dataParsed.version = version
          return dataParsed
        }),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useCreateOrderMutation = () => {
  return useMutation<OrderResponse, Error, CreateOrderRequest>({
    mutationFn: (data) =>
      api
        .post('/order/create', CreateOrderRequest.parse(data))
        .then((response) => response.data)
        .then((data) => {
          return OrderResponse.parse(data)
        }),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useUpdateOrderMutation = () => {
  return useMutation<OrderResponse, Error, UpdateOrderRequest>({
    mutationFn: (data) =>
      api
        .post('/order/update-info', UpdateOrderRequest.parse(data))
        .then((response) => response.data)
        .then((data) => {
          return OrderResponse.parse(data)
        }),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useCancelOrderMutation = () => {
  return useMutation<{}, Error, CancelOrderRequest>({
    mutationFn: (data) =>
      api
        .post('/order/cancel', CancelOrderRequest.parse(data))
        .then(() => {return {}}),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useOrderSummaryMutation = () => {
  return useMutation<OrderSummaryResponse, Error, String>({
    mutationFn: (data) =>
      api
        .get('/order/fetch/summary', { params: { id: data } })
        .then((response) => response.data)
        .then(OrderSummaryResponse.parse),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useOrderCustomFieldMutation = () => {
  return useMutation<OrderCustomFieldResponse, Error, OrderCustomFieldRequest>({
    mutationFn: (data) =>
      api
        .post('/order/custom-field/fetch', OrderCustomFieldRequest.parse(data))
        .then((response) => response.data)
        .then(OrderCustomFieldResponse.parse),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useOrderDataCollectorGenerateLinkMutation = () => {
  return useMutation<OrderDataCollectorGenerateLinkResponse, Error, OrderDataCollectorGenerateLinkRequest>({
    mutationFn: (data) =>
      api
        .post('/odc/link/generate', OrderDataCollectorGenerateLinkRequest.parse(data))
        .then((response) => response.data)
        .then(OrderDataCollectorGenerateLinkResponse.parse),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

const useOrderDataCollectorIsValidLinkMutation = () => {
  return useMutation<OrderDataCollectorIsValidLinkResponse, Error, String>({
    mutationFn: (data) =>
      api
        .post('/odc/data/is-valid/link', data)
        .then((response) => response.data)
        .then(OrderDataCollectorIsValidLinkResponse.parse),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
  })
}

export {
  useCreateOrderMutation,
  useUpdateOrderMutation,
  useCancelOrderMutation,
  useTentativePriceMutation,
  useOrderSummaryMutation,
  useOrderCustomFieldMutation,
  useOrderDataCollectorGenerateLinkMutation,
  useOrderDataCollectorIsValidLinkMutation
}
