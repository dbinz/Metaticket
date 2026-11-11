import { HttpResponse } from '@angular/common/http';
import {
    AddNinePayAccountRequest,
    AddOnePayAccountRequest,
    AddVNPayAccountRequest,
    BookingUploadErrorResponse,
    BookingUploadSuccessResponse,
    CreateReportFilterRequest,
    LoadReportFilterListRequest,
    LoadReportResponse,
    MailTemplateModel,
    NinePayAccountModel,
    OnePayAccountModel,
    PaymentResponse,
    PaymentType,
    PGAccountModel,
    PGConfigModel,
    ReportFilterResponse,
    TicketBookingResponse,
    UpdateNinePayAccountRequest,
    UpdateOnePayAccountRequest,
    UpdateVNPayAccountRequest,
    VNPayAccountModel,
    NewsPageResponse,
    NewsArticleRow,
    NewsArticleForm,
    NewsCategoryOption,
    SaveNewsArticleRequest,
    AccessoryShowModel,
    AccessoryProductModel,
    AccessoryProductUpsertInput,
    AccessoryProductStockAdjustInput,
    AccessoryDiscountModel,
    AccessoryDiscountUpsertInput,
    AccessoryDiscountBatchResult,
    AccessoryOrderListModel,
    AccessoryAdminOrderExportBundle,
    AccessoryReportFilterCreateInput,
    AccessoryReportFilterModel,
    AccessorySalesStatisticModel,
    AccessoryShowStatus,
    AccessoryShowUpsertInput,
} from '../model';
import { Observable } from 'rxjs';
import {
    CityModel,
    CustomFieldTmpl,
    DownloadOrderReportRequest,
    EventCategoryModel,
    InvoiceStatisticsByShow,
    LoadInvoiceStatisticsRequest,
    OrderResponse,
    SeatplanCategoryModel,
    SessionCalendarConfig,
    SessionType,
    ShowModel,
    ShowSessionDraftModel,
    ShowSessionDraftOrderCustomFieldModel,
    ShowSessionDraftPromotionModel,
    ShowSessionModel,
    UploadShowImageResponseModel,
    UserModel,
} from '../model';

export abstract class UserService {
    abstract getUser$(): Observable<UserModel>;
}

export abstract class ShowService {
    abstract getShows$(): Observable<ShowModel[]>;
    abstract getShow$(id: string): Observable<ShowModel>;
    abstract getShowSessions$(id: string): Observable<ShowSessionModel[]>;
    abstract getMarketTicketLink$(showId: string, sessionId: string): Observable<string>;
    abstract getShowDetailLink$(showId: string): Observable<string>;
    abstract getEventCategory$(): Observable<EventCategoryModel[]>;
    abstract requestApproveShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract rejectShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract approveShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract startShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract stopShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract pauseShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract terminateShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract createShows$(
        name: string,
        code: string,
        bannerImage: string,
        detailImageUrl: string,
        thumbnailUrl: string,
        description: string,
        ticketDescription: string,
        policy: string,
        faq: string,
        eventCategoryCode: string,
        eventCategory: string,
        pinned: boolean,
        hideSessions: boolean,
        logoShowImage?: string,
        logoHostImage?: string,
        slug?: string,
    ): Observable<ShowModel>;
    abstract updateShows$(
        id: string,
        name: string,
        code: string,
        ticketDescription: string,
        description: string,
        policy: string,
        faq: string,
        eventCategoryCode: string,
        eventCategory: string,
        pinned: boolean,
        hideSessions: boolean,
        bannerImage?: string,
        detailImageUrl?: string,
        thumbnailUrl?: string,
        logoShowImage?: string,
        logoHostImage?: string,
        slug?: string,
    ): Observable<ShowModel>;
    abstract uploadImage$(formData: FormData): Observable<UploadShowImageResponseModel>;
    abstract updateShowSession$(
        showId: string,
        sessionId: string,
        sessionName: string,
        code: string,
        address: string,
        startTime: number,
        endTime: number,
        requireDelivery: boolean,
        orderNote: string,
        city: string,
        isFree: boolean,
        showInHome: boolean,
        ledgendImage?: string,
        eventSquareImage?: string,
        eventNameImage?: string,
        manageTicket?: boolean,
        qrcodeEmail?: boolean,
        slug?: string,
        sessionType?: SessionType,
        calendar?: SessionCalendarConfig | null,
    ): Observable<ShowSessionModel>;
    abstract updateShowSessionPayment$(
        showId: string,
        sessionId: string,
        paymentOffline?: boolean,
        paymentNote?: string,
        pgAccountCode?: string,
        paymentMethods?: PaymentType[],
    ): Observable<ShowSessionModel>;
    abstract updateShowSessionMailTemplates$(
        showId: string,
        sessionId: string,
        mailTemplates: unknown[],
    ): Observable<ShowSessionModel>;
    abstract updateShowSessionSeatPlan$(
        showId: string,
        sessionId: string,
        seatPlanId: string,
        edType: string,
        extraInfo?: string,
    ): Observable<ShowSessionModel>;
    abstract syncShowData$(): Observable<any>;
    abstract getCities(): Observable<CityModel[]>;
}

