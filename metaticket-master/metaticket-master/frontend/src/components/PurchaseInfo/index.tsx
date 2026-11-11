import deepEqual from 'fast-deep-equal'
import * as StepLayout from '../StepLayout'
import { Button } from '@/components/ui/button'
import { DefaultStepProps } from '../BookingSteps/types'
import { PurchaseSummary } from '../PurchaseSummary'
import { useStepDataContext } from '../BookingSteps/StepDataContext'
import { useToast } from '@/components/ui/use-toast'
import { Loader2, ArrowRight, ArrowLeft } from 'lucide-react'
import {
  OrderCustomFieldBooleanConstraints,
  OrderCustomFieldDateConstraints,
  OrderCustomFieldNumberConstraints,
  OrderCustomFieldStringConstraints,
  OrderCustomFieldType,
  OrderCustomFieldTypeListConstraints,
  OrderCustomFields,
  OrderStatus,
  PurchaseInfo as PurchaseInfoFormSchema,
  useCreateOrderMutation,
  useUpdateOrderMutation,
} from '@/data/order'
import { PurchaseInfoFormFields } from './PurchaseInfoFormFields'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Form } from '@/components/ui/form'
import {
  useReleaseReservationMutation,
  useReservationQueryOnce,
  isReservationExpired,
} from '@/data/ticket'
import { useStepNavContext } from '../BookingSteps/StepNavContext'
import { useContext, useEffect, useRef, useState } from 'react'
import VIETNAM_PROVINCES from '@/data/order/vietnam-provinces.json'
import PromotionDetailsContext from '../BookingSteps/PromotionDetailsContext'
import moment from 'moment'
import { useShowContext } from '../ShowContext'

