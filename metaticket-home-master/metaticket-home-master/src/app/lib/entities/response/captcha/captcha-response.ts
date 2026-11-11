export interface CaptchaSettingResponse {
  enable: boolean;
  captchaGateway: 'hcaptcha' | 'recaptcha';
  siteKey: string;
}
