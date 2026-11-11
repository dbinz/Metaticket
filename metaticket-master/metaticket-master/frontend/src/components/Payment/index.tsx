import { useContext, useEffect, useState } from 'react'
import * as StepLayout from '../StepLayout'
import { ORDERED_STEPS } from '../BookingSteps/StepNavContext'
import { Button } from '@/components/ui/button'
import { DefaultStepProps } from '../BookingSteps/types'
import { PurchaseSummary } from '../PurchaseSummary'
import { useStepDataContext } from '../BookingSteps/StepDataContext'
import { PaymentStatus, PaymentType } from '@/data/payment'
import { Loader2, ArrowRight, ArrowLeft } from 'lucide-react'
import { PurchaseInfoOverview } from '../PurchaseInfoOverview'
import { PaymentTypeSelection } from './PaymentTypeSelection'
import {
  useCreatePaymentMutation,
  usePaymentStatusQuery,
  useStartPaymentMutation,
} from '@/data/payment/query'
import {
  useCancelOrderMutation
} from '@/data/order/query'
import {
  BrowserBlockPopupDialog,
  PaymentFailedDialog,
  TransactionPendingDialog,
} from '../utils/MessageDialogs'
import { useReservationQueryOnce, isReservationExpired } from '@/data/ticket'
import { useStepNavContext } from '../BookingSteps/StepNavContext'
import Link from 'next/link'
import PromotionDetailsContext from '../BookingSteps/PromotionDetailsContext'
import {useShowContext} from "@/components/ShowContext";
const Payment = ({ step, onBack, onSubmit }: DefaultStepProps) => {
  const { resetStep } = useStepNavContext()
  const {
    seatSelectionState,
    purchaseInfoState,
    purchaseInfoDispatch,
    paymentState,
    paymentDispatch,
    resetBookingState,
  } = useStepDataContext()

  // check if reservation is still active (status is Created or Open)
  // if not, reset step and booking state
  const { data: reservationData } = useReservationQueryOnce(
    seatSelectionState.reservationId
  )
  const isBookingExpired =
    reservationData && isReservationExpired(reservationData)

  useEffect(() => {
    window.scrollTo(0, 0)
    setPaymentType(session.paymentMethods.includes(paymentType)? paymentType : session.paymentMethods[0])
    if(paymentId && paymentId.trim()) {
        // Update local state
        setPaymentId(paymentId)
        setPaymentStartAfterRefreshPage(true)
        console.log("payment: ", paymentId, paymentType)

        // Save the payment type and token to the context
        paymentDispatch({
          type: 'SET_PAYMENT_ID',
          payload: paymentId,
        })
        // Start the payment process
        // startPaymentMutation.mutate(
        //   {
        //     paymentId: paymentId,
        //     paymentType,
        //   },
        //   {
        //     onError: (err) => {
        //       console.error(err)
        //     },
        //     onSuccess: (data) => {
        //       debugger
        //       setPaymentUrl(data.redirectUrl)
        //       paymentDispatch({
        //         type: 'SET_PAYMENT_TYPE',
        //         payload: paymentType,
        //       })
        //     },
        //   }
        // )
    }
  }, [])
  useEffect(() => {
    if (isBookingExpired) {
      resetToFirstStep()
    }
  }, [isBookingExpired])

  const { session } = useShowContext()
  
  const { paymentType: persistedPaymentType, paymentId: persistedPaymentId } =
    paymentState
  const [paymentType, setPaymentType] =
    useState<PaymentType>(persistedPaymentType)
  const [paymentId, setPaymentId] = useState<string | undefined>(
    persistedPaymentId
  )
  const [paymentUrl, setPaymentUrl] = useState<string | undefined>(undefined)

  const [paymentStartAfterRefreshPage, setPaymentStartAfterRefreshPage] = useState(false)

  const createPaymentMutation = useCreatePaymentMutation()
  const cancelOrderMutation = useCancelOrderMutation()
  const startPaymentMutation = useStartPaymentMutation()

  const submitPayment = () => {
    // call payment service to create payment
    if (purchaseInfoState.orderId) {
      createPaymentMutation.mutate(
        {
          orderId: purchaseInfoState.orderId
        },
        {
          onError: (err) => {
            console.error(err)
          },
          onSuccess: ({ paymentId }) => {
            // Update local state
            setPaymentId(paymentId)

            // Save the payment type and token to the context
            paymentDispatch({
              type: 'SET_PAYMENT_ID',
              payload: paymentId,
            })

            // Start the payment process
            startPaymentMutation.mutate(
              {
                paymentId,
                paymentType,
              },
              {
                onError: (err) => {
                  console.error(err)
                },
                onSuccess: (data) => {
                  setPaymentUrl(data.redirectUrl)
                  paymentDispatch({
                    type: 'SET_PAYMENT_TYPE',
                    payload: paymentType,
                  })
                },
              }
            )
          },
        }
      )
    }
  }

  const cancelOrder = () => {
    if(!purchaseInfoState.orderId) {
      return onBack()
    }
    cancelOrderMutation.mutate(
      {
        orderId: purchaseInfoState.orderId
      },
      {
        onError: (err) => {
          console.error(err)
          onBack()
        },
        onSuccess: ({}) => {
          purchaseInfoDispatch({ type: 'RESET_PURCHASE_INFO' })
          onBack()
        },
      }
    )
  
  }

  // watch paymentUrl to redirect
  const [isGatewayOpened, setIsGatewayOpened] = useState(false)
  useEffect(() => {
    if (paymentUrl && !isGatewayOpened) {
      processPaymentGateWay()
      
    }
  }, [paymentUrl, isGatewayOpened])

  // periodically check for payment status
  const [isPaymentFailed, setIsPaymentFailed] = useState(false)
  const isPaymentStarted = !!(paymentId && paymentUrl) || (paymentStartAfterRefreshPage && !isPaymentFailed)
  const [openBrowserBlockPopup, setOpenBrowserBlockPopup] = useState(false)
  console.log(paymentId, paymentUrl, isPaymentFailed)
  const { data: paymentData } = usePaymentStatusQuery(
    paymentId,
    isPaymentStarted && !isPaymentFailed
  )

  const paymentStatus = paymentData?.status
  useEffect(() => {
    if (paymentStatus === PaymentStatus.Succeed) {
      onSubmit()
    }

    if (paymentStatus === PaymentStatus.Failed || paymentStatus === PaymentStatus.Cancelled) {
      resetStep()
      resetBookingState()
      
      // // Clear the payment state
      // paymentDispatch({
      //   type: 'RESET_PAYMENT',
      // })
      // clear local state
      setPaymentId(undefined)
      setPaymentUrl(undefined)
      setIsPaymentFailed(false)
    }
  }, [paymentStatus, paymentId])

  if (!purchaseInfoState.orderId) {
    return <div>Invalid order ID</div>
  }

  const onBtnClosePaymentErrorDialogPressed = () => {
    resetStep()
    resetBookingState()
    
    // // Clear the payment state
    // paymentDispatch({
    //   type: 'RESET_PAYMENT',
    // })
    // clear local state
    setPaymentId(undefined)
    setPaymentUrl(undefined)
    setIsPaymentFailed(false)
  }

  const processPaymentGateWay = () => {
    const newTab = window.open(paymentUrl, '_blank')
      // const newTab = window.open("http://localhost:3000/vnpay-payment-result", '_blank')
      if (newTab) {
        newTab.focus()
        // var timer = setInterval(function() {
        //   if(!newTab.open) {
        //     setOpenBrowserBlockPopup(true)
        //   }
        //   clearInterval(timer)
        // }, 500)
        // var timer = setInterval(function() {
        //   if(newTab.closed) {
        //     clearInterval(timer)
        //     alert("tab closed")
        //   }
        // })
      } else {
        setOpenBrowserBlockPopup(true)
      }
  }

  const resetToFirstStep = () => {
    resetStep()
    resetBookingState()
    setPaymentStartAfterRefreshPage(false)
  }

  return (
    <StepLayout.Wrapper>
      <StepLayout.Main>
        <PaymentTypeSelection
          paymentTypes={session.paymentMethods}
          defaultPaymentType={session.paymentMethods.includes(paymentType)? paymentType : session.paymentMethods[0]}
          onChange={setPaymentType}
        />
        <PurchaseInfoOverview purchaseInfo={purchaseInfoState.purchaseInfo} />
      </StepLayout.Main>
      <StepLayout.Sidebar>
        <PurchaseSummary
          selectedTickets={seatSelectionState.selectedTickets}
          editMode={false}
          showQuantity
          usePriceFromLocal={true}
        />

        <div className="flex gap-2 my-2">
          <Button
            onClick={cancelOrder}
            disabled={ORDERED_STEPS.indexOf(step) === 0}
            variant="secondary"
            className="basis-1/2 uppercase"
          >
            <ArrowLeft size={20} />
            <span className="flex-1">Quay lại</span>
          </Button>
          <Button
            variant="primary"
            className="basis-1/2 uppercase"
            onClick={submitPayment}
          >
            {createPaymentMutation.isLoading ||
            startPaymentMutation.isLoading ? (
              <Loader2 className="h-5 w-5 animate-spin" />
            ) : (
              <>
                <span className="flex-1">Thanh toán</span>
                <ArrowRight size={20} />
              </>
            )}
          </Button>
        </div>
      </StepLayout.Sidebar>
      <TransactionPendingDialog open={isPaymentStarted} />
      <BrowserBlockPopupDialog open={openBrowserBlockPopup} processPayment={processPaymentGateWay} cancelPayment={resetToFirstStep}/>
      {/* <PaymentFailedDialog
        open={isPaymentFailed}
        orderId={purchaseInfoState.orderId}
        onClose={onBtnClosePaymentErrorDialogPressed}
      /> */}
    </StepLayout.Wrapper>
  ) 
}

export { Payment }
