import { CustomFieldDataResponse } from '../../@core/metaticket/model';

export interface TicketBookingDataSource {
    // booking ID
    id: string;

    // ticket info
    sid: string;
    quantity: number;

    // customer info
    customerName: string;
    customerPhone: string;
    customerEmail: string;

    // delivery info (all optional)
    deliveryName?: string | null;
    deliveryPhone?: string | null;
    provinceName?: string | null;
    districtName?: string | null;
    wardName?: string | null;
    address?: string | null;

    // custom fields (optional list)
    customFields?: CustomFieldDataResponse[] | null;

    // order info (optional fields)
    promotionCode?: string | null;
    reservationStatus?: string | null;
    orderStatus?: string | null;
    paymentStatus?: string | null;
    reservationId?: string | null;
    orderId?: string | null;
    paymentId?: string | null;

    // Metadata
    version: number;
    lastUpdated: number;
    createdAt: number;
}
