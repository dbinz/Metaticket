'use client'

import { useContext, useEffect, useRef, useState } from 'react'
import * as StepLayout from '../StepLayout'
import { PurchaseSummary } from '../PurchaseSummary'
import { Loader2, ArrowRight } from 'lucide-react'
import { Button } from '../ui/button'
import {
  TicketDocument,
  useDefaultTicketMutation,
  useDefaultTicketsQuery,
  useIncrementalTicketsMutation,
  useIncrementalTicketsQuery,
  useReservationQueryPolling,
  useCreateReservationMutation,
  useStartReservationMutation,
  isReservationSuccess,
  isReservationPending,
  TicketDocuments,
} from '@/data/ticket'
import { useToast } from '@/components/ui/use-toast'
import { Money } from '../utils/Money'

import { SeatPlanContainer } from './SeatPlanContainer'
import { useShowContext } from '../ShowContext'
import { PageLoader } from '../utils/PageLoader'
import { TicketStore } from '@/data/ticket/store'
import { useStepDataContext } from '../BookingSteps/StepDataContext'
import { AxiosError } from 'axios'
import { ReservationFailedDialog } from '../utils/MessageDialogs'
import { TotalPrice } from '../PurchaseSummary/TotalPrice'
import { cn } from '@/lib/utils'
import { useMediaQuery } from 'react-responsive'
import { RESERVATION_REFETCH_INTERVAL, TICKET_REFETCH_INTERVAL } from '@/data/constants'
import PromotionDetailsContext from '../BookingSteps/PromotionDetailsContext'

