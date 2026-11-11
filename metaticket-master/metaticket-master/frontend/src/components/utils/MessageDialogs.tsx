import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogClose,
} from '@/components/ui/alert-dialog'
import { Button } from '../ui/button'
import { Loader2, AlertCircle } from 'lucide-react'

type DialogDefaultProps = {
  open: boolean
  onClose?: () => void
}

const ReservationFailedDialog = ({
  message,
  buttonText,
  open,
  onClose,
}: {
  message: string,
  buttonText: string,
  open: boolean
  onClose: () => void
}) => {
  return (
    <AlertDialog open={open}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Thông báo</AlertDialogTitle>
          <AlertDialogDescription>
            {message}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <Button variant="primary" onClick={onClose}>
            {buttonText}
          </Button>
        </AlertDialogFooter>
        <AlertDialogClose onClick={onClose} />
      </AlertDialogContent>
    </AlertDialog>
  )
}

const TransactionPendingDialog = ({ open }: DialogDefaultProps) => {
  return (
    <AlertDialog open={open}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <Loader2 className="text-muted-foreground h-16 w-16 animate-spin w" />
          <AlertDialogTitle>GIAO DỊCH ĐANG XỬ LÝ</AlertDialogTitle>
          <AlertDialogDescription>
            <div className="max-w-[320px] text-center">
              Vui lòng chờ trong giây lát và không đóng cửa sổ mua vé!
            </div>
            <div className="italic text-sm max-w-[320px] mt-1">
              * Nếu quá trình thanh toán của bạn qua cổng thanh toán bị ngắt quãng đột ngột, bạn có thể phải chờ tối đa 30 phút cho đến khi chúng tôi nhận được thông báo từ cổng thanh toán, để có thể mua vé lại trên trình duyệt này.
            </div>
            
          </AlertDialogDescription>
        </AlertDialogHeader>
      </AlertDialogContent>
    </AlertDialog>
  )
}

const PaymentFailedDialog = ({
  open,
  onClose,
  orderId,
}: {
  open: boolean
  onClose: () => void
  orderId: string
}) => {
  return (
    <AlertDialog open={open}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Thông báo</AlertDialogTitle>
          <AlertDialogDescription>
            <div className="max-w-[320px] text-center">
              Vui lòng chờ trong giây lát và không đóng cửa sổ mua vé!
            </div>
            <div className="italic text-sm max-w-[320px] mt-1">
              * Nếu quá trình thanh toán của bạn qua cổng thanh toán bị ngắt quãng đột ngột, bạn có thể phải chờ tối đa 30 phút cho đến khi chúng tôi nhận được thông báo từ cổng thanh toán, để có thể mua vé lại trên trình duyệt này.
            </div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <div className="flex flex-col sm:flex-row gap-2 w-full">
            <Button
              variant="outline"
              className="flex-1 uppercase"
              onClick={onClose}
            >
              Quay lại
            </Button>
          </div>
        </AlertDialogFooter>
        <AlertDialogClose onClick={onClose} />
      </AlertDialogContent>
    </AlertDialog>
  )
}

const BrowserBlockPopupDialog = ({
  open,
  processPayment,
  cancelPayment
}: {
  open: boolean
  processPayment: () => void
  cancelPayment: () => void
}) => {
  return (
    <AlertDialog open={open}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Trình duyệt của bạn đang khóa pop-ups</AlertDialogTitle>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <div className="flex flex-col sm:flex-row gap-2 w-full">
            <Button
              variant="primary"
              className="flex-1 uppercase"
              onClick={processPayment}
            >
              Mở pop-ups thanh toán
            </Button>
          </div>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

export {
  ReservationFailedDialog,
  TransactionPendingDialog,
  PaymentFailedDialog,
  BrowserBlockPopupDialog
}
