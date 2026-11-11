import axios, { AxiosError } from 'axios'
import { API_DOMAIN, BASE_API_PATH } from './constants'
import { headers } from 'next/dist/client/components/headers'

const api = axios.create({
  baseURL: `/${BASE_API_PATH}`
})

const getStatusCode = (error: AxiosError) => {
  if (error.response) {
    return error.response.status
  }

  // can consider return network error here
  // when this happen, there is no error.response
  return null
}

export { api, getStatusCode }
