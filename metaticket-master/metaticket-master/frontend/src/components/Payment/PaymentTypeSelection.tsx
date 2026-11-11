import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { PaymentType, PAYMENT_TYPE_LABELS } from '@/data/payment'

const PaymentTypeSelection = ({
  paymentTypes,
  defaultPaymentType,
  onChange,
}: {
  paymentTypes: PaymentType[],
  defaultPaymentType: PaymentType
  onChange: (paymentType: PaymentType) => void
}) => {
  return (
    <div className="mb-5">
      <h2 className="text-xl font-bold mb-6 uppercase border-b-2 border-tertiary">
        Phương thức thanh toán
      </h2>
      <RadioGroup defaultValue={defaultPaymentType} onValueChange={onChange}>
        {paymentTypes.map((paymentType) => {
          const { label, icon: Icon } = PAYMENT_TYPE_LABELS[paymentType]
          return (
            <div key={paymentType} className="flex items-center space-x-3 my-1">
              <RadioGroupItem value={paymentType} id={paymentType} />
              <Label htmlFor={paymentType}>
                <span className="flex items-center gap-2 font-bold">
                  <Icon size={20} />
                  {label}
                </span>
              </Label>
            </div>
          )
        })}
      </RadioGroup>
    </div>
  )
}

export { PaymentTypeSelection }
