import React, { createContext, useContext, useEffect, useReducer } from 'react'
import { TicketDocument, TicketDocReservation } from '@/data/ticket'
import { PromotionDetailResponse, PurchaseInfo } from '@/data/order'
import { PaymentType } from '@/data/payment'
import { Step } from './types'
import { StoreManager } from '@/data/storeManager'
const store = StoreManager.getInstance()

type SelectedTickets = Array<{
  ticket: TicketDocument
  quantity: number
}>

type SeatSelectionState = {
  selectedTickets: SelectedTickets
  reservationId?: string
  reservation?: TicketDocReservation
  reservationStartTimestamp?: number,
  currentTime?: number
}

type PurchaseInfoState = {
  orderId?: string
  purchaseInfo?: PurchaseInfo
}

type PaymentState = {
  paymentType: PaymentType
  paymentId?: string
}

type PromotionDetailsState = {
  promotionDetails: Array<PromotionDetailResponse>
}

type SeatSelectionAction =
  | { type: 'RESET_SEAT_SELECTION' }
  | { type: 'SET_SELECTED_TICKETS'; payload: SelectedTickets }
  | { type: 'SET_RESERVATION_ID'; payload: string }
  | { type: 'SET_RESERVATION'; payload: TicketDocReservation }
  | { type: 'SET_CURRENT_TIME'; payload: number }

type PurchaseInfoAction =
  | { type: 'RESET_PURCHASE_INFO' }
  | { type: 'SET_ORDER_ID'; payload: string }
  | { type: 'SET_PURCHASE_INFO'; payload: PurchaseInfo }

type PaymentAction =
  | { type: 'RESET_PAYMENT' }
  | { type: 'SET_PAYMENT_TYPE'; payload: PaymentType }
  | { type: 'SET_PAYMENT_ID'; payload: string }

const seatSelectionInitialState: SeatSelectionState = {
  selectedTickets: [],
  reservationId: undefined,
  reservation: undefined,
  currentTime: undefined
}

const purchaseInfoInitialState: PurchaseInfoState = {
  orderId: undefined,
  purchaseInfo: {
    name: "",
    phone: "",
    email: "",
    delivery: null,
    customFields: [],
  }
}

const paymentInitialState: PaymentState = {
  paymentType: PaymentType.QrCode,
  paymentId: undefined,
}

const seatSelectionReducer = (
  state: SeatSelectionState,
  action: SeatSelectionAction
): SeatSelectionState => {
  switch (action.type) {
    
    case 'RESET_SEAT_SELECTION':
      return { ...state, selectedTickets: [], reservationId: undefined, reservation: undefined, reservationStartTimestamp: undefined }
    case 'SET_SELECTED_TICKETS':
      return { ...state, selectedTickets: action.payload }
    case 'SET_RESERVATION_ID':
      return { ...state, reservationId: action.payload }
    case 'SET_RESERVATION':
      return {
        ...state,
        reservation: action.payload,
        reservationStartTimestamp: new Date().getTime(),
      }
    case 'SET_CURRENT_TIME':
      return {...state, currentTime: action.payload}
    default:
      return state
  }
}

const purchaseInfoReducer = (
  state: PurchaseInfoState,
  action: PurchaseInfoAction
): PurchaseInfoState => {
  switch (action.type) {
    case 'RESET_PURCHASE_INFO':
      return { ...purchaseInfoInitialState }
      // return {...state, orderId: undefined, purchaseInfo: {name: "", phone: "", email: "", delivery: null, customFields: state.purchaseInfo?.customFields}}
    case 'SET_ORDER_ID':
      return { ...state, orderId: action.payload }
    case 'SET_PURCHASE_INFO':
      return { ...state, purchaseInfo: action.payload }
    default:
      return state
  }
}

const paymentReducer = (
  state: PaymentState,
  action: PaymentAction
): PaymentState => {
  switch (action.type) {
    case 'RESET_PAYMENT':
      return { ...paymentInitialState }
    case 'SET_PAYMENT_TYPE':
      return { ...state, paymentType: action.payload }
    case 'SET_PAYMENT_ID':
      return { ...state, paymentId: action.payload }
    default:
      return state
  }
}

type StepDataState = {
  seatSelectionState: SeatSelectionState
  seatSelectionDispatch: React.Dispatch<SeatSelectionAction>
  purchaseInfoState: PurchaseInfoState
  purchaseInfoDispatch: React.Dispatch<PurchaseInfoAction>
  paymentState: PaymentState
  paymentDispatch: React.Dispatch<PaymentAction>
  resetBookingState: () => void
}

const StepDataContext = createContext<StepDataState | undefined>(undefined)

const useStepDataContext = () => {
  const context = useContext(StepDataContext)
  if (!context) {
    throw new Error('useStepDataContext must be used within a StepDataProvider')
  }
  return context
}

const usePersistedReducer = (
  reducer: React.Reducer<any, any>,
  initialState: any,
  key: Step
) => {
  const [state, dispatch] = useReducer(reducer, initialState, (initial) => {
    const persistedState = store.getItem(key)
    return persistedState ? JSON.parse(persistedState) : initial
  })

  useEffect(() => {
    store.setItem(key, JSON.stringify(state))
  }, [key, state])

  return [state, dispatch]
}

const StepDataProvider = ({ children }: { children: React.ReactNode }) => {
  const [seatSelectionState, seatSelectionDispatch] = usePersistedReducer(
    seatSelectionReducer,
    seatSelectionInitialState,
    Step.SeatSelection
  )
  const [purchaseInfoState, purchaseInfoDispatch] = usePersistedReducer(
    purchaseInfoReducer,
    purchaseInfoInitialState,
    Step.PurchaseInfo
  )
  const [paymentState, paymentDispatch] = usePersistedReducer(
    paymentReducer,
    paymentInitialState,
    Step.Payment
  )

  const resetBookingState = () => {
    seatSelectionDispatch({ type: 'RESET_SEAT_SELECTION' })
    purchaseInfoDispatch({ type: 'RESET_PURCHASE_INFO' })
    paymentDispatch({ type: 'RESET_PAYMENT' })
    
  }

  const StepDataState: StepDataState = {
    seatSelectionState,
    seatSelectionDispatch,
    purchaseInfoState,
    purchaseInfoDispatch,
    paymentState,
    paymentDispatch,
    resetBookingState,
  }

  return (
    <StepDataContext.Provider value={StepDataState}>
      {children}
    </StepDataContext.Provider>
  )
}

export { StepDataProvider, useStepDataContext }