export abstract class AdminService {
    abstract getShowSessionsDraft$(id: string): Observable<ShowSessionDraftModel[]>;
    abstract createShowSessionDraft$(
        showId: string,
        name: string,
        code: string,
        address: string,
        startTime: number,
        endTime: number,
        requireDelivery: boolean,
        orderNote: string,
        cityId: string,
        isFree: boolean,
        showInHome: boolean,
        ledgendImage: string | undefined,
        ledgendImageUrl: string | undefined,
        eventSquareImage: string | undefined,
        eventSquareImageUrl: string | undefined,
        eventNameImage: string | undefined,
        eventNameImageUrl: string | undefined,
        manageTicket: boolean | undefined,
        qrcodeEmail: boolean | undefined,
        slug?: string,
        sessionType?: SessionType,
        calendar?: SessionCalendarConfig | null,
    ): Observable<ShowSessionDraftModel>;
    abstract updateShowSessionDraft$(
        id: String,
        sessionName: string,
        code: string,
        address: string,
        startTime: number,
        endTime: number,
        requireDelivery: boolean,
        orderNote: string,
        cityId: string,
        isFree: boolean,
        showInHome: boolean,
        ledgendImage: string | undefined,
        ledgendImageUrl: string | undefined,
        eventSquareImage: string | undefined,
        eventSquareImageUrl: string | undefined,
        eventNameImage: string | undefined,
        eventNameImageUrl: string | undefined,
        manageTicket: boolean | undefined,
        qrcodeEmail: boolean | undefined,
        slug?: string,
        sessionType?: SessionType,
        calendar?: SessionCalendarConfig | null,
    ): Observable<ShowSessionDraftModel>;
    abstract updateShowSessionDraftPayment$(
        id: String,
        paymentOffline?: boolean,
        paymentNote?: string,
        pgAccountCode?: string,
        paymentMethods?: PaymentType[],
    ): Observable<ShowSessionDraftModel>;
    abstract updateShowSessionDraftSeatPlan$(
        id: String,
        seatPlanId: string,
        edType: string,
        extraInfo?: string,
    ): Observable<ShowSessionDraftModel>;
    abstract updateShowSessionDraftMailTemplates$(
        id: String,
        mailTemplates: unknown[],
    ): Observable<ShowSessionDraftModel>;
    abstract getShowSessionsDraftPromotions$(sessionDraftId: string): Observable<ShowSessionDraftPromotionModel[]>;
    abstract createShowSessionDraftPromotions$(
        sessionDraftId: string,
        name: string,
        promotionData: string,
    ): Observable<ShowSessionDraftPromotionModel>;
    abstract updateShowSessionDraftPromotions$(
        id: string,
        name: string,
        promotionData: string,
    ): Observable<ShowSessionDraftPromotionModel>;
    abstract getShowSessionsDraftOrderCustomFields$(
        sessionDraftId: string,
    ): Observable<ShowSessionDraftOrderCustomFieldModel>;
    abstract createShowSessionDraftOrderCustomFields$(
        sessionDraftId: String,
        customFields: any[],
    ): Observable<ShowSessionDraftOrderCustomFieldModel>;
    abstract updateShowSessionDraftOrderCustomFields$(
        id: String,
        customFields: unknown[],
    ): Observable<ShowSessionDraftOrderCustomFieldModel>;
    abstract createShowSession$(id: string): Observable<ShowSessionDraftModel>;
    abstract updateShowSession$(id: string): Observable<ShowSessionDraftModel>;
    abstract deleteShowSessionDraft$(id: string): Observable<{ id: string; deleted: boolean }>;
    abstract createReportFilter$(req: CreateReportFilterRequest): Observable<string>;
    abstract loadReportFilterList$(req: LoadReportFilterListRequest): Observable<ReportFilterResponse[]>;
    abstract disableReportFilter$(reportId: string): Observable<ReportFilterResponse>;
    abstract enableReportFilter$(reportId: string): Observable<ReportFilterResponse>;
    abstract deleteReportFilter$(reportId: string): Observable<string>;
    abstract loadInvoiceStatistics$(reportKey: string): Observable<InvoiceStatisticsByShow[]>;
    abstract loadReport$(
        reportKey: string,
        loadOrderRequest: DownloadOrderReportRequest,
    ): Observable<LoadReportResponse>;
    abstract loadTicketBooking(showId: string, sessionId: string): Observable<TicketBookingResponse[]>;
    abstract bookingTicketFromFile(
        file: File,
        showId: string,
        sessionId: string,
        customerUserId: string,
    ): Observable<HttpResponse<BookingUploadSuccessResponse | BookingUploadErrorResponse>>;
}

