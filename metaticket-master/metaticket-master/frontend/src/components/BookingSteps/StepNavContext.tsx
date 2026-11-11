import {
  ReactNode,
  createContext,
  useContext,
  useEffect,
  useState,
} from 'react'
import { Step } from './types'
import { StoreManager } from '@/data/storeManager'
import { useShowContext } from '../ShowContext'
const store = StoreManager.getInstance()

const ORDERED_STEPS = [
  Step.SeatSelection,
  Step.PurchaseInfo,
  Step.Payment,
  Step.Confirmation,
]

type StepNavContextValue = {
  currentStep: Step
  completedSteps: Step[]
  goBack: () => void
  completeCurrentStep: (numberOfStep?: number) => void
  resetStep: () => void
}

const StepNavContext = createContext<StepNavContextValue | undefined>(undefined)

const useStepNavContext = () => {
  const context = useContext(StepNavContext)
  if (!context) {
    throw new Error('useStepNavContext must be used within a StepNavProvider')
  }
  return context
}

const persistState = (key: string, state: any) => {
  store.setItem(key, JSON.stringify(state))
}

const loadState = (key: string, defaultState: any) => {
  const persistedState = store.getItem(key)
  return persistedState ? JSON.parse(persistedState) : defaultState
}

const StepNavProvider = ({ children }: { children: ReactNode }) => {

  const [currentStep, setCurrentStep] = useState<Step>(
    loadState(`currentStep`, Step.SeatSelection)
  )

  const completedSteps = ORDERED_STEPS.slice(
    0,
    ORDERED_STEPS.indexOf(currentStep)
  )

  useEffect(() => {
    persistState(`currentStep`, currentStep)
  }, [currentStep])

  const goBack = () => {
    const currentIndex = ORDERED_STEPS.indexOf(currentStep)
    if (currentIndex > 0) {
      setCurrentStep(ORDERED_STEPS[currentIndex - 1])
    }
  }

  // assumption: this is only called after completing a step
  const completeCurrentStep = (numberOfStep?: number) => {
    const currentIndex = ORDERED_STEPS.indexOf(currentStep)
    var step = numberOfStep ?? 1;
    if (currentIndex < ORDERED_STEPS.length - step) {
      setCurrentStep(ORDERED_STEPS[currentIndex + step])
    }
  }

  const resetStep = () => {
    setCurrentStep(ORDERED_STEPS[0])
  }

  const stepNavContextValue: StepNavContextValue = {
    currentStep,
    completedSteps,
    goBack,
    completeCurrentStep,
    resetStep,
  }

  return (
    <StepNavContext.Provider value={stepNavContextValue}>
      {children}
    </StepNavContext.Provider>
  )
}

export { Step, ORDERED_STEPS, StepNavProvider, useStepNavContext }
export type { StepNavContextValue }