const SeatSelection = ({ onSubmit }: { onSubmit: () => void }) => {
  const { show, session } = useShowContext()
  const { toast } = useToast()
  const isMobile = useMediaQuery({
    query: '(max-width: 680px)'
  })


  const { seatSelectionState, seatSelectionDispatch, purchaseInfoState, purchaseInfoDispatch } = useStepDataContext()
  const {
    selectedTickets: persistedSelectedTickets,
    reservationId: persistedReservationId,
    currentTime: persistedCurrentTime
  } = seatSelectionState

  // Load tickets
  const defaultTicketMutation = useDefaultTicketMutation()
  // const { isLoading, isError } = useDefaultTicketsQuery({
  //   showId: show.id,
  //   sessionId: session.id,
  // })
  const [isError, setIsError] = useState(false)
  const [isLoading, setIsLoading] = useState(true)

  // const { data: updatedTickets = [] } = useIncrementalTicketsQuery({
  //   showId: show.id,
  //   sessionId: session.id,
  // })
  const incrementalTicketsMutation = useIncrementalTicketsMutation()

  const tickets = TicketStore.getTickets()
  const [updatedTickets, setUpdatedTickets] = useState<TicketDocuments>([])

  const seatTicketMap = tickets.reduce((acc, cur) => {
    acc[cur.sid] = cur
    return acc
  }, {} as Record<string, TicketDocument>)

  // selected tickets are loaded from existing context
  const [selectedTickets, setSelectedTickets] = useState<
    Array<{
      ticket: TicketDocument
      quantity: number
    }>
  >(persistedSelectedTickets)
  // End of loading tickets

  // Ticket selection events
  const onSeatSelected = (sid: string, quantity: number = 1) => {
    var arr = selectedTickets.slice()
    var isExists = false
    loop: for(var i = 0; i < arr.length; i ++) {
      if(arr[i].ticket.id == seatTicketMap[sid].id) {
        arr[i].quantity += quantity
        isExists = true
        break loop
      }
    }
    if(!isExists) {
      const newTicket = {
        ticket: seatTicketMap[sid],
        quantity: quantity,
      }
      arr.push(newTicket)
      
    }
    setSelectedTickets(arr)
    // const newTicket = {
    //   ticket: seatTicketMap[sid],
    //   quantity: quantity,
    // }
    // console.log(newTicket)
    // setSelectedTickets((selectedTickets) => [...selectedTickets, newTicket])
  }

  const onSeatRemoved = (sid: string) => {
    setSelectedTickets((tickets) => tickets.filter((t) => t.ticket.sid !== sid))
  }

  // Ticket reservation flow - create and polling status
  // Create and start reservation
  const { mutate: createReservation, isLoading: isCreateReservationLoading } =
    useCreateReservationMutation()
  const { mutate: startReservation, isLoading: isStartReservationLoading } =
    useStartReservationMutation()
  const [reservationId, setReservationId] = useState<string | undefined>(
    persistedReservationId
  )
  const [validQueueId, setValidQueueId] = useState(0)

  const [currentTime, setCurrentTime] = useState<number>(
    0
  )

  const promotionDetailsContext = useContext(PromotionDetailsContext)
  const [errorMessage, setErrorMessage] = useState("")
  const [errorButtonText, setErrorButtonText] = useState("")
  const [isErrorDialogOpen, setIsErrorDialogOpen] = useState(false)
  const [expandModalBottomSheet, setExpandModalBottomSheet] = useState(false)


  const handleCreateReservationError = (error: any) => {
    console.error(error)
    setErrorMessage(error.response?.data?.message ?? "Loại vé bạn chọn đã hết. Vui lòng chọn lại vé!")
    setErrorButtonText("Chọn lại vé")
    setIsErrorDialogOpen(true)
    promotionDetailsContext.setPromotionCode("")
    promotionDetailsContext.setPromotionDetails([])
    
  }
  const closeErrorDialog = () => {
    setIsErrorDialogOpen(false)
  }

  const onReserve = () => {
    var promotionErrorPos = -1;
    for(var i = 0; i < promotionDetailsContext.promotionDetails.length; i ++) {
      if(promotionDetailsContext.promotionDetails[i].error) {
        promotionErrorPos = i;
        break;
      }
    }
    if(promotionErrorPos > -1) {
      setErrorMessage(promotionDetailsContext.promotionCode + " - " + (promotionDetailsContext.promotionDetails[promotionErrorPos].errorMessage ?? "mã đã được sử dụng hết"))
      setErrorButtonText("Đóng")
      setIsErrorDialogOpen(true)
      return;
    }
    
    createReservation(
      {
        showId: show.id,
        sessionId: session.id,
        details: selectedTickets.map(({ ticket, quantity }) => ({
          ticketId: ticket.id,
          quantity: quantity,
          priceCat: ticket.priceCat,
        })),
      },
      {
        onError: handleCreateReservationError,
        onSuccess: ({ reservationId }) => {
          setReservationId(reservationId)
          // store this on context
          seatSelectionDispatch({
            type: 'SET_SELECTED_TICKETS',
            payload: selectedTickets,
          })
          seatSelectionDispatch({
            type: 'SET_RESERVATION_ID',
            payload: reservationId,
          })
          // start reservation
          startReservation(reservationId, {
            onError: handleCreateReservationError,
          })
        },
      }
    )
  }

  // Get reservation status
  const { data: reservationData, error: reservationError } =
    useReservationQueryPolling(reservationId)

  // handle reservation error
  useEffect(() => {
    if (reservationError) {
      if (
        reservationError instanceof AxiosError &&
        reservationError.response?.status === 404
      ) {
        // TODO: show error message
        handleCreateReservationError(reservationError)

        // reset local state
        setReservationId(undefined)
        setSelectedTickets([])

        // reset reservation state
        seatSelectionDispatch({ type: 'RESET_SEAT_SELECTION' })
      }
    }
  }, [reservationError])

  // proceed if reserved
  const isReserved = reservationData && isReservationSuccess(reservationData)
  const isReservationActive =
    reservationData && isReservationPending(reservationData)

  useEffect(() => {
    defaultTicketMutation.mutate(
      {
        showId: show.id,
        sessionId: session.id,
      },
      {
        onError: (err) => {
          console.error(err)
          setIsError(true)
          setIsLoading(false)
        },
        onSuccess: (data) => {
          setIsLoading(false)
          setCurrentTime(persistedCurrentTime ?? Date.now())
          var queueId = validQueueId + 1
          setValidQueueId(queueId)
          
          
          // fetchTicketTime()
        },
      }
    )
  }, [])

  useEffect(() => {
    processIntervalTicket(currentTime)
  }, [currentTime])


  const processIntervalTicket = (startTime: number) => {
    if(startTime) {
      setTimeout(() => {
        var timeToSave = Date.now() - 5 * 60 * 1000;
        incrementalTicketsMutation.mutate({
          showId: show.id,
          sessionId: session.id,
          startTime: startTime
        }, {
          onError: (err) => {
            processIntervalWhenFailed(startTime)
          },
          onSuccess: (data) => {
            setCurrentTime(timeToSave)
            seatSelectionDispatch({
              type: 'SET_CURRENT_TIME',
              payload: timeToSave,
            })
            setUpdatedTickets(data)
          }
        })
      }, TICKET_REFETCH_INTERVAL)
      
    }
  }

  const processIntervalWhenFailed = (startTime: number) => {
    var timeToSave = Date.now() - 5 * 60 * 1000;
    incrementalTicketsMutation.mutate({
      showId: show.id,
      sessionId: session.id,
      startTime: startTime
    }, {
      onError: (err) => {
        setTimeout(() => {
          processIntervalWhenFailed(startTime)
        }, TICKET_REFETCH_INTERVAL)
      },
      onSuccess: (data) => {
        setCurrentTime(timeToSave)
        seatSelectionDispatch({
          type: 'SET_CURRENT_TIME',
          payload: timeToSave,
        })
        setUpdatedTickets(data)
      }
    })
  }
  
  useEffect(() => {
    if (isReserved) {
      seatSelectionDispatch({
        type: 'SET_RESERVATION',
        payload: reservationData,
      })
      
      onSubmit()
    }
  }, [isReserved])
  // End of ticket reservation

  if (isError) {
    toast({
      variant: 'destructive',
      title: 'Đã có lỗi xảy ra, vui lòng thử lại sau',
    })
    return null
  }

  // TODO: handle loading state
  // when seat plan is ready but tickets are not loaded yet
  if (isLoading) {
    return <PageLoader className="h-[50vh]" />
  }

  const isReserving =
    isCreateReservationLoading ||
    isStartReservationLoading ||
    isReservationActive

  const continueButton = (
    <Button
      className="w-full font-bold uppercase"
      variant="primary"
      onClick={onReserve}
      disabled={selectedTickets.length === 0 || isReserving}
    >
      {isReserving ? (
        <Loader2 className="h-5 w-5 animate-spin" />
      ) : (
        <>
          <span className="flex-1">Tiếp tục</span>
          <ArrowRight size={20} />
        </>
      )}
    </Button>
  )

  const updateTicketQuantity = (sid: string, quantity: number) => {
    var ticketArr = selectedTickets.slice()
    for(var i = 0; i < ticketArr.length; i ++) {
      
      if(ticketArr[i].ticket.sid == sid && ticketArr[i].ticket.remCap >= quantity && quantity >= 1) {
        ticketArr[i].quantity = quantity
      }
    }
    setSelectedTickets(ticketArr)
  }


  return (
    <StepLayout.Wrapper>
      <StepLayout.Main>
        <SeatPlanContainer
          seatPlanId={session.seatPlanId}
          edType={session.edType}
          tickets={tickets}
          selectedSeats={selectedTickets.map((t) => t.ticket.sid)}
          onSeatSelected={onSeatSelected}
          onSeatRemoved={onSeatRemoved}
          updatedTickets={updatedTickets}
        />
      </StepLayout.Main>
      {!isMobile && (
        <StepLayout.Sidebar className="hidden md:block">
          <PurchaseSummary
            selectedTickets={selectedTickets}
            showQuantity={true}
            showQuantityEditButton={true}
            onSeatRemoved={onSeatRemoved}
            updateTicketQuantity={updateTicketQuantity}
          />
          {continueButton}
        </StepLayout.Sidebar>
      )}
      
      {isMobile && (
        <div className="md:hidden">
          <div className={cn(
            "transition-all delay-150 duration-300 bottom-0 left-0 w-full p-3 sm:p-5 bg-popover text-popover-foreground h-fit z-20 flex flex-col items-center"
          )}>
            <div className='w-12 h-1.5 rounded-md bg-slate-400' onClick={() => setExpandModalBottomSheet(!expandModalBottomSheet)}>
              
            </div>
            
            <div className='w-full'>
              <PurchaseSummary
                selectedTickets={selectedTickets}
                showTicket={expandModalBottomSheet}
                hideTitle
                onSeatRemoved={onSeatRemoved}
                updateTicketQuantity={updateTicketQuantity}
                showQuantity={true}
                showQuantityEditButton={true}
              />
              
            </div>
            {/* <TotalPrice selectedTickets={selectedTickets} /> */}
            {continueButton}
            
            
          </div>
        </div>
      )}
      
      
      <ReservationFailedDialog
        message={errorMessage}
        buttonText={errorButtonText}
        open={isErrorDialogOpen}
        onClose={closeErrorDialog}
      />
    </StepLayout.Wrapper>
  )
}

export { SeatSelection }
