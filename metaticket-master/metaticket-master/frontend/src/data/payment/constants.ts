import { PaymentType } from './types'
import { QrCode, CreditCard, Landmark } from 'lucide-react'

const PAYMENT_TYPE_LABELS = {
  [PaymentType.DomesticCard]: {
    label: 'Thẻ ATM nội địa',
    icon: Landmark,
  },
  [PaymentType.InternationalCard]: {
    label: 'Thẻ quốc tế (Visa/Master Card/...)',
    icon: CreditCard,
  },
  [PaymentType.QrCode]: {
    label: 'QR | Chuyển khoản | Ví điện tử',
    icon: QrCode,
  },
  [PaymentType.VietQr]: {
    label: 'VietQR',
    icon: QrCode,
  },
}

export { PAYMENT_TYPE_LABELS }
