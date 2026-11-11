import { OrderSummaryBuyerInfo, OrderSummaryCustomFields, OrderSummaryRecipientInfo, PurchaseInfo } from '@/data/order'
import VIETNAM_PROVINCES from '@/data/order/vietnam-provinces.json'
import { DataLabel } from './utils/DataLabel'

const OrderSummaryInfoOverview = ({
  buyerInfo,
  recipientInfo,
  customFields
}: {
  buyerInfo?: OrderSummaryBuyerInfo,
  recipientInfo?: OrderSummaryRecipientInfo | null,
  customFields?: OrderSummaryCustomFields | null
}) => {
  if (!buyerInfo) {
    return null
  }

  return (
    <>
      <div className="mb-5">
        <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
          Thông tin mua vé
        </h2>
        <div className="flex flex-wrap">
          <DataLabel className="basis-1/2 mb-5" label="Họ tên" value={buyerInfo.name} />
          <DataLabel
            className="basis-1/2 mb-5"
            label="Số điện thoại"
            value={buyerInfo.phone}
          />
          <DataLabel className="basis-1/2 mb-5" label="Email" value={buyerInfo.email} />
        </div>
      </div>
      {recipientInfo && (
        <div className="mb-5">
          <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
            Thông tin nhận vé
          </h2>
          <div className="flex flex-wrap">
            <DataLabel
              className="basis-1/2 mb-5"
              label="Họ tên"
              value={recipientInfo.name}
            />
            <DataLabel
              className="basis-1/2 mb-5"
              label="Số điện thoại"
              value={recipientInfo.phone}
            />
            <DataLabel
              className="mb-5"
              label="Địa chỉ"
              value={recipientInfo.address}
              // value={[address, wardName, districtName, provinceDisplayName].join(
              //   ' - '
              // )}
            />
          </div>
        </div>
      )}
      {(customFields?.length ?? 0) > 0 && (
        <div className="mb-5">
          <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
            Thông tin khác
          </h2>
          
          <div className="flex flex-wrap">
            {customFields?.map((field, index) => (
              <DataLabel
                key={index}
                className="basis-1/2 mb-5 w-full"
                label={field.name}
                value={field.value}
              />
            ))}
            
          </div>
        </div>
      )}
      
    </>
  )
}

export { OrderSummaryInfoOverview }
