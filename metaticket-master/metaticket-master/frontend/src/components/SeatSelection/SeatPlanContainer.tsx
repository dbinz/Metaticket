import { useState, useEffect, useRef } from 'react'
import Script from 'next/script'
import { TicketDocument, isTicketDisabled } from '@/data/ticket'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { useShowContext } from '../ShowContext'
import { Money } from '../utils/Money'
import { Button } from '../ui/button'
import { cn } from '@/lib/utils'
import {API_DOMAIN} from "@/data/constants";
import { Loader2 } from 'lucide-react'


enum SeatPlanEvent {
  ItemSelected = 'item-selected',
  ItemDeselected = 'item-deselected',
  ItemHighlight = 'item-highlight',
  ItemLowlight = 'item-lowlight',
}

type SeatPlanEventState = {
  triggerEl: HTMLElement
  triggerItem: { sid: string; id: number }
}
type SeatPlanEventData = {
  eventType: SeatPlanEvent
  state: SeatPlanEventState
}

declare global {
  interface Window {
    metaticket: any
  }
}

const SeatPlanContainer = ({
  seatPlanId,
  edType,
  tickets,
  selectedSeats,
  onSeatSelected,
  onSeatRemoved,
  updatedTickets = [],
}: {
  seatPlanId: string
  edType: string
  tickets: Array<TicketDocument>
  selectedSeats: Array<string>
  onSeatSelected: (sid: string, quantity: number) => void
  onSeatRemoved: (sid: string) => void
  updatedTickets: Array<TicketDocument>
}) => {
  const { session } = useShowContext()
  const seatPlanViewportRef = useRef<HTMLDivElement>(null)
  const seatPlanWrapperRef = useRef<any>(null)
  const [mfeReady, setMfeReady] = useState(false)
  const [spReady, setSpReady] = useState(false)
  const [openLedgend, setOpenLedgend] = useState(true)
  const seatPlannerReady = mfeReady && spReady
  const [cursorType, setCursorType] = useState("")
  const [seatPlanInitiated, setSeatPlanInitiated] = useState(false)

  const CursorZoomIn = "cursor-zoom-in"
  const CursorZoomOut = "cursor-zoom-out"
  const CursorGrab = "cursor-grab"
  

  const seatTicketMap = tickets.reduce((acc, cur) => {
    acc[cur.sid] = cur
    return acc
  }, {} as Record<string, TicketDocument>)

  

  // seat focus state, action, and effect
  const [focusSeat, setFocusSeat] = useState<SeatPlanEventState | null>(null)
  const lastFocusSeatRef = useRef<SeatPlanEventState | null>(null)
  const focusSeatTicket = (focusSeat && focusSeat.triggerItem)
    ? seatTicketMap[focusSeat.triggerItem.sid]
    : null
  const [ticketQuantity, setTicketQuantity] = useState(1)

  useEffect(() => {
    // clear selection on current focus seat when focus seat changes
    if (focusSeat && lastFocusSeatRef.current && lastFocusSeatRef.current.triggerItem) {
      seatPlanWrapperRef.current.clearSelection(seatPlanId, [
        lastFocusSeatRef.current.triggerItem.sid,
      ])
    }
    lastFocusSeatRef.current = focusSeat
  }, [focusSeat])

  const onSeatFocus = (state: SeatPlanEventState) => {
    setFocusSeat(state)
  }

  const onSeatUnfocus = (state: SeatPlanEventState) => {
    const lastFocusSeat = lastFocusSeatRef.current
    if (
      lastFocusSeat &&
      lastFocusSeat.triggerItem &&
      state.triggerItem &&
      state.triggerItem.sid &&
      lastFocusSeat.triggerItem.sid !== state.triggerItem.sid
    ) {
      return
    } else {
      
      if(state.triggerItem) {
        setFocusSeat(null)
        onSeatRemoved(state.triggerItem.sid);
      }
      
    }
  }

  const onSelectFocusSeat = () => {
    if (focusSeat) {
      // select seats
      onSeatSelected(focusSeat.triggerItem.sid, ticketQuantity)
      // clear focus seat and close popover
      setFocusSeat(null)
    }
  }

  const onDismissFocusSeat = () => {
    // clear selection
    seatPlanWrapperRef.current.clearSelection(seatPlanId, [
      focusSeat?.triggerItem.sid,
    ])
    // clear focus seat and close popover
    setFocusSeat(null)
  }

  const onSeatPlanEvent = (data: SeatPlanEventData) => {
    const { eventType, state } = data
    switch (eventType) {
      case SeatPlanEvent.ItemSelected:
        return onSeatFocus(state)
      case SeatPlanEvent.ItemDeselected:
        return onSeatUnfocus(state)
      case SeatPlanEvent.ItemHighlight:
      case SeatPlanEvent.ItemLowlight:
      default:
        return console.log('Unhandled event', eventType)
    }
  }

  // useEffect(() => {
  //   const onScroll = () => {
  //     console.log(seatPlanViewportRef?.current?.getBoundingClientRect().top)
  //   }
  //   window.removeEventListener('scroll', onScroll);
  //   window.addEventListener('scroll', onScroll, { passive: true });
  //   return () => window.removeEventListener('scroll', onScroll);
  // }, [])

  // TODO: may need to de-register seatplan listener on unmount
  useEffect(() => {
    if (seatPlannerReady && seatPlanViewportRef.current) {
      const wrapper = new window.metaticket.PlannerWrapper(edType)
      seatPlanWrapperRef.current = wrapper
      wrapper.initialize(seatPlanViewportRef.current, seatPlanId).then(() => {
        wrapper.registerListener(seatPlanId, {
          onEvent: onSeatPlanEvent,
        })

        setSeatPlanInitiated(true)
      })
    }
  }, [seatPlannerReady])

  // side effect to disable seats when tickets are updated
  useEffect(() => {
    if (seatPlanInitiated) {
      const disabledSids = tickets.filter(isTicketDisabled).map((t) => t.sid)
      seatPlanWrapperRef.current.setDisabled(true, seatPlanId, disabledSids)
    }
  }, [seatPlanInitiated])

  // side effect to disable/enable seats with new updatedTickets
  useEffect(() => {
    if (seatPlanInitiated && updatedTickets.length > 0) {
      const disabledSids = updatedTickets
        .filter(isTicketDisabled)
        .map((t) => t.sid)
      if(disabledSids.length) {
        seatPlanWrapperRef.current.setDisabled(true, seatPlanId, disabledSids)
      }
      const enabledSids = [];
      for(var i = 0; i < updatedTickets.length; i ++) {
        var isSelect = false;
        loop: for(var j = 0; j < selectedSeats.length; j ++) {
          if(updatedTickets[i].sid == selectedSeats[j]) {
            isSelect = true;
            break loop;
          }
        }
        if(!isSelect && !isTicketDisabled(updatedTickets[i])) {
          enabledSids.push(updatedTickets[i].sid);
        }
      }
      if(enabledSids) {
        seatPlanWrapperRef.current.setDisabled(false, seatPlanId, enabledSids)
      }
    }
  }, [updatedTickets, seatPlanInitiated])

  // side effect to remove seats when selectedSeats is updated
  const lastSelectedSeatsRef = useRef<Array<string>>(selectedSeats)
  useEffect(() => {
    
    const lastSelectedSeats = lastSelectedSeatsRef.current
    // tell seatplan to remove seats
    if (seatPlanInitiated && selectedSeats.length < lastSelectedSeats.length) {
      const seatsToRemove = lastSelectedSeats.filter(
        (sid) => !selectedSeats.includes(sid)
      )
      seatPlanWrapperRef.current.clearSelection(seatPlanId, seatsToRemove)
    }

    lastSelectedSeatsRef.current = selectedSeats
  }, [selectedSeats, seatPlanInitiated])

  // get popover position from seat position
  const focusSeatRect = focusSeat?.triggerEl.getBoundingClientRect() ?? {
    top: 0,
    left: 0,
    width: 0,
    height: 0,
  }

  const zoomIn = () => {
    if(!cursorType.length) {
      seatPlanWrapperRef.current.startZoom("zoom-in")
      setCursorType(CursorZoomIn)
    } else if(cursorType == CursorZoomIn) {
      seatPlanWrapperRef.current.clearTool()
      setCursorType('')
    } else {
      // seatPlanWrapperRef.current.clearTool()
      seatPlanWrapperRef.current.startZoom("zoom-in")
      setCursorType(CursorZoomIn)
    }
    
    
  }

  const zoomOut = () => {
    if(!cursorType.length) {
      seatPlanWrapperRef.current.startZoom("zoom-out")
      setCursorType(CursorZoomOut)
    } else if(cursorType == CursorZoomOut) {
      seatPlanWrapperRef.current.clearTool()
      setCursorType('')
    } else {
      // seatPlanWrapperRef.current.clearTool()
      seatPlanWrapperRef.current.startZoom("zoom-out")
      setCursorType(CursorZoomOut)
    }
  }

  const startPan = () => {
    if(!cursorType.length) {
      seatPlanWrapperRef.current.startPan()
      setCursorType(CursorGrab)
    } else if(cursorType == CursorGrab) {
      seatPlanWrapperRef.current.clearTool()
      setCursorType('')
    } else {
      // seatPlanWrapperRef.current.clearTool()
      seatPlanWrapperRef.current.startPan()
      setCursorType(CursorGrab)
    }
  }

  const clearTool = () => {
    seatPlanWrapperRef.current.clearTool()
    setCursorType('')
  }

  const plusQuantity = () => {
    if(focusSeatTicket?.remCap ?? 0 >= ticketQuantity) {
      setTicketQuantity(ticketQuantity + 1)
    }
  }

  const minusQuantity = () => {
    if(ticketQuantity > 1) {
      setTicketQuantity(ticketQuantity - 1)
    }
  }

  
  
  return (
    <>
      <Script
        src={`/modules/viclass/ww/mfe.webrt.js`}
        onReady={() => setMfeReady(true)}
      />
      <Script
        src={`/modules/metaticket/ww/planner.wrapper.js`}
        onReady={() => setSpReady(true)}
      />
      <div className="w-full h-full">
        {!seatPlanInitiated ? (
          <Loader2 className="h-5 w-5 animate-spin" />
        ) : null}

        <div
          className={cn(
            'relative',
            cursorType
          )}
        >
          <Popover open={!!focusSeat}>
            <PopoverTrigger asChild>
              <div
                className="absolute invisible"
                style={{
                  top: focusSeatRect.top - (seatPlanViewportRef.current?.getBoundingClientRect()?.top || 0),
                  left: focusSeatRect.left + (seatPlanViewportRef.current?.getBoundingClientRect()?.left || 0),
                  width: focusSeatRect.width,
                  height: focusSeatRect.height,
                }}
              />
            </PopoverTrigger>
            <PopoverContent className="w-fit">
              {focusSeatTicket && (
                <div>
                  <div className="flex flex-col mr-4">
                    <div className="font-bold">
                      {focusSeatTicket?.dname ? focusSeatTicket.dname : `${focusSeatTicket?.priceCat} | ${focusSeatTicket?.sid}`}
                    </div>
                    <div className="text-sm">
                      Giá: <Money amount={focusSeatTicket?.basePrice} />
                    </div>
                    
                    {focusSeatTicket.remCap > 1 ? (
                      <div className="text-sm flex flex-row mt-1 items-center">
                        Số lượng:
                        <Button
                          disabled={focusSeatTicket.remCap == 1 || ticketQuantity == 1}
                          variant="primary"
                          className="uppercase w-6 h-6 p-2 ml-2"
                          onClick={() => minusQuantity()}
                        >
                          -
                        </Button>
                        
                        <div className="text-1xl font-bold ml-2 mr-2">{ticketQuantity}</div>
                        <Button
                          disabled={focusSeatTicket.remCap == 1 || ticketQuantity == focusSeatTicket.remCap}
                          variant="primary"
                          className="uppercase w-6 h-6 p-2"
                          onClick={() => plusQuantity()}
                        >
                          +
                        </Button>
                      </div>

                    ) : null}
                    
                  </div>
                  {focusSeatTicket.remCap == 0 ? (
                    <div className="font-bold text-red-500">
                      Đã hết vé
                    </div>
                  ) : null}
                  <div className="flex gap-2 w-fit min-w-[180px] mt-2">
                    <Button
                      variant="outline"
                      size="sm"
                      className="flex-1 h-8"
                      onClick={
                        () => {
                          setTicketQuantity(1)
                          onDismissFocusSeat()
                        }
                      }
                    >
                      Đóng
                    </Button>
                    {focusSeatTicket.remCap > 0 ? (
                      <Button
                        variant="primary"
                        size="sm"
                        className="flex-1 h-8"
                        onClick={
                          () => {
                            setTicketQuantity(1)
                            onSelectFocusSeat()
                          }
                        }
                      >
                        Chọn
                      </Button>
                    ) : null}
                    
                  </div>
                </div>
              )}
            </PopoverContent>
          </Popover>
          <div
            ref={seatPlanViewportRef}
            id="seatplan_viewport"
            data-zoom="3"
            className="relative w-full min-h-[65svh] sm:min-h-[60svh] md:min-h-[70vh] rounded-md board-viewport"
          />
          <div className={cn(
              "absolute top-0 left-0 z-20 flex flex-col w-1/2 md:w-1/6 p-2",
              (!session.ledgendImageUrl) && "hidden"
          )}>
            <img
              src={openLedgend ? '/assets/frontend/icons/collapse_arrow.png' : '/assets/frontend/icons/expand_arrow.png'}
              alt={'toggle ledgend'}
              className="w-8 h-8"
              onClick={() => setOpenLedgend(!openLedgend)}
            />
            
            <img
              src={session.ledgendImageUrl || ""}
              alt={'Show ledgend'}
              className={cn(
                "mt-1",
                !openLedgend && "hidden"
              )}
            />
          </div>
          <div className={cn(
              "md:flex absolute bottom-1 right-1 z-20 w-fit flex-row p-2",
              !seatPlanWrapperRef.current?.controllable() && '!hidden'
              )}>
            <img
              src="/assets/frontend/icons/hand_active.png"
              alt={'hand active'}
              className={cn(
                "w-8 h-8 mr-2 hover:cursor-pointer border border-black rounded",
                cursorType == CursorGrab ? "block" : "hidden",
              )}
              onClick={clearTool}
            />
            <img
              src="/assets/frontend/icons/hand.png"
              alt={'hand'}
              className={cn(
                "w-8 h-8 mr-2 hover:cursor-pointer border border-black rounded",
                cursorType != CursorGrab ? "block" : "hidden",
              )}
              onClick={startPan}
            />
            <img
              src="/assets/frontend/icons/zoom_out_active.png"
              alt={'zoom out active'}
              className={cn(
                "w-8 h-8 mr-2 hover:cursor-pointer border border-black rounded",
                cursorType == CursorZoomOut ? "block" : "hidden",
              )}
              onClick={clearTool}
            />
            <img
              src="/assets/frontend/icons/zoom_out.png"
              alt={'zoom out'}
              className={cn(
                "w-8 h-8 mr-2 hover:cursor-pointer border border-black rounded",
                cursorType != CursorZoomOut ? "block" : "hidden",
              )}
              onClick={zoomOut}
            />
            <img
              src="/assets/frontend/icons/zoom_in_active.png"
              alt={'zoom in active'}
              className={cn(
                "w-8 h-8 hover:cursor-pointer border border-black rounded",
                cursorType == CursorZoomIn ? "block" : "hidden",
              )}
              onClick={clearTool}
            />
            <img
              src="/assets/frontend/icons/zoom_in.png"
              alt={'zoom in'}
              className={cn(
                "w-8 h-8 hover:cursor-pointer border border-black rounded",
                cursorType != CursorZoomIn ? "block" : "hidden",
              )}
              onClick={zoomIn}
            />
          </div>
        </div>
      </div>
    </>
  )
}

export { SeatPlanContainer }