export abstract class NotificationService {
    abstract getMailTemplates(): Observable<MailTemplateModel[]>;
    abstract getMailTemplate(id: string): Observable<MailTemplateModel>;
    abstract getMailPlugins(): Observable<String[]>;
    abstract createMailTemplate(
        id: string,
        root: string,
        customFieldItem: string,
        priceItem: string,
        promotionItem: string,
        ticketPdfTemplate?: string,
        ticketPdfFileName?: string,
    ): Observable<MailTemplateModel>;
    abstract updateMailTemplate(
        id: string,
        root: string,
        customFieldItem: string,
        priceItem: string,
        promotionItem: string,
        ticketPdfTemplate?: string,
        ticketPdfFileName?: string,
    ): Observable<MailTemplateModel>;
}

export abstract class SeatplanService {
    abstract getSeatplanCategory(id: string): Observable<SeatplanCategoryModel>;
}

export abstract class OrderService {
    abstract getOrderCustomFields(showId: string, sessionId: string): Observable<CustomFieldTmpl[]>;
    abstract getPromotions(showId: string, sessionId: string): Observable<any>;
    abstract updatePromotion(
        showId: string,
        sessionId: string,
        promotionId: string | null,
        pluginName: string,
        promotionData: string,
    ): Observable<any>;
    abstract updateOrderCustomFields(
        showId: string,
        sessionId: string,
        customFields: any[],
    ): Observable<CustomFieldTmpl[]>;
    abstract loadStatistic(request: LoadInvoiceStatisticsRequest): Observable<InvoiceStatisticsByShow[]>;
    abstract loadReport(request: DownloadOrderReportRequest): Observable<OrderResponse[]>;
}

export abstract class PaymentService {
    abstract getPayments(paymentIds: string[]): Observable<PaymentResponse[]>;

    abstract getPGConfig(): Observable<PGConfigModel>;

    abstract getPGAccount(code: string): Observable<PGAccountModel>;
    abstract deletePGAccount(code: string): Observable<any>;
    abstract enablePGAccount(code: string): Observable<any>;
    abstract disablePGAccount(code: string): Observable<any>;
    abstract setDefaultPGAccount(code: string): Observable<any>;

    abstract addOnePayAccount(request: AddOnePayAccountRequest): Observable<OnePayAccountModel>;
    abstract updateOnePayAccount(request: UpdateOnePayAccountRequest): Observable<OnePayAccountModel>;

