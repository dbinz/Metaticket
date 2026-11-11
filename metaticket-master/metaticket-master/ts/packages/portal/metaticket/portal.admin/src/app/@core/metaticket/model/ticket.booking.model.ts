import { CustomFieldDataResponse } from './order.model';

export interface TicketBookingResponse {
    // booking ID
    id: string;
    showId: string;
    sessionId: string;

    // ticket info
    ticketId: string;
    capacityId?: string | null;
    priceCat?: string | null;
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
    reservationId?: string | null;
    orderId?: string | null;
    paymentId?: string | null;

    // Metadata
    version: number;
    lastUpdated: number;
    createdAt: number;
}

// Interface for potential success response (adjust based on actual backend structure)
export interface BookingUploadSuccessResponse {
    message: string;
    bookingIds?: string[]; // For 200 OK
    successfulBookingIds?: string[]; // For 207 Multi-Status
    failedBookings?: { [key: string]: string }; // For 207 Multi-Status
}

// Interface for potential error response (adjust based on actual backend structure)
export interface BookingUploadErrorResponse {
    message: string;
    details?: string[] | string;
    errors?: string[] | string;
}
