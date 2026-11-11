enum Step {
  SeatSelection = 'SeatSelection',
  PurchaseInfo = 'PurchaseInfo',
  Payment = 'Payment',
  Confirmation = 'Confirmation',
}

type DefaultStepProps = {
  step: Step
  onBack: () => void
  onSubmit: (numberOfStep?: number) => void
}

export { Step }
export type { DefaultStepProps }
