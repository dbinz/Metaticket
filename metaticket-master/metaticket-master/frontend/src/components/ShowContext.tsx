import { ShowDocument, ShowSessionDocument } from '@/data/show'
import { createContext, useContext } from 'react'

type State = {
  show: ShowDocument
  session: ShowSessionDocument,
}

const Context = createContext<State | undefined>(undefined)
Context.displayName = 'ShowContext'

const useShowContext = () => {
  const context = useContext(Context)
  if (!context) {
    throw new Error('useShowContext must be used within ShowContext')
  }
  return context
}

const ShowContextProvider = ({
  value,
  children,
}: {
  value: State
  children: React.ReactNode
}) => {
  return <Context.Provider value={value}>{children}</Context.Provider>
}

export { ShowContextProvider, useShowContext }
export type { Context as ShowContext }
