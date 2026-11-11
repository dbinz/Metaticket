import { cn } from '@/lib/utils'
import { Step, ORDERED_STEPS, useStepNavContext } from './StepNavContext'

import { DefaultStepProps } from './types'

import { SeatSelection } from '../SeatSelection'
import { PurchaseInfo } from '../PurchaseInfo'
import { Payment } from '../Payment'
import { Confirmation } from '../Confirmation'
import { PromotionDetailsContextProvider } from './PromotionDetailsContext'
import { PromotionDetailResponse } from '@/data/order'
import { useEffect, useState } from 'react'
import { useShowContext } from '../ShowContext'

const STEP_LABEL = {
  [Step.SeatSelection]: 'Chọn vé',
  [Step.PurchaseInfo]: 'Điền thông tin',
  [Step.Payment]: 'Thanh toán',
  [Step.Confirmation]: 'Hoàn tất',
}

const StepContent = ({ step, ...rest }: DefaultStepProps) => {
  switch (step) {
    case Step.SeatSelection:
    default:
      return <SeatSelection {...rest} />
    case Step.PurchaseInfo:
      return <PurchaseInfo step={step} {...rest} />
    case Step.Payment:
      return <Payment step={step} {...rest} />
    case Step.Confirmation:
      return <Confirmation />
  }
}

/**
 * Overview of the step styling:
 * Each step will be displayed with 2 pseudo elements, which are triangles.
 * - Left one is with the same color as the outer background
 * - Right one is with the same color as the step background
 *
 * Both to create the arrow shape of the step.
 */
const StepsContainer = () => {
  const { currentStep, completedSteps, goBack, completeCurrentStep, resetStep } =
    useStepNavContext()
  
  const [promotionDetails, setPromotionDetails] = useState<PromotionDetailResponse[]>([])
  const [promotionCode, setPromotionCode] = useState<string>("")
  const { session, show } = useShowContext()
  const [priceTotal, setPriceTotal] = useState(0);
  
  useEffect(() => {
    if(session.id != localStorage.getItem("lastSession")) {
      resetStep()
    }
    localStorage.setItem("lastSession", session.id);
  }, [])

  useEffect(() => {
    if(currentStep == Step.SeatSelection) {
      setPromotionDetails([])
      setPromotionCode("")
    }
  }, [currentStep])
  

  const highlightedSteps = [...completedSteps, currentStep]

  return (
    <PromotionDetailsContextProvider
      value={{
        priceTotal,
        setPriceTotal,
        promotionDetails,
        setPromotionDetails,
        promotionCode,
        setPromotionCode
      }}
    >
      <div className="w-full">
        <div className="w-full flex gap-2 sm:gap-3">
          {ORDERED_STEPS.map((stepKey, index) => (
            <div
              key={stepKey}
              className={cn(
                'sm:basis-1/4 relative h-[30px] sm:h-[54px] cursor-default inline-flex items-center justify-center rounded-[2px] sm:rounded-sm',
                'last:mr-1',
                currentStep === stepKey && 'flex-1 font-bold',
                highlightedSteps.includes(stepKey)
                  ? 'bg-accent text-accent-foreground'
                  : 'bg-tertiary text-tertiary-foreground'
              )}
            >
              <div
                className={cn(
                  'absolute w-0 h-0 left-0',
                  'border-solid border-t-[15px] sm:border-t-[26px] border-r-[0] border-b-[15px] sm:border-b-[26px] border-l-[7px]',
                  'border-t-transparent border-r-transparent border-b-transparent',
                  'border-l-background',
                  index === 0 && 'hidden'
                )}
              />

              <div className="w-full flex gap-2 items-center justify-center min-w-[30px] ml-1">
                {currentStep === stepKey ? (
                  <>
                    <span className="basis-0 text-sm sm:hidden">
                      {index + 1}.
                    </span>
                    <span className="text-sm">{STEP_LABEL[stepKey]}</span>
                  </>
                ) : (
                  <>
                    <span className="text-sm sm:hidden w-full text-center font-bold">
                      {index + 1}
                    </span>
                    <span className="text-sm hidden sm:block">
                      {STEP_LABEL[stepKey]}
                    </span>
                  </>
                )}
              </div>
              <div
                className={cn(
                  'absolute w-0 h-0 right-[-6px]',
                  'border-solid border-t-[15px] sm:border-t-[26px] border-r-[0] border-b-[15px] sm:border-b-[26px] border-l-[7px]',
                  'border-t-transparent border-r-transparent border-b-transparent',
                  highlightedSteps.includes(stepKey)
                    ? 'border-l-accent'
                    : 'border-l-tertiary'
                )}
              />
            </div>
          ))}
        </div>

        <div>
          <StepContent
            step={currentStep}
            onBack={goBack}
            onSubmit={(numberOfStep?: number) => completeCurrentStep(numberOfStep)}
          />
        </div>
      </div>
    </PromotionDetailsContextProvider>
    
  )
}

export { StepsContainer }
