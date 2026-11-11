import { TicketDocument } from '@/data/ticket'
import { Money } from '../utils/Money'
import { ChangeEvent, useContext, useEffect, useState } from 'react'
import { PromotionDetailResponse, PromotionType, useOrderSummaryMutation, useTentativePriceMutation } from '@/data/order'
import { Loader2 } from 'lucide-react'
import { useShowContext } from '../ShowContext'
import { Button } from '../ui/button'
import PromotionDetailsContext from '../BookingSteps/PromotionDetailsContext'
import {cn} from "@/lib/utils";

const TotalPrice = ({
  selectedTickets,
  showPromotionCodeBox = true,
  usePriceFromLocal = false
}: {

  selectedTickets: Array<{
    ticket: TicketDocument
    quantity: number
  }>,
  showPromotionCodeBox?: boolean,
  usePriceFromLocal?: boolean
}) => {
  const { show, session } = useShowContext()
  const [loading, setLoading] = useState(false)
  // const [priceTotal, setPriceTotal] = useState(
  //   selectedTickets.reduce((acc, { ticket }) => {
  //     return acc + ticket.basePrice
  //   }, 0)
  // )
  const promotionDetailsContext = useContext(PromotionDetailsContext)
  const [version, setVersion] = useState(0)
  const { mutate: checkPrice, isLoading } = useTentativePriceMutation(version)
  const [applyPromotionSucceed, setApplyPromotionSuccess] = useState(false)

  useEffect(() => {
    if(!usePriceFromLocal || !promotionDetailsContext.priceTotal) {
      getPrice(promotionDetailsContext.promotionCode)
    }
  }, [selectedTickets])

  const getPrice = (code: string) => {
    if(selectedTickets.length) {
      setLoading(true)
      setVersion(version + 1)
      const reservedTickets = selectedTickets.map(({ ticket, quantity }) => ({
        ticketId: ticket.id,
        quantity,
        priceCat: ticket.priceCat,
      }))
      checkPrice({
        tickets: reservedTickets,
        sessionId: session.id,
        showId: show.id,
        promotionCode: code
      }, {
        onSuccess: (data) => {
          
          setLoading(false)
          // if(data.version == version) {
          // setPriceTotal(data.priceTotal)
          promotionDetailsContext.setPriceTotal(data.priceTotal);
          promotionDetailsContext.setPromotionDetails(data.promotionDetails)
          var haveCoupon = false
          loop: for(var i = 0; i < data.promotionDetails.length; i ++) {
            if(data.promotionDetails[i].type == PromotionType.Coupon) {
              haveCoupon = true
              break loop
            }
          }
          setApplyPromotionSuccess(haveCoupon)
          // }
          // updatePromotionDetail(data)
        },
      })
    } else {
      promotionDetailsContext.setPromotionDetails([])
      promotionDetailsContext.setPromotionCode("")
      promotionDetailsContext.setPriceTotal(0)
    }
  }

  const clearPromotion = () => {
    getPrice("")
    promotionDetailsContext.setPromotionCode("")
  }

  return (
    <div className='mb-4'>
      {showPromotionCodeBox ? (
        <div className='flex items-center mt-2 h-8'>
          <input
            placeholder='Mã giảm giá'
            type="text"
            disabled={applyPromotionSucceed}
            value={promotionDetailsContext.promotionCode}
            onChange={(event: ChangeEvent<HTMLInputElement>) => promotionDetailsContext.setPromotionCode(event.target.value)}
            className="h-full w-full grow border-transparent focus:outline-none rounded-l pl-2 pr-2"
          />
          <Button
            disabled={applyPromotionSucceed ? false : (promotionDetailsContext.promotionCode.length == 0 || selectedTickets.length == 0)}
            variant="primary"
            className="uppercase grow-0 rounded-r rounded-l-none h-full whitespace-nowrap"
            onClick={() => applyPromotionSucceed ? clearPromotion() : getPrice(promotionDetailsContext.promotionCode)}
          >
            {applyPromotionSucceed ? "Xóa mã" : "Áp dụng"}
            
          </Button>
        </div>
      ) : null}
      
      {promotionDetailsContext.promotionDetails.map((promotionDetail, index) => (
        <div className={cn("flex mt-2", promotionDetail.error && "text-red-500")} key={index}>
          {promotionDetail.error ? (
            <div className="flex-1 text-right">
              {promotionDetailsContext.promotionCode} - mã đã được sử dụng hết
            </div>
          ) : (
            <>
              <div className="flex-1">{promotionDetail.name}</div>
              <div className="flex-1 text-right">
                <Money amount={-promotionDetail.priceDiscount} />
              </div>
            </>
          )}
          
        </div>
      ))}
      {/* {priceDiscount > 0 ? (
        <div className="flex mb-2">
          <div className="flex-1">Giảm giá</div>
          <div className="flex-1 text-right">
            <Money amount={-priceDiscount} />
          </div>
        </div>
      ) : null} */}
      
      <div className="flex font-bold uppercase mt-2">
        <div className="flex-1">Tổng</div>
        <div className="flex-1 text-right">
          {isLoading ? (
            <Loader2 className="h-5 w-5 animate-spin" />
          ) : (
            <Money amount={promotionDetailsContext.priceTotal} />
          )}
        </div>
      </div>
    </div>
  )
}

export { TotalPrice }
