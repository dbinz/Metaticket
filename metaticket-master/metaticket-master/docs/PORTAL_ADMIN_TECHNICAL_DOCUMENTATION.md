# Tài Liệu Kỹ Thuật Portal Admin - MetaTicket

## Tổng Quan Hệ Thống

**Portal Admin** là ứng dụng web quản trị dành cho admin của hệ thống bán vé sự kiện MetaTicket. Được xây dựng bằng Angular với Nebular UI framework, cung cấp giao diện quản lý toàn diện cho việc điều hành hệ thống bán vé.

### Thông Tin Dự Án
- **Framework**: Angular 12+
- **UI Library**: Nebular Theme (NbAuth, NbLayout, NbCard, etc.)
- **Kiến trúc**: Module-based Architecture với Lazy Loading
- **Ngôn ngữ**: TypeScript
- **Styling**: SCSS
- **State Management**: RxJS Observables
- **Data Tables**: Angular 2 Smart Table (ng2-smart-table)

---

## Mục lục

1. [Cấu trúc Dự án](#cấu-trúc-dự-án)
2. [Modules](#modules)
3. [Routing](#routing)
4. [Components](#components)
5. [Services](#services)
6. [Gateways](#gateways)
7. [Models](#models)
8. [Authentication](#authentication)
9. [Menu Navigation](#menu-navigation)
10. [QR Code Scanner](#qr-code-scanner)
11. [UI Components](#ui-components)

---

## Cấu trúc Dự án

```
portal.admin/
├── src/
│   └── app/
│       ├── @core/
│       │   ├── metaticket/
│       │   │   ├── data.service/
│       │   │   │   └── data.service.ts        # Abstract services
│       │   │   └── model/
│       │   │       ├── index.ts               # Export all models
│       │   │       ├── show.model.ts
│       │   │       ├── order.model.ts
│       │   │       ├── ticket.model.ts
│       │   │       ├── payment.model.ts
│       │   │       ├── seatplan.model.ts
│       │   │       ├── user.model.ts
│       │   │       ├── notification.model.ts
│       │   │       ├── pg.account.model.ts
│       │   │       ├── report.filter.model.ts
│       │   │       ├── ticket.slot.model.ts
│       │   │       └── ticket.booking.model.ts
│       │   └── services/
│       ├── @theme/
│       │   ├── components/
│       │   │   ├── login/
│       │   │   ├── header/
│       │   │   ├── footer/
│       │   │   └── search-input/
│       │   ├── layouts/
│       │   ├── pipes/
│       │   └── theme.module.ts
│       ├── metaticket/                        # Main business module
│       │   ├── gateway/                       # API Gateway services
│       │   ├── service/                       # Service implementations
│       │   ├── dialog/                        # Dialog components
│       │   ├── dashboard/                     # Dashboard module
│       │   ├── seat_map/                      # Seat map module
│       │   ├── metaticket-menu.ts             # Menu configuration
│       │   ├── metaticket-routing.module.ts
│       │   ├── auth-Interceptor.ts
│       │   └── [25+ components]
│       ├── public/                            # Public reports module
│       ├── qrcode/                            # QR code scanner module
│       ├── app-routing.module.ts
│       ├── app.module.ts
│       └── main.ts
├── environments/
│   └── environment.ts
└── angular.json
```

---

## Modules

### 1. AppModule (Root)
Module gốc của ứng dụng, import tất cả các module con.

### 2. MetaticketModule
Module chính chứa toàn bộ business logic cho admin portal.

**Features:**
- Shows Management
- Orders Management
- Tickets Management
- Reports & Statistics
- Payment Gateway Configuration
- Email Templates Management
- Seat Map Management
- Category Mapping

### 3. PublicModule
Module cho báo cáo công khai, không yêu cầu authentication đầy đủ.

### 4. QrcodeModule
Module cho việc quét và xác thực QR code tại cổng vào sự kiện.

### 5. ThemeModule
Module chứa các theme components: Header, Footer, Login, Layouts.

### 6. DashboardModule
Module cho dashboard với analytics và thống kê.

### 7. SeatMapModule
Module cho việc quản lý và vẽ seat plan (lazy loaded).

### 8. DialogModule
Module chứa các dialog components tái sử dụng.

---

## Routing

### Root Routing (app-routing.module.ts)

```typescript
const routes: Routes = [
    {
        path: 'portal',
        loadChildren: () => import('./metaticket/metaticket.module').then(m => m.MetaticketModule),
    },
    {
        path: 'auth',
        component: NbAuthComponent,
        children: [
            { path: 'login', component: CustomLoginComponent },
            { path: 'register', component: NbRegisterComponent },
            { path: 'logout', component: NbLogoutComponent },
        ],
    },
    {
        path: 'public',
        loadChildren: () => import('./public/public.module').then(m => m.PublicModule),
    },
    {
        path: 'qrcode',
        loadChildren: () => import('./qrcode/qrcode.module').then(m => m.QrcodeModule),
    },
    { path: '', redirectTo: 'portal', pathMatch: 'full' },
];
```

### Metaticket Routing (metaticket-routing.module.ts)

```typescript
const routes: Routes = [
    {
        path: '',
        canActivate: [MetaticketAuthGuardService],
        component: MetaticketComponent,
        children: [
            { path: 'dashboard', component: DashboardComponent },
            { path: 'report/orders', component: OrdersComponent },
            { path: 'report/statistic', component: OrderStatisticComponent },
            { path: 'report/offline-payment', component: OfflinePaymentComponent },
            { path: 'report/filter', component: ReportFilterComponent },
            { path: 'tickets', component: TicketsComponent },
            { path: 'ticket/category', component: CategoryMappingComponent },
            { path: 'ticket/create', component: CreateTicketComponent },
            { path: 'ticket-slots', component: TicketSlotsComponent },
            { path: 'ticket/reservations', component: ReservationOfTicketComponent },
            { path: 'reservation-order', component: ReservationOrderDetailsComponent },
            { path: 'show/create', component: CreateShowComponent },
            { path: 'show/update', component: CreateShowComponent },
            { path: 'show/session/create', component: CreateShowSessionComponent },
            { path: 'shows', component: ShowsComponent },
            { path: 'tickets/booking', component: TicketBookingComponent },
            { path: 'mail/templates', component: MailTemplatesComponent },
            { path: 'mail/template/create', component: CreateMailTemplateComponent },
            { path: 'mail/template/update', component: CreateMailTemplateComponent },
            { path: 'pg-accounts', component: PGAccountComponent },
            { path: 'pg-account/onepay/create', component: CreateOnePayAccountComponent },
            { path: 'pg-account/onepay/detail', component: DetailOnePayAccountComponent },
            { path: 'pg-account/ninepay/create', component: CreateNinePayAccountComponent },
            { path: 'pg-account/ninepay/detail', component: DetailNinePayAccountComponent },
            { path: 'pg-account/vnpay/create', component: CreateVNPayAccountComponent },
            { path: 'pg-account/vnpay/detail', component: DetailVNPayAccountComponent },
            {
                path: 'seat-map',
                loadChildren: () => import('./seat_map/seat_map.module').then(m => m.SeatMapModule)
            },
            { path: '', redirectTo: 'shows', pathMatch: 'full' },
        ],
    },
];
```

---

## Components

### 1. Shows Management

| Component | Path | Description |
|-----------|------|-------------|
| `ShowsComponent` | `shows/shows.component.ts` | Danh sách tất cả shows |
| `CreateShowComponent` | `create_show/create_show.component.ts` | Tạo/Chỉnh sửa show |
| `CreateShowSessionComponent` | `create_show_session/create_show_session.component.ts` | Tạo session cho show |

### 2. Orders Management

| Component | Path | Description |
|-----------|------|-------------|
| `OrdersComponent` | `orders/orders.component.ts` | Danh sách đơn hàng |
| `ReservationOfTicketComponent` | `reservation_of_ticket/reservation.of.ticket.component.ts` | Quản lý đặt vé |
| `ReservationOrderDetailsComponent` | `reservation_order_details/reservation.order.details.component.ts` | Chi tiết đơn hàng đặt vé |

### 3. Tickets Management

| Component | Path | Description |
|-----------|------|-------------|
| `TicketsComponent` | `tickets/tickets.component.ts` | Danh sách vé |
| `CreateTicketComponent` | `create_ticket/create-ticket.component.ts` | Tạo vé mới |
| `TicketSlotsComponent` | `ticket_slots/ticket-slots.component.ts` | Quản lý ticket slots |
| `TicketBookingComponent` | `ticket_booking/ticket.booking.component.ts` | Đặt vé hàng loạt (Excel/CSV) |

### 4. Reports

| Component | Path | Description |
|-----------|------|-------------|
| `OrderStatisticComponent` | `order_statistic/order-statistic.component.ts` | Thống kê doanh số |
| `ReportFilterComponent` | `report_filter/report.filter.component.ts` | Bộ lọc báo cáo |
| `OfflinePaymentComponent` | `offline_payment/offline_payment.component.ts` | Thanh toán nội bộ |

### 5. Payment Gateway

| Component | Path | Description |
|-----------|------|-------------|
| `PGAccountComponent` | `pg_account/pg-account.component.ts` | Danh sách PG accounts |
| `CreateOnePayAccountComponent` | `pg_account/create_one_pay_account/create-onepay-account.component.ts` | Tạo OnePay account |
| `DetailOnePayAccountComponent` | `pg_account/detail_onepay_account/detail-onepay-account.component.ts` | Chi tiết OnePay |
| `CreateVNPayAccountComponent` | `pg_account/create_vnpay_account/create-vnpay-account.component.ts` | Tạo VNPay account |
| `DetailVNPayAccountComponent` | `pg_account/detail_vnpaypay_account/detail-vnpay-account.component.ts` | Chi tiết VNPay |
| `CreateNinePayAccountComponent` | `pg_account/create_ninepay_account/create-ninepay-account.component.ts` | Tạo NinePay account |
| `DetailNinePayAccountComponent` | `pg_account/detail_ninepay_account/detail-ninepay-account.component.ts` | Chi tiết NinePay |

### 6. Email Templates

| Component | Path | Description |
|-----------|------|-------------|
| `MailTemplatesComponent` | `mail_templates/mail-templates.component.ts` | Danh sách templates |
| `CreateMailTemplateComponent` | `create_mail_template/create_mail_template.component.ts` | Tạo/Chỉnh sửa template |

### 7. Category & Seat Map

| Component | Path | Description |
|-----------|------|-------------|
| `CategoryMappingComponent` | `category_mapping/category_mapping.component.ts` | Mapping hạng vé |
| `CategoryItemComponent` | `category_mapping/category_item/category_item.component.ts` | Item trong category mapping |

---

## Services

### Abstract Services (data.service.ts)

```typescript
// User Service
abstract class UserService {
    abstract getUser$(): Observable<UserModel>;
}

// Show Service - Quản lý shows và sessions
abstract class ShowService {
    abstract getShows$(): Observable<ShowModel[]>;
    abstract getShow$(id: string): Observable<ShowModel>;
    abstract getShowSessions$(id: string): Observable<ShowSessionModel[]>;
    abstract getMarketTicketLink$(showId: string, sessionId: string): Observable<string>;
    abstract getEventCategory$(): Observable<EventCategoryModel[]>;
    abstract requestApproveShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract approveShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract startShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract stopShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract pauseShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract terminateShowSession$(showId: string, sessionId: string): Observable<ShowSessionModel>;
    abstract createShows$(...): Observable<ShowModel>;
    abstract updateShows$(...): Observable<ShowModel>;
    abstract uploadImage$(formData: FormData): Observable<UploadShowImageResponseModel>;
    abstract updateShowSession$(...): Observable<ShowSessionModel>;
    abstract updateShowSessionPayment$(...): Observable<ShowSessionModel>;
    abstract updateShowSessionSeatPlan$(...): Observable<ShowSessionModel>;
    abstract getCities(): Observable<CityModel[]>;
}

// Admin Service - Quản lý admin và draft sessions
abstract class AdminService {
    abstract getShowSessionsDraft$(id: string): Observable<ShowSessionDraftModel[]>;
    abstract createShowSessionDraft$(...): Observable<ShowSessionDraftModel>;
    abstract updateShowSessionDraft$(...): Observable<ShowSessionDraftModel>;
    abstract createShowSession$(...): Observable<ShowSessionModel>;
    abstract loadInvoiceStatistics$(...): Observable<InvoiceStatisticsByShow[]>;
    abstract loadReport$(...): Observable<LoadReportResponse>;
    abstract loadTicketBooking$(...): Observable<TicketBookingResponse>;
    abstract bookingTicketFromFile$(...): Observable<BookingUploadSuccessResponse | BookingUploadErrorResponse>;
}

// Order Service - Quản lý đơn hàng
abstract class OrderService {
    abstract getOrderCustomFields(...): Observable<CustomFieldTmpl[]>;
    abstract getPromotions(...): Observable<any>;
    abstract updatePromotion(...): Observable<any>;
    abstract loadStatistic(...): Observable<InvoiceStatisticsByShow[]>;
    abstract loadReport(...): Observable<OrderResponse[]>;
}

// Payment Service - Quản lý thanh toán
abstract class PaymentService {
    abstract getPayments$(): Observable<PaymentResponse[]>;
    abstract getPGConfig$(): Observable<PGConfigModel>;
    abstract getPGAccount$(): Observable<PGAccountModel>;
    abstract addOnePayAccount$(...): Observable<OnePayAccountModel>;
    abstract addNinePayAccount$(...): Observable<NinePayAccountModel>;
    abstract addVNPayAccount$(...): Observable<VNPayAccountModel>;
}

// Notification Service - Quản lý email templates
abstract class NotificationService {
    abstract getMailTemplates$(): Observable<MailTemplateModel[]>;
    abstract getMailTemplate$(id: string): Observable<MailTemplateModel>;
    abstract createMailTemplate$(...): Observable<MailTemplateModel>;
    abstract updateMailTemplate$(...): Observable<MailTemplateModel>;
}
```

### Concrete Service Implementations (metaticket/service/)

| Service | File | Extends |
|---------|------|---------|
| `MetaticketShowService` | `metaticket.show.service.ts` | ShowService |
| `MetaticketAdminService` | `metaticket.admin.service.ts` | AdminService |
| `MetaticketUserService` | `metaticket.user.service.ts` | UserService |
| `MetaticketOrderService` | `metaticket.order.service.ts` | OrderService |
| `MetaticketPaymentService` | `metaticket.payment.service.ts` | PaymentService |
| `MetaticketNotificationService` | `metaticket.notification.service.ts` | NotificationService |
| `MetaticketAuthGuardService` | `service/metaticket.auth.guard.service.ts` | CanActivate |
| `QRCodeService` | `service/metaticket.service.qrcode.ts` | - |
| `MetaticketSeatplannerService` | `service/metaticket.seatplanner.service.ts` | - |
| `MetaticketPolicyDescService` | `service/metaticket.policydesc.service.ts` | - |

---

## Gateways

Gateways là lớp trung gian giữa services và API endpoints.

| Gateway | File | Key Methods |
|---------|------|-------------|
| `ShowGateway` | `gateway/show.gateway.ts` | `fetchAllShow()`, `fetchShow()`, `createShow()`, `fetchShowSessions()`, `requestApproveShowSession()`, `approveShowSession()`, `startShowSession()`, `pauseShowSession()`, `stopShowSession()`, `terminateShowSession()` |
| `AdminGateway` | `gateway/admin.gateway.ts` | `fetchAllShowSessionDraft()`, `createShowSessionDraft()`, `loadTicketBooking()`, `bookingTicketFromFile()` |
| `OrderGateway` | `gateway/order.gateway.ts` | `getOrderCustomFields()`, `getPromotions()`, `loadStatistic()`, `loadReport()` |
| `PaymentGateway` | `gateway/payment.gateway.ts` | `getPayments()`, `getPGConfig()`, `getPGAccount()`, `addOnePayAccount()`, `addNinePayAccount()`, `addVNPayAccount()` |
| `TicketGateway` | `gateway/ticket.gateway.ts` | `getTickets()`, `createTicket()` |
| `TicketSlotGateway` | `gateway/ticket.slot.gateway.ts` | `getTicketSlots()` |
| `NotificationGateway` | `gateway/notification.gateway.ts` | `getMailTemplates()`, `createMailTemplate()`, `updateMailTemplate()` |
| `SeatplannerGateway` | `gateway/seatplanner.gateway.ts` | `getSeatplanCategory()`, `getSeatplans()`, `createSeatplan()`, `updateSeatplan()` |
| `PolicyDescGateway` | `gateway/policydesc.gateway.ts` | `getPolicyDesc()` |
| `QRCodeGateway` | `gateway/qrcode.gateway.ts` | QR code validation |

### API Base URLs

```typescript
// environments/environment.ts
publicApi('/show/fetch/all')    // Public endpoints
privateApi('/show/create')       // Authenticated endpoints
```

---

## Models

### Show Model (show.model.ts)

```typescript
export type ShowSessionStatus = 
    | 'Created'
    | 'ApprovalWait'
    | 'Rejected'
    | 'OpeningWait'
    | 'Open'
    | 'Paused'
    | 'Stopped'
    | 'Terminated';

export interface ShowModel {
    id: string;
    showName: string;
    code: string;
    website: string;
    eventCategoryCode: string;
    eventCategory: string;
    bannerImageUrl: string;
    detailImageUrl: string;
    thumbnailUrl: string;
    logoHostUrl: string;
    logoShowUrl: string;
    status: string;
    description: string;
    ticketDescription: string;
    faq: string;
    policy: string;
    shipPrice: number;
    sessions: ShowSessionModel[];
    pinned: boolean;
    createdAt: number;
}

export interface ShowSessionModel {
    id: string;
    code: string;
    sessionName?: string;
    status: ShowSessionStatus;
    time: string;
    venue: string;
    startTime: number;
    endTime: number;
    seatPlanId: string;
    edType: string;
    ledgendImageUrl: string;
    eventSquareImageUrl: string;
    eventNameImageUrl: string;
    requireDelivery: boolean;
    paymentOffline: boolean;
    paymentNote: string;
    mailPluginTemplate: ShowSessionMailTemplateModel[];
    orderNote: string;
    city?: CityModel;
    isFree: boolean;
    showInHome: boolean;
    pgAccountCode?: string;
    paymentMethods?: PaymentType[];
    manageTicket?: boolean;
    qrcodeEmail?: boolean;
}

export interface EventCategoryModel {
    id: string;
    name: string;
}

export interface CityModel {
    id: string;
    name: string;
    code: string;
}
```

### Order Model (order.model.ts)

```typescript
export enum OrderStatus {
    // Various order statuses
}

export interface OrderResponse {
    id: string;
    code: string;
    customerName: string;
    customerPhone: string;
    customerEmail: string;
    orderStatus: OrderStatus;
    paymentStatus: PaymentStatus;
    totalPrice: number;
    // ... other fields
}

export interface InvoiceStatisticsByShow {
    // Statistics fields
}
```

### Ticket Model (ticket.model.ts)

```typescript
export interface TicketModel {
    // Ticket fields
}

export interface TicketReservationModel {
    // Reservation fields
}

export type TicketReservationStatus = // ... enum values

export type TicketStatus = // ... enum values
```

### Payment Model (payment.model.ts)

```typescript
export type PaymentType = 'VNPay' | 'OnePay' | 'NinePay' | 'Offline';

export type PaymentStatus = // ... enum values

export type PaymentGatewayType = // ... enum values
```

### Other Models

| Model File | Key Interfaces |
|------------|----------------|
| `seatplan.model.ts` | SeatplanModel, SeatType, CategoryMappingModel |
| `user.model.ts` | UserModel, ContactModel |
| `notification.model.ts` | MailTemplateModel |
| `pg.account.model.ts` | OnePayAccountModel, NinePayAccountModel, VNPayAccountModel |
| `ticket.slot.model.ts` | TicketSlotModel |
| `ticket.booking.model.ts` | TicketBookingResponse |

---

## Authentication

### Auth Guard (metaticket.auth.guard.service.ts)

```typescript
@Injectable({ providedIn: 'root' })
export class MetaticketAuthGuardService implements CanActivate {
    constructor(
        private authService: NbAuthService,
        private router: Router,
    ) {}

    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
        return this.authService.isAuthenticatedOrRefresh().pipe(
            tap(authenticated => {
                if (!authenticated) {
                    this.router.navigate(['/auth/login'], {
                        queryParams: { next: state.url },
                    });
                }
            }),
        );
    }
}
```

### HTTP Interceptor (auth-Interceptor.ts)

```typescript
@Injectable({ providedIn: 'root' })
export class AuthInterceptor implements HttpInterceptor {
    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        return this.authService.getToken().pipe(
            switchMap(token => {
                if (token && token.isValid()) {
                    const newReq = req.clone({
                        setHeaders: { Authorization: `Bearer ${token.getValue()}` },
                    });
                    return next.handle(newReq);
                }
                return next.handle(req);
            }),
            catchError((err: any) => {
                // Handle 401 - redirect to login
                if (status === 401) {
                    this.authService.logout('email').subscribe(() => {
                        window.location.href = '/auth/login';
                    });
                }
                throw err;
            }),
        );
    }
}
```

### Authentication Flow

1. User đăng nhập tại `/auth/login`
2. `NbAuthService` xác thực và lưu JWT token
3. `AuthInterceptor` thêm token vào mọi request
4. `MetaticketAuthGuardService` bảo vệ các routes cần authentication
5. Khi 401 response, tự động logout và redirect về login

---

## Menu Navigation

```typescript
export const MENU_ITEMS: NbMenuItem[] = [
    {
        title: 'META Ticket',
        group: true,
    },
    {
        title: 'Danh sách show',
        icon: 'film-outline',
        home: true,
        link: '/portal/shows',
    },
    {
        title: 'Báo cáo',
        icon: 'folder-outline',
        expanded: true,
        children: [
            { title: 'Filters', icon: 'funnel-outline', link: '/portal/report/filter' },
            { title: 'Thống kê', icon: 'trending-up-outline', link: '/portal/report/statistic' },
            { title: 'Danh sách đơn hàng', icon: 'layers-outline', link: '/portal/report/orders' },
            { title: 'Thanh toán nội bộ', icon: 'cast-outline', link: '/portal/report/offline-payment' },
        ],
    },
    {
        title: 'Hạng vé / Vé',
        icon: 'layers-outline',
        expanded: true,
        children: [
            { title: 'Hạng vé', icon: 'plus-square-outline', link: '/portal/ticket/category' },
            { title: 'Tạo vé', icon: 'plus-square-outline', link: '/portal/ticket/create' },
            { title: 'Danh sách vé', icon: 'list-outline', link: '/portal/tickets' },
        ],
    },
    // ... thêm các menu items khác
];
```

---

## QR Code Scanner

### QR Code Scanner Component

**Route**: `/qrcode/scanner/:sessionId?privateCode=<code>`

**Features:**
- Quét QR code bằng camera (sử dụng `qr-scanner` library)
- Nhập mã QR thủ công (hỗ trợ barcode scanner devices)
- Xác thực và hiển thị thông tin vé
- Bật/tắt đèn flash
- Chuyển đổi giữa camera và device input

### QR Code Format

```typescript
const prefixQRCode = 'MTTKQR:';        // QR code thường
const prefixSlotQRCode = 'MTTKSLOTQR:'; // QR code slot
```

### Data Models

```typescript
interface ScanQRCodeRequest {
    sessionId: string;
    verifyCode: string;
}

interface QRCodeTicketInfoModel {
    orderId: string;
    orderCode: string;
    customerName: string;
    customerPhone: string;
    ticketInfo: TicketInfo[];
    status: 'VALID' | 'INVALID' | 'USED';
}

interface TicketInfo {
    ticketId: string;
    categoryName: string;
    seatNumber?: string;
    price: number;
}
```

---

## UI Components

### Nebular UI Components Used

| Module | Components |
|--------|------------|
| `NbLayoutModule` | Main layout container |
| `NbSidebarModule` | Sidebar navigation |
| `NbMenuModule` | Menu items |
| `NbCardModule` | Card containers |
| `NbButtonModule` | Buttons |
| `NbInputModule` | Input fields |
| `NbSelectModule` | Dropdown selects |
| `NbAutocompleteModule` | Autocomplete |
| `NbCheckboxModule` | Checkboxes |
| `NbRadioModule` | Radio buttons |
| `NbDatepickerModule` | Date picker |
| `NbTimepickerModule` | Time picker |
| `NbStepperModule` | Stepper/wizard |
| `NbTabsetModule` | Tabs |
| `NbTagModule` | Tags |
| `NbAlertModule` | Alerts |
| `NbSpinnerModule` | Loading spinner |
| `NbIconModule` | Icons (Eva Icons) |
| `NbTreeGridModule` | Tree grid (for reports) |
| `NbCalendarRangeModule` | Date range picker |
| `NbFormFieldModule` | Form field wrapper |
| `NbButtonGroupModule` | Button groups |

### Third-party Libraries

| Library | Purpose |
|---------|---------|
| `ng2-smart-table` | Data tables with sorting, filtering, pagination |
| `qr-scanner` | QR code scanning |
| `exceljs` | Excel export |

### Custom Theme Components

- `CustomLoginComponent` - Login page
- `HeaderComponent` - App header
- `FooterComponent` - App footer
- `SearchInputComponent` - Search input
- `TinyMCEComponent` - Rich text editor

---

## Performance Optimization

### Lazy Loading

```typescript
// Example: Seat Map Module
{
    path: 'seat-map',
    loadChildren: () => import('./seat_map/seat_map.module').then(m => m.SeatMapModule)
}
```

### Change Detection

- Sử dụng `OnPush` strategy cho các component cần performance
- Immutable data patterns
- Proper subscription management (unsubscribe trong ngOnDestroy)

### Memory Management

- Cleanup camera resources khi destroy QR scanner component
- Remove event listeners

---

## Environment Configuration

```typescript
// environments/environment.ts
export const environment = {
    production: false,
    apiUrl: 'http://localhost:8080',
    publicApiUrl: 'http://localhost:8081'
};

// environments/environment.prod.ts
export const environment = {
    production: true,
    apiUrl: 'https://api.metaticket.vn',
    publicApiUrl: 'https://api-public.metaticket.vn'
};
```

---

## Kết Luận

Portal Admin là ứng dụng web quản trị toàn diện cho hệ thống bán vé MetaTicket:

| Feature | Description |
|---------|-------------|
| **Shows Management** | Tạo, chỉnh sửa, quản lý shows và sessions |
| **Orders Management** | Xem, lọc, gửi lại email xác nhận |
| **Tickets Management** | Tạo vé, quản lý ticket slots, đặt vé hàng loạt |
| **Reports** | Thống kê doanh số, bộ lọc báo cáo, export Excel |
| **Payment Gateway** | Cấu hình OnePay, VNPay, NinePay |
| **Email Templates** | Tạo và quản lý mail templates |
| **Seat Map** | Quản lý seat plan |
| **QR Code Scanner** | Xác thực vé tại cổng vào |
| **Category Mapping** | Mapping hạng vé |

**Technology Stack:**
- Angular 12+ với TypeScript
- Nebular UI Theme
- RxJS cho reactive programming
- Lazy loading cho tất cả modules
- JWT Authentication với NbAuth

---

**Tài liệu này được cập nhật lần cuối**: 04/06/2026
**Version**: 1.0
**Tác giả**: MetaTicket Development Team

## Slug Links (Đường dẫn thân thiện)

Portal Admin cho phép người tổ chức nhập **slug** cho show và cho từng session, để tạo link thân thiện trên homepage (`/s/{show-slug}` và `/s/{show-slug}/{session-slug}`). Slug là tùy chọn, có thể bỏ trống.

### Tiện ích dùng chung — `slug.util.ts`
`@core/metaticket/utils/slug.util.ts` là bản client mirror của `SlugNormalizer`/`SlugValidator` phía Show_Service:

- `normalizeSlug(input)`: chuyển text bất kỳ thành canonical slug (`[a-z0-9-]`, bỏ dấu tiếng Việt, gộp khoảng trắng/ký tự lạ thành `-`, cắt `-` đầu/cuối). Dùng để **sinh slug mặc định** và kiểm tra trùng.
- `isValidSlug(raw)`: **validate nghiêm theo giá trị nhập** (đã trim), KHÔNG normalize trước. Trả `null` nếu hợp lệ/vắng mặt; ngược lại trả `SlugError`:
  - `Empty` — (giữ lại cho thông điệp; thực tế input chỉ toàn ký tự lạ sẽ rơi vào `Format`)
  - `TooLong` — vượt quá `MAX_SLUG_LENGTH = 200`
  - `Format` — không đúng `^[a-z0-9]+(-[a-z0-9]+)*$` (có hoa, khoảng trắng, dấu, gạch đầu/cuối/đôi…)
- `generateDefaultSlug(source, taken)`: trả `normalizeSlug(source)`; nếu đã bị dùng thì thêm hậu tố nhỏ nhất `-2`, `-3`, … chưa dùng; trả `""` khi canonical rỗng.

### Form Show — `create_show.component`
- Control `slug` gắn 2 validator: `slugFormatValidator` (dùng `isValidSlug`) và `slugDuplicateValidator` (so canonical với slug của các show khác lấy từ `fetchAllShow()`).
- **Tự sinh theo tên sự kiện**: khi chưa có slug lưu và người dùng chưa sửa slug bằng tay, mỗi lần đổi tên show sẽ tự cập nhật slug = `generateDefaultSlug(showName, allShowSlugs)` (đặt bằng `setValue(..., { emitEvent: false })` để không bị coi là chỉnh tay).
- Khi mở show đã có slug → hiển thị slug đã lưu, không tự sinh.
- **Nhãn trạng thái** (badge cạnh label) qua getter `slugState`:
  - `auto` — đang tự sinh theo tên sự kiện
  - `manual` — slug đã lưu trước đó, chưa chỉnh
  - `editing` — người dùng đã gõ tay vào ô slug
- Submit: gửi giá trị slug **đúng như nhập** (chỉ trim), rỗng → không gán slug. Lỗi server (`slug` trong body) được surface qua `serverSlugError` mà vẫn giữ nguyên dữ liệu form.

### Form Session — `create_show_session.component`
- Tương tự form show, nhưng slug **tự sinh theo tên suất diễn** (`sessionName`) và kiểm tra trùng trong phạm vi cùng show (active sessions + drafts).
- Cùng badge `auto` / `manual` / `editing` (dùng cờ `slugHasStored` + `slugManuallyEdited`).
- Áp dụng format + uniqueness trước khi lưu draft; lỗi server được surface kèm slug bị từ chối.

### Luồng gateway/service
- `show.gateway.ts` / `metaticket.show.service.ts`: `createShow` / `updateShow` / `updateShowSession` truyền thêm `slug`.
- `admin.gateway.ts` / `metaticket.admin.service.ts`: `createShowSessionDraft` / `updateShowSessionDraft` truyền `slug` (lưu trên draft phía Admin_Service, promote sang session khi xuất bản).

> Server là nguồn chân lý cuối cùng: dù client đã validate, Show_Service vẫn validate lại nghiêm và đảm bảo uniqueness; client surface mọi lỗi `Invalid slug` / `Duplicate slug` trả về.
