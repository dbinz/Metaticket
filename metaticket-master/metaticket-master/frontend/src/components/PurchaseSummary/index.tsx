import { Trash2 } from 'lucide-react'
import { Button } from '../ui/button'
import { Input } from '../ui/input'
import { TicketDocument } from '@/data/ticket'
import { Money } from '../utils/Money'
import { ENABLE_PROMO_CODE } from '@/constants'
import { ReservationCountdown } from '../ReservationCountdown'
import { TotalPrice } from './TotalPrice'
import { useShowContext } from '../ShowContext'
import { ChangeEvent, useState } from 'react'
import {cn} from "@/lib/utils";

const PurchaseSummary = ({
  selectedTickets,
  editMode = true,
  showTicket = true,
  hideTitle = false,
  showQuantity = false,
  showQuantityEditButton = false,
  usePriceFromLocal = false,
  onSeatRemoved,
  updateTicketQuantity
}: {
  selectedTickets: Array<{
    ticket: TicketDocument
    quantity: number
  }>
  editMode?: boolean,
  showTicket?: boolean,
  hideTitle?: boolean,
  showQuantity?: boolean,
  showQuantityEditButton?: boolean,
  usePriceFromLocal?: boolean,
  onSeatRemoved?: (sid: string) => void
  updateTicketQuantity?: (sid: string, quantity: number) => void
}) => {

  const { show } = useShowContext()

  

  const onRemoveTicket = (id: string) => {
    if (editMode) {
      onSeatRemoved?.(id)
    }
  }

  const plusQuantity = (sid: string, currentQuantity: number) => {
    updateTicketQuantity?.(sid, currentQuantity + 1)
  }

  const minusQuantity = (sid: string, currentQuantity: number) => {
    if(currentQuantity == 1) {
      return
    }
    updateTicketQuantity?.(sid, currentQuantity - 1)
  }

  return (
    <div>
      {!hideTitle && (
        <div className="flex justify-between mb-4 border-b-2 border-tertiary">
          <h2 className="text-xl font-bold uppercase ">Thông tin đặt vé</h2>
          <ReservationCountdown />
        </div>
      )}
      
      {showTicket ? (
        <table className="w-full mb-4">
          <thead>
            <tr className="flex">
              <th className="flex-1 text-left">Loại vé</th>
              <th className={cn("flex text-center", !showQuantity && "hidden")}>Số lượng</th>
              <th className={cn("flex-1 text-right")}>Giá vé</th>
              <th className={cn("w-7", !editMode && "hidden")}></th>
            </tr>
          </thead>

          <tbody>
            {selectedTickets.map(({ ticket, quantity }) => (
              <tr key={ticket.sid} className="flex">
                <td className="flex-1">
                  {ticket.dname ? ticket.dname : `${ticket.priceCat} | ${ticket.sid}`}
                </td>
                {showQuantity ? (
                  <td className='flex-1'>
                    <div className='w-full h-full flex flex-row justify-center items-center'>
                      {(showQuantityEditButton && ticket.remCap > 1) && (
                        <Button
                          disabled={ticket.remCap == 1 || quantity == 1}
                          variant="primary"
                          className="uppercase w-5 h-5 p-2 mr-2"
                          onClick={() => minusQuantity(ticket.sid, quantity)}
                        >
                          -
                        </Button>
                      )}
                      
                      <div className="text-1xl font-bold">{quantity}</div>

                      {(showQuantityEditButton && ticket.remCap > 1) && (
                        <Button
                          disabled={quantity == ticket.remCap}
                          variant="primary"
                          className="uppercase w-5 h-5 p-2 ml-2"
                          onClick={() => plusQuantity(ticket.sid, quantity)}
                        >
                          +
                        </Button>
                      )}
                      
                      
                    </div>
                  </td>
                ) : null}
                
                <td className="flex-1 flex items-center justify-end">
                  <Money amount={ticket.basePrice}/>
                </td>
                {editMode && (
                  <td className="w-7 flex items-center justify-end">
                    <Trash2
                      size={16}
                      className="cursor-pointer"
                      onClick={() => onRemoveTicket(ticket.sid)}
                    />
                  </td>
                )}
              </tr>
            ))}
            {(selectedTickets.length > 0 && show.shipPrice > 0) ? (
              <tr className="flex">
                <td className="flex-1">
                  Vận chuyển
                </td>
                <td className="flex-1 text-right">
                  <Money amount={show.shipPrice} />
                </td>
              </tr>
            ) : null}
            
            

            {selectedTickets.length === 0 && (
              <tr className="flex">
                <td className="flex-1 text-center italic my-2" colSpan={3}>
                  Vui lòng chọn vé
                </td>
              </tr>
            )}
          </tbody>
        </table>
      ) : null}

      <TotalPrice selectedTickets={selectedTickets} showPromotionCodeBox={editMode} usePriceFromLocal={usePriceFromLocal}/>
    </div>
  )
}

export { PurchaseSummary }
