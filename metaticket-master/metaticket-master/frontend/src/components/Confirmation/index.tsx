import * as StepLayout from '../StepLayout'
import { CheckCircle } from 'lucide-react'
import { DataLabel } from '../utils/DataLabel'
import { useStepDataContext } from '../BookingSteps/StepDataContext'
import { useEffect, useState } from 'react'
import { useStepNavContext } from '../BookingSteps/StepNavContext'
import { Button } from '../ui/button'
import { OrderSummaryResponse, useOrderSummaryMutation } from '@/data/order'
import { OrderSummaryInfoOverview } from '../OrderSummaryInfoOverview'
import { Money } from '../utils/Money'

const Confirmation = () => {
  const { resetStep } = useStepNavContext()
  
  const {
    seatSelectionState,
    purchaseInfoState,
    paymentState,
    resetBookingState,
  } = useStepDataContext()

  // const { orderId: persistedOrderId } =
  // purchaseInfoState

  // const [orderId, setOrderId] = useState<string | undefined>(
  //   persistedOrderId
  // )

  const { mutate: getOrderSummary } =
  useOrderSummaryMutation()

  const [orderSummary, setOrderSummary] = useState<OrderSummaryResponse | undefined>()

  const resetAll = () => {
    console.log('reseting all state')
    resetBookingState()
    resetStep()
  }

  // reset persisted state once user leaves the page
  useEffect(() => {
    window.addEventListener('beforeunload', resetAll)
    getOrderSummary(purchaseInfoState.orderId ?? "", {
      // onError: handleError,
      onSuccess: (data) => {
        setOrderSummary(data)
      },
    })
    return () => {
      window.removeEventListener('beforeunload', resetAll)
    }
  }, [])

  return (
    <StepLayout.Wrapper>
      <div className="w-full mt-4">
        <div className="flex flex-col gap-2 items-center text-success mb-9">
          <CheckCircle size={64} />
          <div className="text-2xl font-bold">Đặt vé thành công!</div>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8 mb-9">
          <DataLabel
            label="Thời gian"
            value={orderSummary?.paymentInfo?.payTime ?? ""}
          />
          <DataLabel
            className="break-all"
            label="Mã đơn hàng"
            value={orderSummary?.orderCode ?? ""}
          />
          <DataLabel
            label="Phương thức thanh toán"
            value={orderSummary?.paymentInfo?.payMethod ?? ""}
          />
          <DataLabel
            label="Trạng thái"
            value={orderSummary?.paymentInfo?.payStatus ?? ""}
          />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div className='w-full flex flex-col'>
            <table className="w-full">
              <thead>
                <tr className="flex">
                  <th className="flex-1 text-left">Loại vé</th>
                  <th className="flex text-center">Số lượng</th>
                  <th className="flex-1 text-right">Thành tiền</th>
                </tr>
              </thead>

              <tbody>
                {orderSummary?.orderDetail?.prices?.map((price, index) => (
                  <tr key={index} className="flex">
                      <td className="flex-1">
                        {price.name}
                      </td>
                      <td className='flex-1 text-center'>
                        <div className="text-1xl">{price.quantity}</div>
                      </td>
                      <td className="flex-1 flex items-center justify-end">
                        <Money amount={price.price}/>
                      </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="divide-y dash"/>
            {orderSummary?.orderDetail?.promotions?.map((promotion, index) => (
              <div className='flex flex-row justify-between items-center mt-1' key={index}>
                <div>
                  {promotion.name}
                </div>
                <div className='text-right'>
                  <Money amount={-promotion.price}/>
                </div>
              </div>
            ))}
            <div className='flex flex-row justify-between items-center mt-1'>
              <div className='text-xl uppercase'>
                Tổng
              </div>
              <div className='text-right font-bold'>
                <Money amount={orderSummary?.orderDetail?.total ?? 0} />
              </div>
            </div>
          </div>
          <div className="">
            <OrderSummaryInfoOverview
              buyerInfo={orderSummary?.buyerInfo}
              recipientInfo={orderSummary?.recipientInfo}
              customFields={orderSummary?.customFields}
            />
            {orderSummary?.paymentNote && (
              <div className="mb-5">
                <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
                  Thông tin thanh toán
                </h2>
                <div
                    className="mt-2 italic text-sm" dangerouslySetInnerHTML={{__html: orderSummary?.paymentNote}}
                >
                </div>
              </div>
            )}
          </div>
        </div>
        <div className="flex justify-center">
          <Button
            variant="primary"
            className="w-2/3 lg:w-1/2 uppercase"
            onClick={resetAll}
          >
            <span>Tiếp tục mua vé</span>
          </Button>
          
        </div>
      </div>
    </StepLayout.Wrapper>
  )
}

export { Confirmation }
