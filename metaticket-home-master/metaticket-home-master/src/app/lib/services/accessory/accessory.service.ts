import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { apiUrl } from 'src/app/lib/constants';
import {
  AccessoryCheckoutRequest,
  AccessoryCheckoutResponse,
  AccessoryOrderResponse,
  AccessoryResponse,
  AccessoryShowListPage,
  AccessoryShowResponse,
  CustomerAccessoryOrderItem,
  PendingAccessoryOrderLookup,
  PromotionOfferResponse,
  ValidateDiscountRequest,
  ValidateDiscountResponse,
} from 'src/app/lib/entities';
import { HttpClientService } from '../http-client/http-client.service';

/**
 * Data service for the accessory storefront ("Sản phẩm").
 *
 * Catalog + show detail (incl. sessions / fulfillment / pickup points) hit real
 * `service.accessory` endpoints through nginx (`/api/public/accessory/*`).
 * Pickup points come from `getAccessoryShow(...).fulfillment.pickupPoints` — no separate mock list.
 */
@Injectable({ providedIn: 'root' })
export class AccessoryService {
  constructor(private _httpClientService: HttpClientService) {}

  /** Paginated storefront listing (`page` is 0-based; `size` is the load-more page size). */
  getAccessoryShows(page = 0, size = 6): Observable<AccessoryShowListPage> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this._httpClientService.get<AccessoryShowListPage>(apiUrl.getAccessoryShows, params);
  }

  /** Pinned accessory shows in pinOrder — `/san-pham` banner slider. */
  getAccessoryShowBanner(): Observable<AccessoryShowResponse[]> {
    return this._httpClientService.get<AccessoryShowResponse[]>(apiUrl.getAccessoryShowBanner);
  }

  /** Keyword search (0-based `page`), ranked by match quality — powers the "Sản phẩm" search column. */
  searchAccessoryShows(keyword: string, page = 0, size = 6): Observable<AccessoryShowListPage> {
    const params = new HttpParams().set('keyword', keyword).set('page', String(page)).set('size', String(size));
    return this._httpClientService.get<AccessoryShowListPage>(apiUrl.searchAccessoryShows, params);
  }

  /**
   * Fetch a single open accessory show together with its active accessories
   * (including each accessory's variants) and fulfillment config, keyed by the show's encoded id.
   */
  getAccessoryShow(showId: string): Observable<AccessoryShowResponse> {
    const params = new HttpParams().set('id', showId);
    return this._httpClientService.get<AccessoryShowResponse>(apiUrl.getAccessoryShow, params);
  }

  /** Resolve an Open accessory show by Canonical_Slug (`/san-pham/{slug}`). */
  getAccessoryShowBySlug(slug: string): Observable<AccessoryShowResponse> {
    const params = new HttpParams().set('slug', slug);
    return this._httpClientService.get<AccessoryShowResponse>(apiUrl.getAccessoryShowBySlug, params);
  }

  /**
   * Fetch a single active accessory with its current stock (top-level for single-SKU, per-variant
   * for variant products), keyed by the accessory's encoded id. Backs the product-detail popup's
   * live-stock refresh/poll so sold-out products/variants are reflected before add-to-cart.
   */
  getAccessory(accessoryId: string): Observable<AccessoryResponse> {
    const params = new HttpParams().set('id', accessoryId);
    return this._httpClientService.get<AccessoryResponse>(apiUrl.getAccessory, params);
  }

  /**
   * Preview a discount code against the submitted cart lines (Req 4.1). Returns
   * the resolved discount amount and resulting total; no usage count is mutated.
   */
  validateDiscount(body: ValidateDiscountRequest): Observable<ValidateDiscountResponse> {
    return this._httpClientService.post<ValidateDiscountResponse>(apiUrl.validateDiscount, body);
  }

  /**
   * Public discount codes of an on-sale accessory show (needs login). `showId` is the encoded public id held in
   * `Cart.accessoryShowId`, passed through untouched: the backend decodes it and rejects raw ObjectIds.
   */
  getDiscountOffers(showId: string): Observable<PromotionOfferResponse[]> {
    const params = new HttpParams().set('showId', showId);
    return this._httpClientService.get<PromotionOfferResponse[]>(apiUrl.getAccessoryDiscountOffers, params);
  }

  /**
   * Submit the whole cart to create an order and its payment (Req 6.1, 7.3).
   * Returns the encoded payment/order ids plus the created Order_Code and total.
   */
  checkout(body: AccessoryCheckoutRequest): Observable<AccessoryCheckoutResponse> {
    return this._httpClientService.post<AccessoryCheckoutResponse>(apiUrl.accessoryCheckout, body);
  }

  /**
   * Fetch a completed order by its encoded id, powering the "Hoàn tất" review
   * link (Req 13.3).
   */
  getAccessoryOrder(encodedOrderId: string): Observable<AccessoryOrderResponse> {
    const params = new HttpParams().set('id', encodedOrderId);
    return this._httpClientService.get<AccessoryOrderResponse>(apiUrl.getAccessoryOrder, params);
  }

  /**
   * List PaymentSucceed accessory orders for the logged-in customer ("Quản lý đơn hàng").
   */
  getMyAccessoryOrders(): Observable<CustomerAccessoryOrderItem[]> {
    return this._httpClientService.get<CustomerAccessoryOrderItem[]>(apiUrl.getMyAccessoryOrders);
  }

  /**
   * Newest unpaid hold for the logged-in customer (`Created` / `WaitingForPayment`).
   * Requires auth gateway `X-Customer-User-Id`. Body is `{ order: null | PendingAccessoryOrder }`.
   */
  getPendingAccessoryOrder(): Observable<PendingAccessoryOrderLookup> {
    return this._httpClientService.get<PendingAccessoryOrderLookup>(apiUrl.getPendingAccessoryOrder);
  }

  /**
   * Cancel the customer's own pre-payment order (the "Hủy đơn" action on the payment step).
   * Releases the reserved stock + discount server-side. Rejects with 409 if the order is past the
   * cancellable window (payment already in-flight or terminal).
   */
  cancelAccessoryOrder(encodedOrderId: string): Observable<void> {
    const params = new HttpParams().set('id', encodedOrderId);
    return this._httpClientService.post<void>(apiUrl.cancelAccessoryOrder, null, params);
  }
}
