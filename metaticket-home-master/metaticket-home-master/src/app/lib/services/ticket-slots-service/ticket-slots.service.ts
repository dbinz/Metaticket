import { Injectable } from '@angular/core';
import { HttpClientService } from '../http-client/http-client.service';
import { Observable } from 'rxjs';
import { CaptchaSettingResponse, TicketSlotResponse, TransferTicketSlotRequest } from '../../entities';
import { apiUrl } from '../../constants';
import { HttpParams } from '@angular/common/http';

@Injectable({
  providedIn: 'root',
})
export class TicketSlotsService {
  constructor(private _http: HttpClientService) {}

  /**
   * Retrieves a paginated list of ticket slots from the backend API.
   *
   * @param page The page number to fetch. The interpretation (0-based or 1-based)
   *             depends on your backend API's implementation.
   * @param pageSize The number of ticket slots to retrieve per page.
   * @returns An Observable that emits an array of TicketSlotResponse objects for the requested page.
   */
  getAllTicketSlots(page: number, pageSize: number): Observable<TicketSlotResponse[]> {
    const params = new HttpParams().set('page', page).set('size', pageSize);
    return this._http.getJson<TicketSlotResponse[]>(apiUrl.getTicketSlots, params);
  }

  getQrCode(slotId: string, stateIdx: number): Observable<string> {
    const params = new HttpParams().set('slotId', slotId).set('stateIdx', stateIdx);
    return this._http.getAsText(apiUrl.getTicketSlotQrCode, params);
  }

  getCaptchaSetting(): Observable<CaptchaSettingResponse> {
    return this._http.getJson(apiUrl.getTicketSlotsCaptchaSetting);
  }

  initSessionTransferTicketSlot(captchaToken?: string): Observable<void> {
    return this._http.postVoid(apiUrl.initSessionTicketSlots, { captchaToken });
  }

  sendOtpTransferTicketSlot(slotId: string): Observable<void> {
    return this._http.postVoid(apiUrl.sendOtpTicketSlots, { slotId: slotId });
  }

  resendOtpTransferTicketSlot(): Observable<void> {
    return this._http.postVoid(apiUrl.resendOtpTicketSlots, {});
  }

  verifyOtpTransferTicketSlot(slotId: string, otp: string): Observable<void> {
    return this._http.postVoid(apiUrl.verifyOtpTicketSlots, { slotId: slotId, otp: otp });
  }

  transferTicketSlot(request: TransferTicketSlotRequest): Observable<void> {
    return this._http.postVoid(apiUrl.transferTicketSlot, request);
  }
}
