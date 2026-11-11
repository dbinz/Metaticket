export const apiUrl = {
  // auth
  getAuthCaptchaSetting: 'public/customer-auth/captcha-setting',
  login: 'public/customer-auth/login',
  logout: 'public/customer-auth/logout',
  refreshToken: 'public/customer-auth/refresh-token',

  // user
  getUserCaptchaSetting: 'public/customer-user/captcha-setting',
  getUserEmailVerificationSetting: 'public/customer-user/email-verification-setting',
  registerUser: 'public/customer-user/register',
  registerUserInfo: 'public/customer-user/register/create-info',
  initSessionRegisterUser: 'public/customer-user/register/init-session',
  sendOtpRegisterUser: 'public/customer-user/register/send-otp',
  resendOtpRegisterUser: 'public/customer-user/register/resend-otp',
  verifyOtpRegisterUser: 'public/customer-user/register/verify-otp',
  getUserProfile: 'customer-user/profile',
  initSessionUpdateProfile: 'customer-user/update-profile/init-session',
  sendOtpUpdateProfile: 'customer-user/update-profile/send-otp',
  resendOtpUpdateProfile: 'customer-user/update-profile/resend-otp',
  verifyOtpUpdateProfile: 'customer-user/update-profile/verify-otp',
  updateProfile: 'customer-user/update-profile',

  // reset password
  resetPassword: 'public/customer-user/reset-pass',
  initSessionResetPass: 'public/customer-user/reset-pass/init-session',
  verifyCaptchaResetPass: 'public/customer-user/reset-pass/verify-captcha',
  sendOtpResetPass: 'public/customer-user/reset-pass/send-otp',
  resendOtpResetPass: 'public/customer-user/reset-pass/resend-otp',
  verifyOtpResetPass: 'public/customer-user/reset-pass/verify-otp',

  // ticket slots
  getTicketSlots: 'ticket-slots/fetch-by-owner',
  getTicketSlotQrCode: 'ticket-slots/get-qrcode',
  getTicketSlotsCaptchaSetting: 'ticket-slots/captcha-setting',
  initSessionTicketSlots: 'ticket-slots/transfer/init-session',
  sendOtpTicketSlots: 'ticket-slots/transfer/send-otp',
  resendOtpTicketSlots: 'ticket-slots/transfer/resend-otp',
  verifyOtpTicketSlots: 'ticket-slots/transfer/verify-otp',
  transferTicketSlot: 'ticket-slots/transfer',

  // ticket
  getAllTicket: 'ticket/doc/fetch-all',
  getTicketByTime: 'ticket/doc/fetch-time',
  getCalendarMonth: 'ticket/calendar/month',
  createReservation: 'ticket/reservation/create',
  reserveReservation: 'ticket/reservation/reserve',
  releaseReservation: 'ticket/reservation/release',
  getSingleReservation: 'ticket/reservation/fetch',

  // order
  tentativePrice: 'order/price/tentative',
  getCustomFields: 'order/custom-field/fetch',
  createOrder: 'order/create',
  createPayment: 'order/payment/create',
  getOrderSummary: 'order/fetch/summary',
  getOrdersEventInfo: 'order/fetch/orders-event-info',
  getOrderEventInfoById: 'order/fetch/order-event-info-by-id',
  getPromotionOffers: 'order/promotions/offers',
  generateOdcLink: 'odc/link/generate',
  validateOdcLink: 'odc/data/is-valid/link',

  // payment
  startPayment: 'payment/start',
  getPaymentStatus: 'payment/fetch-status',
  updatePaymentResult: '/payment/return/result',

  // show
  getShowDetail: 'public/show/fetch',
  getShowBySlug: 'public/show/fetch/slug',
  getEventCategory: 'public/show/event-category/fetch',
  getListCity: 'public/show/city/fetch/all',
  // seat plan blocks for the lone-seat rule (service.editor.seatplanner, same public path as the seat plan document)
  getSeatBlocks: 'public/editor/seatplanner/seat-blocks',
  // metasearch
  getShowFilter: 'public/metasearch/shows/filter',
  getHighlightShow: 'public/metasearch/shows/highlight',
  getTopBanner: 'public/metasearch/shows/top-banner',
  searchShowByKeyword: 'public/metasearch/shows/suggestion',

  // accessory (served by service.accessory via nginx public/accessory/*)
  getAccessoryShows: 'public/accessory/show/fetch/all',
  getAccessoryShowBanner: 'public/accessory/show/pin/banner',
  getAccessoryShow: 'public/accessory/show/fetch',
  getAccessoryShowBySlug: 'public/accessory/show/fetch/slug',
  getAccessory: 'public/accessory/fetch',
  searchAccessoryShows: 'public/accessory/show/search',
  validateDiscount: 'accessory/discount/validate',
  getAccessoryDiscountOffers: 'accessory/discount/offers',
  accessoryCheckout: 'accessory/checkout',
  getAccessoryOrder: 'public/accessory/order/fetch',
  getMyAccessoryOrders: 'accessory/order/fetch/mine',
  cancelAccessoryOrder: 'accessory/order/cancel',
  getPendingAccessoryOrder: 'accessory/order/pending',

  // news
  getNewsCategoryList: 'public/news/category/fetch/all',
  getNewsList: 'public/news/fetch/all',
  getNewsBySlug: 'public/news/fetch/slug',
  getFeaturedNews: 'public/news/fetch/featured',
  searchNews: 'public/news/search',
  getRelatedNews: 'public/news/fetch/related',
  getNewsAdvertisement: 'public/news/advertisement/fetch',

  // vote (temporary feature)
  getVoteOptions: 'public/customer-auth/vote/options',
  getMyVote: 'customer-auth/vote/me',
  submitVote: 'customer-auth/vote',
};
