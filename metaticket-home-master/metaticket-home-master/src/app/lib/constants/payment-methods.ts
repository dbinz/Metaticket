import { appIcons } from '../theme';
import { PaymentType } from '../enums';

export const listPaymentMethods = [
  {
    name: 'QR | Chuyển khoản | Ví điện tử',
    icon: appIcons.icQrCode,
    code: PaymentType.QrCode,
  },
  {
    name: 'Thẻ ATM nội địa',
    icon: appIcons.icAtmCard,
    code: PaymentType.DomesticCard,
  },
  {
    name: 'Thẻ quốc tế (Visa/Master Card/...)',
    icon: appIcons.icVisa,
    code: PaymentType.InternationalCard,
  },
  {
    name: 'Thanh toán với VietQR',
    icon: appIcons.icVietQr,
    code: PaymentType.VietQr,
  },
];
