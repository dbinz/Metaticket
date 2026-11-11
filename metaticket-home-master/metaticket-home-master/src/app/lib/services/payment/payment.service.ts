import { Injectable } from '@angular/core';
import { HttpClientService } from '../http-client/http-client.service';
import { Observable } from 'rxjs';
import { PaymentRedirectResponse, PaymentStatusResponse, StartPaymentRequest } from 'src/app/lib/entities';
import { apiUrl } from 'src/app/lib/constants';
import { HttpParams } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class PaymentService {
  constructor(private _http: HttpClientService) {}

  startPayment(body: StartPaymentRequest): Observable<string> {
    return this._http.postAsText(apiUrl.startPayment, body);
  }

  getPaymentStatus(paymentId: string): Observable<PaymentStatusResponse> {
    const params = new HttpParams().set('id', paymentId);
    return this._http.getJson(apiUrl.getPaymentStatus, params);
  }

  updatePaymentResultFromReturnUrl(data: string): Observable<PaymentRedirectResponse> {
    return this._http.getJson(`${apiUrl.updatePaymentResult}/${data}`);
  }
}
