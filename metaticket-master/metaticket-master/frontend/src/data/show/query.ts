import { useQuery } from '@tanstack/react-query'
import { api } from '../api'
import { ShowDocument } from './types'
import { DEFAULT_STALE_TIME } from '../constants'

const useShowQuery = (id: string) => {
  return useQuery<ShowDocument, Error>({
    queryKey: ['show', id],
    queryFn: () =>
      api
        .get('/show/fetch', { params: { id } })
        .then((response) => response.data)
        .then(data => {
          var dataParsed = ShowDocument.parse(data)
          return dataParsed
        }),
    onError: (error) => {
      // TODO: add error handling
      console.error(error)
    },
    staleTime: DEFAULT_STALE_TIME,
  })
}

export { useShowQuery }
