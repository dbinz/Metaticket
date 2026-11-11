import { Injectable } from '@angular/core';
import { HttpClientService } from '../http-client/http-client.service';
import {
  CreateOrderRequest,
  CreateOrderResponse,
  CreatePaymentRequest,
  CustomFieldResponse,
  GenerateOdcLinkRequest,
  GetCustomFieldsRequest,
  OrderSummaryResponse,
  PromotionOfferResponse,
  TentativePriceRequest,
  TentativePriceResponse,
} from 'src/app/lib/entities';
import { Observable } from 'rxjs';
import { apiUrl } from 'src/app/lib/constants';
import { HttpParams } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class OrderService {
  constructor(private _http: HttpClientService) {}

  tentativePrice(body: TentativePriceRequest): Observable<TentativePriceResponse> {
    if (body.tickets.length === 0) {
      return new Observable(function subscribe(observer) {
        const tentativePrice: TentativePriceResponse = {
          priceTotal: 0,
          priceDiscount: 0,
          shipPrice: 0,
        };

        observer.next(tentativePrice);
        observer.complete();
      });
    }

    return this._http.postJson<TentativePriceResponse>(apiUrl.tentativePrice, body);
  }

  getCustomFields(body: GetCustomFieldsRequest): Observable<CustomFieldResponse[]> {
    return this._http.postJson<CustomFieldResponse[]>(apiUrl.getCustomFields, body);
  }

  generateOdcLink(body: GenerateOdcLinkRequest): Observable<string> {
    return this._http.postAsText(apiUrl.generateOdcLink, body);
  }

  validateOdcLink(body: string): Observable<string> {
    return this._http.postAsText(apiUrl.validateOdcLink, body);
  }

  /**
   * Requested as text: a refusal is a plain-text 4xx, and with fetch-based HttpClient a JSON request loses that body
   * (the error is a bare SyntaxError), so the customer would never see why.
   */
  createOrder(body: CreateOrderRequest): Observable<CreateOrderResponse> {
    return this._http.postJson<CreateOrderResponse>(apiUrl.createOrder, body);
  }

  createPayment(body: CreatePaymentRequest): Observable<string> {
    return this._http.postAsText(apiUrl.createPayment, body);
  }

  getOrderSummary(orderId: string): Observable<OrderSummaryResponse> {
    const params = new HttpParams().set('id', orderId);
    return this._http.getJson<OrderSummaryResponse>(apiUrl.getOrderSummary, params);
  }

  /** Public promotion codes of an on-sale session (needs login; 403 when the session is not open). */
  getPromotionOffers(showId: string, sessionId: string): Observable<PromotionOfferResponse[]> {
    const params = new HttpParams().set('showId', showId).set('sessionId', sessionId);
    return this._http.getJson<PromotionOfferResponse[]>(apiUrl.getPromotionOffers, params);
  }
}
