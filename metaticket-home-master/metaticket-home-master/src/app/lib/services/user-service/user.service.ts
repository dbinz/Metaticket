import { Injectable } from '@angular/core';
import { HttpClientService } from '../http-client/http-client.service';
import { apiUrl } from '../../constants';
import { Observable } from 'rxjs';
import {
  CaptchaSettingResponse,
  CreateUserRequest,
  EmailVerificationSettingResponse,
  UserProfileModel,
} from '../../entities';

@Injectable({
  providedIn: 'root',
})
export class UserService {
  constructor(private _http: HttpClientService) {}

  getCaptchaSetting(): Observable<CaptchaSettingResponse> {
    return this._http.getJson(apiUrl.getUserCaptchaSetting);
  }

  getEmailVerificationSetting(): Observable<EmailVerificationSettingResponse> {
    return this._http.getJson(apiUrl.getUserEmailVerificationSetting);
  }

  initSessionRegisterUser(captchaToken?: string): Observable<void> {
    return this._http.postVoid(apiUrl.initSessionRegisterUser, { captchaToken });
  }

  sendOtpRegisterUser(phone: string, email?: string): Observable<void> {
    return this._http.postVoid(apiUrl.sendOtpRegisterUser, { phone, email });
  }

  resendOtpRegisterUser(): Observable<void> {
    return this._http.postVoid(apiUrl.resendOtpRegisterUser, {});
  }

  verifyOtpRegisterUser(phone: string, otp: string, email?: string): Observable<void> {
    return this._http.postVoid(apiUrl.verifyOtpRegisterUser, { phone: phone, otp: otp, email: email });
  }

  createUser(request: CreateUserRequest): Observable<void> {
    return this._http.postVoid(apiUrl.registerUser, request);
  }

  initSessionResetPassword(captchaToken?: string): Observable<void> {
    return this._http.postVoid(apiUrl.initSessionResetPass, { captchaToken });
  }

  sendOtpResetPassword(phone: string): Observable<void> {
    return this._http.postVoid(apiUrl.sendOtpResetPass, { phone: phone });
  }

  resendOtpResetPassword(): Observable<void> {
    return this._http.postVoid(apiUrl.resendOtpResetPass, {});
  }

  verifyOtpResetPassword(phone: string, otp: string): Observable<void> {
    return this._http.postVoid(apiUrl.verifyOtpResetPass, { phone: phone, otp: otp });
  }

  resetPassword(phone: string, password: string, otp: string): Observable<void> {
    return this._http.postVoid(apiUrl.resetPassword, { phone: phone, otp: otp, password: password });
  }

  getUserProfile(): Observable<UserProfileModel> {
    return this._http.getJson(apiUrl.getUserProfile);
  }

  initSessionUpdateProfile(): Observable<void> {
    return this._http.postVoid(apiUrl.initSessionUpdateProfile, {});
  }

  sendOtpUpdateProfile(email: string): Observable<void> {
    return this._http.postVoid(apiUrl.sendOtpUpdateProfile, { email });
  }

  resendOtpUpdateProfile(): Observable<void> {
    return this._http.postVoid(apiUrl.resendOtpUpdateProfile, {});
  }

  verifyOtpUpdateProfile(email: string, otp: string): Observable<void> {
    return this._http.postVoid(apiUrl.verifyOtpUpdateProfile, { email, otp });
  }

  updateProfile(name: string, email: string, otp?: string): Observable<void> {
    return this._http.postVoid(apiUrl.updateProfile, { name, email, otp });
  }
}