const PurchaseInfo = ({ step, onBack, onSubmit }: DefaultStepProps) => {
  const { session } = useShowContext()
  const { toast } = useToast()
  const { resetStep } = useStepNavContext()
  const {
    seatSelectionState,
    seatSelectionDispatch,
    purchaseInfoState,
    purchaseInfoDispatch,
    resetBookingState,
  } = useStepDataContext()
  const { orderId: persistedOrderId, purchaseInfo: persistedPurchaseInfo } =
    purchaseInfoState

  // check if reservation is still active (status is Created or Open)
  // if not, reset step and booking state
  const { data: reservationData } = useReservationQueryOnce(
    seatSelectionState.reservationId
  )
  const isBookingExpired =
    reservationData && isReservationExpired(reservationData)
  const {promotionCode} = useContext(PromotionDetailsContext)
  const [canSubmitOrder, setCanSubmitOrder] = useState(false)
  
  useEffect(() => {
    if (isBookingExpired) {
      resetStep()
      resetBookingState()
    }
  }, [isBookingExpired])

  // Purchase Info form
  // prefill by persisted data if available
  const form = useForm<PurchaseInfoFormSchema>({
    resolver: zodResolver(PurchaseInfoFormSchema),
    defaultValues: persistedPurchaseInfo,
  })

  // form submission - begin
  const createOrderMutation = useCreateOrderMutation()
  const createOrder = (
    formData: PurchaseInfoFormSchema,
    reservationId: string
  ) => {
    if(formData.delivery) {
      formData.delivery.provinceName = VIETNAM_PROVINCES.find(
        (item) => item.code === formData.delivery!!.provinceName
      )?.name ?? ""
    }

    if(formData.customFields) {
      for(var i = 0; i < formData.customFields?.length; i ++) {
        formData.customFields[i].error = undefined
        formData.customFields[i].constraints = undefined
        formData.customFields[i].name = formData.customFields[i].displayName
        formData.customFields[i].displayName = undefined
        formData.customFields[i].note = undefined
        formData.customFields[i].type = undefined
      }
    }
    
    return createOrderMutation.mutate(
      {
        ...formData,
        reservationId,
        promotionCode,
      },
      {
        onError: (err) => {
          console.error(err)
          toast({
            variant: 'destructive',
            title: 'Có lỗi xảy ra',
            description: err.message,
          })
        },
        onSuccess: (data) => {
          purchaseInfoDispatch({
            type: 'SET_PURCHASE_INFO',
            payload: formData,
          })
          purchaseInfoDispatch({ type: 'SET_ORDER_ID', payload: data.id })
          var numberOfStep = 1;
          if(session.paymentOffline || data.status == OrderStatus.PaymentSucceed) {
            numberOfStep = 2;
          }
          onSubmit(numberOfStep)
        },
      }
    )
  }

  const updateOrderMutation = useUpdateOrderMutation()
  const updateOrder = (
    formData: PurchaseInfoFormSchema,
    orderId: string,
    persistedPurchaseInfo: PurchaseInfoFormSchema
  ) => {
    if (deepEqual(formData, persistedPurchaseInfo)) {
      return onSubmit()
    }

    return updateOrderMutation.mutate(
      {
        orderId,
        ...formData,
      },
      {
        onError: (err: Error) => {
          console.error(err)
          toast({
            variant: 'destructive',
            title: 'Error creating order',
            description: 'Something went wrong. Please try again later.',
          })
        },
        onSuccess: () => {
          purchaseInfoDispatch({
            type: 'SET_PURCHASE_INFO',
            payload: formData,
          })
          var numberOfStep = 1;
          if(session.paymentOffline) {
            numberOfStep = 2;
          }
          onSubmit(numberOfStep)
        },
      }
    )
  }

  const onFormSubmit = (formData: PurchaseInfoFormSchema) => {
    
    if(formData.customFields != null) {
      var customFields = formData.customFields
      var errorMessage = ""
      for(var i = 0; i < customFields.length; i ++) {
        var error = false
        switch(customFields[i].type) {
          case OrderCustomFieldType.String:
            if(customFields[i].constraints) {
              var stringConstraints = OrderCustomFieldStringConstraints.parse(JSON.parse(customFields[i].constraints!!));
              if(stringConstraints.require && (!customFields[i].value || !customFields[i].value.trim())) {
                errorMessage = 'Bạn chưa nhập ' + customFields[i].displayName;
                error = true
              } else {
                if(stringConstraints.regex && customFields[i].value?.trim()?.length) {
                  var regex = new RegExp(stringConstraints.regex);
                  if(!regex.test(customFields[i].value)) {
                    errorMessage = customFields[i].displayName + ' không đúng định dạng';
                    error = true
                  }
                }
                if(stringConstraints.minLength && ((customFields[i].value?.trim()?.length ?? 0) < stringConstraints.minLength)) {
                  errorMessage = customFields[i].displayName + ' dài tối thiểu ' + stringConstraints.minLength + " ký tự";
                  error = true
                }
                if(stringConstraints.maxLength && ((customFields[i].value?.trim()?.length ?? 0) > stringConstraints.maxLength)) {
                  errorMessage = customFields[i].displayName + ' dài tối đa ' + stringConstraints.maxLength + " ký tự";
                  error = true
                }
              }
            }
            break;
          case OrderCustomFieldType.Number:
            if(customFields[i].constraints) {
              var numberConstraints = OrderCustomFieldNumberConstraints.parse(JSON.parse(customFields[i].constraints!!));
              if(numberConstraints.require && (!customFields[i].value || !customFields[i].value.trim())) {
                errorMessage = 'Bạn chưa nhập ' + customFields[i].displayName;
                error = true
              } else {
                if(numberConstraints.min && ((customFields[i].value?.trim()?.length ?? 0) < numberConstraints.min)) {
                  errorMessage = customFields[i].displayName + ' nhỏ hơn ' + numberConstraints.min;
                  error = true
                }
                if(numberConstraints.max && ((customFields[i].value?.trim()?.length ?? 0) > numberConstraints.max)) {
                  errorMessage = customFields[i].displayName + ' lớn hơn ' + numberConstraints.max;
                  error = true
                }
              }
            }
            break;
          case OrderCustomFieldType.Date:
            if(customFields[i].constraints) {
              var dateConstraints = OrderCustomFieldDateConstraints.parse(JSON.parse(customFields[i].constraints!!));
              if(dateConstraints.require && (!customFields[i].value || !customFields[i].value.trim())) {
                errorMessage = 'Bạn chưa chọn ' + customFields[i].displayName;
                error = true
              } else {
                if(dateConstraints.minDate && customFields[i].value) {
                  if(moment(dateConstraints.minDate, "DD/MM/YYYY").isAfter(moment(customFields[i].value, "DD/MM/yyyy"))) {
                    errorMessage = customFields[i].displayName + ' phải sau ' + moment(dateConstraints.minDate, "DD/MM/YYYY").format('DD/MM/yyyy');
                    error = true
                  }
                }
                if(dateConstraints.maxDate && customFields[i].value) {
                  if(moment(dateConstraints.maxDate, "DD/MM/YYYY").isBefore(moment(customFields[i].value, "DD/MM/YYYY"))) {
                    errorMessage = customFields[i].displayName + ' phải sau ' + moment(dateConstraints.maxDate, "DD/MM/YYYY").format('DD/MM/yyyy');
                    error = true
                  }
                }
              }
            }
            break;
          case OrderCustomFieldType.List:
            if(customFields[i].constraints) {
              var listConstraints = OrderCustomFieldTypeListConstraints.parse(JSON.parse(customFields[i].constraints!!));
              if(listConstraints.require && (!customFields[i].value || !customFields[i].value.trim())) {
                errorMessage = 'Bạn chưa chọn ' + customFields[i].displayName;
                error = true
              }
            }
            break;
          case OrderCustomFieldType.Boolean:
            if(customFields[i].constraints) {
              var booleanConstraints = OrderCustomFieldBooleanConstraints.parse(JSON.parse(customFields[i].constraints!!));
              if(booleanConstraints.require && (!customFields[i].value || !customFields[i].value.trim() || customFields[i].value == "false")) {
                errorMessage = 'Bạn chưa chọn ' + customFields[i].displayName;
                error = true
              }
            }
            break;
        }
        customFields[i].error = error
      }

      form.setValue("customFields", customFields)
      if(errorMessage.trim()) {
        toast({
          variant: 'destructive',
          title: errorMessage,
        })
        return
      }
    }


    if (seatSelectionState.reservation?.id) {
      if (persistedOrderId && persistedPurchaseInfo) {
        updateOrder(formData, persistedOrderId, persistedPurchaseInfo)
      } else {
        createOrder(formData, seatSelectionState.reservation.id)
      }
    } else {
      console.error('Reservation not found')
      toast({
        variant: 'destructive',
        title: 'Reservation not found',
        description: 'Something went wrong. Please try again later.',
      })
    }
  }
  // form submission - end

  const { mutate: releaseReservation, isLoading: isReleaseReservationLoading } =
    useReleaseReservationMutation()
  const onReselectSeats = () => {
    // call API to release reservation
    if (seatSelectionState.reservation?.id) {
      releaseReservation(seatSelectionState.reservation?.id, {
        onError: (err) => {
          console.error(err)
          toast({
            variant: 'destructive',
            title: 'Error releasing reservation',
            description: 'Something went wrong. Please try again later.',
          })
        },
      })
    }

    // TODO: might need to update this to clear reservation/payment as well?
    seatSelectionDispatch({ type: 'RESET_SEAT_SELECTION' })
    onBack()
  }


  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onFormSubmit)}>
      {/* <form> */}
        <StepLayout.Wrapper>
          <StepLayout.Main>
            <PurchaseInfoFormFields form={form} setCanSubmitOrder={setCanSubmitOrder}/>

          </StepLayout.Main>
          <StepLayout.Sidebar>
            <PurchaseSummary
              selectedTickets={seatSelectionState.selectedTickets}
              editMode={false}
              showQuantity
            />

            <div className="flex gap-2 my-2 flex-row-reverse">
              <Button
                className="basis-1/2 uppercase font-bold"
                variant="primary"
                type="submit"
                // type="button"
                disabled={!canSubmitOrder}
              >
                {createOrderMutation.isLoading ||
                updateOrderMutation.isLoading ? (
                  <Loader2 className="h-5 w-5 animate-spin" />
                ) : (
                  <>
                    <span className="flex-1">Tiếp tục</span>
                    <ArrowRight size={20} />
                  </>
                )}
              </Button>
              <Button
                onClick={onReselectSeats}
                variant="secondary"
                className="basis-1/2 uppercase font-bold"
              >
                {isReleaseReservationLoading ? (
                  <Loader2 className="h-5 w-5 animate-spin" />
                ) : (
                  <>
                    <ArrowLeft size={20} />
                    <span className="flex-1">Chọn lại vé*</span>
                  </>
                )}
              </Button>

              
            </div>
            <div className="italic text-sm">
              *Nếu Chọn lại vé, toàn bộ thông tin vé đã chọn sẽ không được lưu.
            </div>
          </StepLayout.Sidebar>
        </StepLayout.Wrapper>
      </form>
    </Form>
  )
}

export { PurchaseInfo }
