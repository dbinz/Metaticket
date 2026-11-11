import { PurchaseInfo } from '@/data/order'
import VIETNAM_PROVINCES from '@/data/order/vietnam-provinces.json'
import { DataLabel } from './utils/DataLabel'

const PurchaseInfoOverview = ({
  purchaseInfo,
}: {
  purchaseInfo?: PurchaseInfo
}) => {
  if (!purchaseInfo) {
    return null
  }

  const {
    name,
    phone,
    email,
    delivery,
    customFields
  } = purchaseInfo

  const provinceDisplayName = VIETNAM_PROVINCES.find(
    (item) => item.code === delivery?.provinceName
  )?.name
  return (
    <>
      <div className="mb-5">
        <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
          Thông tin mua vé
        </h2>
        <div className="flex flex-wrap">
          <DataLabel className="basis-1/2 mb-5" label="Họ tên" value={name} />
          <DataLabel
            className="basis-1/2 mb-5"
            label="Số điện thoại"
            value={phone}
          />
          <DataLabel className="basis-1/2 mb-5" label="Email" value={email} />
        </div>
      </div>
      {delivery && (
        <div className="mb-5">
          <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
            Thông tin nhận vé
          </h2>
          <div className="flex flex-wrap">
            <DataLabel
              className="basis-1/2 mb-5"
              label="Họ tên"
              value={delivery.deliveryName}
            />
            <DataLabel
              className="basis-1/2 mb-5"
              label="Số điện thoại"
              value={delivery.deliveryPhone}
            />
            <DataLabel
              className="mb-5"
              label="Địa chỉ"
              value={[delivery.address, delivery.wardName, delivery.districtName, provinceDisplayName].join(
                ' - '
              )}
            />
          </div>
        </div>
      )}
      {customFields?.length ?? 0 > 0 ? (
        <div className="mb-5">
          <h2 className="text-xl font-bold mb-4 uppercase border-b-2 border-tertiary">
            Thông tin khác
          </h2>
          <div className="flex flex-wrap">
            {customFields?.map((field, index) => (
              <DataLabel
                key={index}
                className="basis-1/2 mb-5"
                label={field.name ?? ""}
                value={field.value}
              />
            ))}
          </div>
        </div>
      ) : null}
      
    </>
  )
}

export { PurchaseInfoOverview }
