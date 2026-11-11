import { RESERVATION_TIMEOUT } from '@/constants'
import { Step, useStepNavContext } from '../BookingSteps/StepNavContext'
import { useStepDataContext } from '../BookingSteps/StepDataContext'
import { CountdownTimer } from './CountdownTimer'
import { useEffect } from 'react'

const ReservationCountdown = () => {
  const { currentStep, resetStep } = useStepNavContext()
  const { seatSelectionState, resetBookingState } = useStepDataContext()
  const startTime = seatSelectionState.reservationStartTimestamp

  const onTimeout = () => {
    resetStep()
    resetBookingState()
  }

  

  useEffect(() => {
    if (!startTime) {
      onTimeout()
    }
  }, [startTime])

  if (!startTime || currentStep === Step.Confirmation) {
    return null
  }

  if(seatSelectionState.reservation?.expireInS == -1) {
    return
  }

  return (
    <CountdownTimer
      startTime={startTime}
      duration={(seatSelectionState.reservation?.expireInS ?? RESERVATION_TIMEOUT) * 1000}
      onTimeout={onTimeout}
    />
  )
}

export { ReservationCountdown }
