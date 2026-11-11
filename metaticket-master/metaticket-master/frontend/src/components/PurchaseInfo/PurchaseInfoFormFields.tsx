import { UseFormReturn, useFieldArray, useForm } from 'react-hook-form'
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import VIETNAM_PROVINCES from '@/data/order/vietnam-provinces.json'
import { OrderCustomField, OrderCustomFieldDataCollectorConstraints, OrderCustomFieldResponse, OrderCustomFieldStringConstraints, OrderCustomFieldType, OrderCustomFields, PurchaseInfo, useOrderCustomFieldMutation } from '@/data/order'
import { useShowContext } from '../ShowContext'
import { Fragment, useEffect, useState } from 'react'
import { useStepDataContext } from '../BookingSteps/StepDataContext'
import CustomField from './components/CustomField'

const sortedProvinces = VIETNAM_PROVINCES.sort((a, b) =>
  a.name.localeCompare(b.name)
)

const PurchaseInfoFormFields = ({
  form,
  setCanSubmitOrder
}: {
  form: UseFormReturn<PurchaseInfo>,
  setCanSubmitOrder: (canSubmit: boolean) => void
}) => {
  const { mutate: getOrderCustomField } = useOrderCustomFieldMutation()

  const { session, show } = useShowContext()
  const { fields, update, append, remove } = useFieldArray({
    control: form.control, // control props comes from useForm (optional: if you are using FormContext)
    name: "customFields", // unique name for your Field Array,
    keyName: "key"
  });
  const { purchaseInfoState, purchaseInfoDispatch } = useStepDataContext()
  const { purchaseInfo: persistedPurchaseInfo } = purchaseInfoState
  const [dataCollectorValidIds, setDataCollectorValidIds] = useState<string[]>([])
  const [totalRequireDataCollectorIds, setTotalRequireDataCollectorIds] = useState<string[]>([])
  const [loadingCustomFields, setLoadingCustomFields] = useState(true)
  

  useEffect(() => {
    if(session && show) {
      getOrderCustomField({sessionId: session.id, showId: show.id}, {
        onError: (err) => {
          console.error(err)
        },
        onSuccess: (data) => {
          remove()
          var customFields: OrderCustomField[] = [];
          for(var i = 0; i < data.length; i ++) {
            customFields.push({
              id: data[i].id,
              displayName: data[i].displayName,
              value: "",
              type: data[i].type,
              error: false,
              constraints: data[i].constraints,
              note: data[i].note
            })
          }
          append(customFields, {shouldFocus: false})
          var dataCollectorsRequire = [];
          for(var i = 0; i < customFields.length; i ++) {
            if(customFields[i].type == OrderCustomFieldType.DataCollector) {
              if(customFields[i].constraints) {
                var constraints = OrderCustomFieldDataCollectorConstraints.parse(JSON.parse(customFields[i].constraints!!))
                if(constraints.require) {
                  dataCollectorsRequire.push(customFields[i].id);
                }
              }
              
            }
          }
          setLoadingCustomFields(false)
          setTotalRequireDataCollectorIds(dataCollectorsRequire)

          // var customFields = []
          // for(var i = 0; i < data.orderCustomFieldOptional.length; i ++) {
          //   var value = ""
            
          //   if(persistedPurchaseInfo?.customFields != null) {
          //     for(var j = 0; j < persistedPurchaseInfo.customFields.length; j ++) {
          //       if(persistedPurchaseInfo?.customFields[j].id == data.orderCustomFieldOptional[i].id) {
          //         value = persistedPurchaseInfo?.customFields[j].value ?? ""
          //       }
          //     }
          //   }
          //   customFields.push({
          //     id: data.orderCustomFieldOptional[i].id,
          //     displayName: data.orderCustomFieldOptional[i].name,
          //     value: value,
          //     type: data.orderCustomFieldOptional[i].type,
          //     require: false,
          //     error: false
          //   })
          // }
          // for(var i = 0; i < data.orderCustomFieldRequired.length; i ++) {
          //   var value = ""
          //   if(persistedPurchaseInfo?.customFields != null) {
          //     for(var j = 0; j < persistedPurchaseInfo.customFields.length; j ++) {
          //       if(persistedPurchaseInfo?.customFields[j].id == data.orderCustomFieldRequired[i].id) {
          //         value = persistedPurchaseInfo?.customFields[j].value ?? ""
          //       }
          //     }
          //   }
          //   customFields.push({
          //     id: data.orderCustomFieldRequired[i].id,
          //     name: data.orderCustomFieldRequired[i].name,
          //     value: value,
          //     type: data.orderCustomFieldRequired[i].type,
          //     required: true,
          //     error: false
          //   })
          // }
        
        },
      
      })
      
      
    }
  }, [session])

  useEffect(() => {
    if(!loadingCustomFields) {
      setCanSubmitOrder(dataCollectorValidIds.length == totalRequireDataCollectorIds.length);
    }
  }, [dataCollectorValidIds, loadingCustomFields]);

  const isAllCustomFieldValid = () => {

  }

  const setCustomFieldValue = (position: number, newValue: string) => {
    // var isValid = true;
    // var errorMessage = "";
    // switch(fields[position].type) {
    //   case OrderCustomFieldType.String:
    //     if(fields[position].constraints) {
    //       var constraints = OrderCustomFieldStringConstraints.parse(fields[position].constraints);

    //       if(constraints.regex) {
    //           var regex = new RegExp(constraints.regex);
    //           if(!regex.test(newValue)) {
    //             errorMessage = fields[position].displayName + ' không đúng định dạng';
    //             isValid = false
    //           }
    //       }
    //     }
    //     break;
    // }
    
    update(position, {...fields[position], value: newValue})
  }

  const plusDataCollectorValid = (id: string) => {
    var exists = false;
    for(var i = 0; i < dataCollectorValidIds.length; i ++) {
      if(dataCollectorValidIds[i] == id) {
        exists = true;
        break;
      }
    }
    if(!exists) {
      var arr = dataCollectorValidIds.slice();
      arr.push(id);
      setDataCollectorValidIds(arr);
    }
  }

  return (
    <>
      <div className="mb-5">
        <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
          Thông tin mua vé
        </h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <FormField
            control={form.control}
            name="name"
            render={({ field, fieldState }) => (
              <FormItem>
                <FormLabel>
                  Họ tên (<span className="text-destructive">*</span>)
                </FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    hasError={!!fieldState.error}
                    type="text"
                    className="basis-1/2"
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="phone"
            render={({ field, fieldState }) => (
              <FormItem>
                <FormLabel>
                  Số điện thoại (<span className="text-destructive">*</span>)
                </FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    hasError={!!fieldState.error}
                    type="tel"
                    className="basis-1/2"
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="email"
            render={({ field, fieldState }) => (
              <FormItem>
                <FormLabel>
                  Email (<span className="text-destructive">*</span>)
                </FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    hasError={!!fieldState.error}
                    type="email"
                    className="basis-1/2"
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      </div>
      {session.requireDelivery && (
        <div className="mb-5">
          <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
            Thông tin nhận vé
          </h2>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField
              control={form.control}
              name="delivery.deliveryName"
              render={({ field, fieldState }) => (
                <FormItem>
                  <FormLabel>
                    Họ tên (<span className="text-destructive">*</span>)
                  </FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      hasError={!!fieldState.error}
                      type="text"
                      className="basis-1/2"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="delivery.deliveryPhone"
              render={({ field, fieldState }) => (
                <FormItem>
                  <FormLabel>
                    Số điện thoại (<span className="text-destructive">*</span>)
                  </FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      hasError={!!fieldState.error}
                      type="tel"
                      className="basis-1/2"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="delivery.provinceName"
              render={({ field, fieldState }) => (
                <FormItem>
                  <FormLabel>
                    Tỉnh/ Thành phố (<span className="text-destructive">*</span>)
                  </FormLabel>
                  <FormControl>
                    <Select
                      onValueChange={field.onChange}
                      defaultValue={field.value}
                    >
                      <SelectTrigger
                        className={!!fieldState.error ? 'border-destructive' : ''}
                      >
                        <SelectValue placeholder="Chọn tỉnh thành" />
                      </SelectTrigger>
                      <SelectContent>
                        {sortedProvinces.map((option) => (
                          <SelectItem key={option.code} value={option.code}>
                            {option.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="delivery.districtName"
              render={({ field, fieldState }) => (
                <FormItem>
                  <FormLabel>
                    Quận/ Huyện (<span className="text-destructive">*</span>)
                  </FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      hasError={!!fieldState.error}
                      type="text"
                      className="basis-1/2"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="delivery.wardName"
              render={({ field, fieldState }) => (
                <FormItem>
                  <FormLabel>
                    Phường/ Xã (<span className="text-destructive">*</span>)
                  </FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      hasError={!!fieldState.error}
                      type="text"
                      className="basis-1/2"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="delivery.address"
              render={({ field, fieldState }) => (
                <FormItem>
                  <FormLabel>
                    Tên đường, số nhà (<span className="text-destructive">*</span>)
                  </FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      hasError={!!fieldState.error}
                      type="text"
                      className="basis-1/2"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </div>
      )}
      
      {fields.length > 0 ? (
        <div className="mb-5">
          <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
            Thông tin khác
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {fields.map((field, index) => (
              <CustomField key={index} fieldInfo={field} setValue={(value) => setCustomFieldValue(index, value)} onDataCollectorSucceed={(id: string) => plusDataCollectorValid(id)}/>
            ))}
            {/* {fields.map((value, index) => {
              return (
                <FormItem key={index}>
                  <FormLabel>
                    {value.displayName} {value.require && (<span className="text-destructive">*</span>)}
                  </FormLabel>
                  <FormControl>
                    <Input
                      onChange={(event) => {
                        update(index, {...value, value: event.target.value})
                      }}
                      value={value.value}
                      hasError={value.error}
                      type="text"
                      className="basic-1/2"
                    />
                  </FormControl>
                </FormItem>
              )
            })} */}
          </div>
          
        </div>
      ) : null}
      {(session.orderNote != null && session.orderNote.length) ? (
        <div className="mb-5">
          <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
            Lưu ý
          </h2>
          <div
            className="mt-2 italic text-sm" dangerouslySetInnerHTML={{__html: session.orderNote}}
          >
          </div>
        </div>
      ) : null}
    </>
  )
}

export { PurchaseInfoFormFields }
