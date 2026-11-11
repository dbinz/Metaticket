export interface CreateOrderRequest {
  reservationId: string;
  name: string;
  phone: string;
  email: string;
  delivery?: OrderDeliveryRequest;
  customFields: CustomFieldRequest[];
  promotionCode: string;
}

export interface OrderDeliveryRequest {
  deliveryName: string;
  deliveryPhone: string;
  provinceName: string;
  districtName: string;
  wardName: string;
  address: string;
}

export interface CustomFieldRequest {
  id: string;
  name: string;
  value: string;
}