    abstract addNinePayAccount(request: AddNinePayAccountRequest): Observable<NinePayAccountModel>;
    abstract updateNinePayAccount(request: UpdateNinePayAccountRequest): Observable<NinePayAccountModel>;

    abstract addVNPayAccount(request: AddVNPayAccountRequest): Observable<VNPayAccountModel>;
    abstract updateVNPayAccount(request: UpdateVNPayAccountRequest): Observable<VNPayAccountModel>;
}

/** Admin news CRUD / visibility — backed by service.news private API. */
export abstract class NewsService {
    abstract listArticles(
        page: number,
        size: number,
        search?: string,
        category?: string,
    ): Observable<NewsPageResponse<NewsArticleRow>>;

    abstract getArticle(id: number): Observable<NewsArticleForm>;

    abstract createArticle(request: SaveNewsArticleRequest): Observable<NewsArticleForm>;

    abstract updateArticle(id: number, request: SaveNewsArticleRequest): Observable<NewsArticleForm>;

    abstract setVisibility(id: number, visible: boolean): Observable<NewsArticleRow>;

    abstract listCategories(): Observable<NewsCategoryOption[]>;
}

/**
 * Admin accessory-show catalog. Phase UI uses an in-memory mock implementation —
 * no DB persistence until write APIs land.
 */
export abstract class AccessoryShowService {
    abstract getShows$(): Observable<AccessoryShowModel[]>;
    abstract getShow$(id: string): Observable<AccessoryShowModel>;
    abstract createShow$(input: AccessoryShowUpsertInput): Observable<AccessoryShowModel>;
    abstract updateShow$(id: string, input: AccessoryShowUpsertInput): Observable<AccessoryShowModel>;
    abstract setStatus$(id: string, status: AccessoryShowStatus): Observable<AccessoryShowModel>;
    abstract getProducts$(showId: string): Observable<AccessoryProductModel[]>;
    abstract getProduct$(id: string): Observable<AccessoryProductModel>;
    abstract createProduct$(input: AccessoryProductUpsertInput): Observable<AccessoryProductModel>;
    abstract updateProduct$(id: string, input: AccessoryProductUpsertInput): Observable<AccessoryProductModel>;
    abstract setProductActive$(id: string, active: boolean): Observable<AccessoryProductModel>;
    abstract adjustProductStock$(
        id: string,
        body: AccessoryProductStockAdjustInput,
    ): Observable<AccessoryProductModel>;
    abstract getDiscounts$(showId: string): Observable<AccessoryDiscountModel[]>;
    abstract getDiscount$(id: string): Observable<AccessoryDiscountModel>;
    abstract createDiscount$(input: AccessoryDiscountUpsertInput): Observable<AccessoryDiscountModel>;
    abstract createDiscountsBatch$(
        inputs: AccessoryDiscountUpsertInput[],
    ): Observable<AccessoryDiscountBatchResult>;
    abstract updateDiscount$(id: string, input: AccessoryDiscountUpsertInput): Observable<AccessoryDiscountModel>;
    abstract setDiscountActive$(id: string, active: boolean): Observable<AccessoryDiscountModel>;
    abstract deleteDiscount$(id: string): Observable<void>;
    abstract getOrders$(showId: string): Observable<AccessoryOrderListModel>;
    abstract getOrderStats$(
        showId: string,
        startAt?: number | null,
        endAt?: number | null,
    ): Observable<AccessorySalesStatisticModel>;
    abstract exportOrders$(
        showId: string,
        startAt?: number | null,
        endAt?: number | null,
    ): Observable<AccessoryAdminOrderExportBundle>;
    abstract getReportFilters$(showId?: string | null): Observable<AccessoryReportFilterModel[]>;
    abstract getReportFilterByKey$(reportKey: string): Observable<AccessoryReportFilterModel>;
    abstract createReportFilter$(input: AccessoryReportFilterCreateInput): Observable<string>;
    abstract enableReportFilter$(reportId: string): Observable<AccessoryReportFilterModel>;
    abstract disableReportFilter$(reportId: string): Observable<AccessoryReportFilterModel>;
    abstract deleteReportFilter$(reportId: string): Observable<void>;
}
