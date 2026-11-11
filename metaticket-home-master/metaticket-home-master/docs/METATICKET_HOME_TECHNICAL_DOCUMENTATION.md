# METATICKET-HOME - TÀI LIỆU KỸ THUẬT

## TỔNG QUAN HỆ THỐNG

### Giới thiệu
MetaTicket-Home là ứng dụng web frontend chính của hệ thống bán vé sự kiện MetaTicket, được xây dựng bằng Angular 17+ với kiến trúc standalone components. Ứng dụng cung cấp giao diện người dùng để tìm kiếm, đặt vé và quản lý vé sự kiện.

### Công nghệ sử dụng
- **Framework**: Angular 17+ (Standalone Components)
- **UI Library**: Ng-Zorro (Ant Design for Angular)
- **Styling**: TailwindCSS + SCSS
- **State Management**: Custom Store Pattern với RxJS
- **HTTP Client**: Angular HttpClient
- **Build Tool**: Angular CLI
- **Package Manager**: Yarn

### Kiến trúc tổng quan
```
src/app/
├── lib/                    # Thư viện chung
│   ├── components/         # Components tái sử dụng
│   ├── services/          # Business services
│   ├── stores/            # State management
│   ├── entities/          # Data models
│   ├── constants/         # Hằng số
│   ├── pipes/             # Custom pipes
│   ├── guards/            # Route guards
│   └── utils/             # Utility functions
└── pages/                 # Page components
    ├── home/              # Trang chủ
    ├── event/             # Danh sách sự kiện
    ├── booking/           # Quy trình đặt vé
    ├── login/             # Đăng nhập
    ├── register/          # Đăng ký
    └── ...
```

## CẤU TRÚC COMPONENTS

### 1. App Component (app.component.ts)
**Chức năng**: Component gốc của ứng dụng

**Tính năng chính**:
- Khởi tạo ứng dụng và load external scripts
- Cấu hình meta tags cho SEO
- Quản lý router outlet

**Dependencies**:
- RouterOutlet: Hiển thị các page components
- Meta service: Quản lý meta tags

### 2. Layout Components

#### 2.1 LayoutComponent
**Chức năng**: Component layout chung cho toàn bộ ứng dụng

**Tính năng**:
- Cung cấp cấu trúc layout cơ bản
- Quản lý header, content, footer
- Responsive design

#### 2.2 NavbarComponent
**Chức năng**: Thanh điều hướng chính

**Tính năng**:
- Menu điều hướng
- User authentication status
- Search functionality
- Mobile responsive menu

#### 2.3 FooterComponent
**Chức năng**: Footer của website

**Tính năng**:
- Thông tin liên hệ
- Links chính sách
- Social media links

### 3. Home Module Components

#### 3.1 HomeComponent
**Chức năng**: Trang chủ chính

**Structure**:
```typescript
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [LayoutComponent, HomeHeaderComponent, HomeContentComponent]
})
export class HomeComponent
```

#### 3.2 HomeHeaderComponent
**Chức năng**: Header của trang chủ với banner slider

**Tính năng**:
- Hiển thị banner shows từ API
- Slider banner cho desktop và mobile
- Navigation menu

**Dependencies**:
- ShowService: Lấy dữ liệu banner shows
- SliderBannerComponent: Hiển thị slider

#### 3.3 HomeContentComponent
**Chức năng**: Nội dung chính trang chủ

**Tính năng**:
- Hiển thị danh sách sự kiện nổi bật
- Tìm kiếm sự kiện
- Filter theo category, location, time
- Grid layout cho events

**Dependencies**:
- ShowService: API calls
- HomeSearchService: Search state management
- GridEventTicketComponent: Hiển thị event items

### 4. Event Module Components

#### 4.1 EventComponent
**Chức năng**: Trang danh sách sự kiện

**Structure**:
```typescript
@Component({
  selector: 'app-event',
  imports: [LayoutComponent, EventHeaderComponent, EventContentComponent]
})
export class EventComponent
```

#### 4.2 GridEventTicketComponent
**Chức năng**: Component hiển thị thông tin sự kiện dạng grid

**Tính năng**:
- Hiển thị thông tin cơ bản của event
- Navigation đến trang chi tiết
- Format date và price
- Responsive grid layout

**Input Properties**:
- `events`: Danh sách sự kiện
- `loading`: Trạng thái loading

### 4.3 News Module Components

> **Tài liệu chi tiết:** [NEWS.md](./NEWS.md) — kiến trúc, API, Hybrid SSR.

#### 4.3.1 NewsHomeComponent
**Chức năng**: Trang danh sách tin tức (`/news`)

**Tính năng**:
- Hiển thị grid bài viết qua `NewsCardComponent`
- Phân trang offset: 9 tin đầu, **Xem thêm** +6
- Dữ liệu từ `NewsService.getNewsList()`

#### 4.3.2 NewsDetailComponent
**Chức năng**: Trang chi tiết bài viết (`/news/:slug`)

**Tính năng**:
- Resolve slug từ `ActivatedRoute`
- Render HTML qua `SafeHtmlPipe`
- Layout 70/30 (nội dung / sidebar)
- Bài quan tâm / cùng category + sự kiện highlight (`GridEventTicketComponent`)

#### 4.3.3 NewsCardComponent
**Chức năng**: Thẻ bài viết tái sử dụng (list, sidebar, related)

**Input**: `newsItem: NewsItem`

### 5. Booking Module Components

#### 5.1 BookingComponent
**Chức năng**: Container chính cho quy trình đặt vé

**Tính năng**:
- Multi-step booking process
- Breadcrumb navigation
- State management cho booking flow
- Session validation

**Booking Steps**:
1. Seat Selection (BookingSeatMapComponent)
2. Policy Description (BookingPolicyDescComponent)
3. Customer Information (BookingInformationComponent)
4. Payment (BookingPaymentComponent)
5. Completion (BookingCompletedComponent)

#### 5.2 BookingSeatMapComponent
**Chức năng**: Chọn ghế và vé

**Tính năng chính**:
- Tích hợp seat plan từ external library
- Real-time seat availability
- Ticket selection và quantity
- Price calculation
- Promotion code application
- Reservation creation

**Key Methods**:
```typescript
// Load tickets by time
loadTicketByTime(): void

// Handle seat selection events
onSeatPlanTrigger(event: SeatPlanTriggerEvent): void

// Create reservation
createReservation(): void

// Calculate tentative price
getTentativePrice(): void
```

**Dependencies**:
- TicketService: Ticket operations
- OrderService: Reservation management
- SeatPlanComponent: Seat visualization

#### 5.3 BookingPolicyDescComponent
**Chức năng**: Hiển thị chính sách và điều khoản

**Tính năng**:
- Hiển thị policy description cho từng loại vé
- Quantity adjustment
- Price recalculation
- Validation rules

#### 5.4 BookingInformationComponent
**Chức năng**: Thu thập thông tin khách hàng

**Tính năng**:
- Form validation
- Customer data collection
- Data collection fields (custom fields)
- Order creation

#### 5.5 BookingPaymentComponent
**Chức năng**: Xử lý thanh toán

**Tính năng**:
- Payment method selection
- Payment gateway integration
- Countdown timer
- Payment status tracking
- Order summary

**Key Features**:
```typescript
// Payment methods
listPaymentMethods = [
  { type: PaymentType.VNPAY, name: 'VNPay' },
  { type: PaymentType.MOMO, name: 'MoMo' },
  // ...
];

// Start payment process
startPayment(): void

// Check payment status
checkPaymentStatus(): void
```

#### 5.6 BookingCompletedComponent
**Chức năng**: Xác nhận hoàn thành đặt vé

**Tính năng**:
- Order confirmation
- Ticket information display
- Download/print options
- Navigation to ticket management

### 6. Authentication Components

#### 6.1 LoginUserComponent
**Chức năng**: Form đăng nhập

**Tính năng**:
- Username/password authentication
- Captcha integration
- Remember me functionality
- Social login options

#### 6.2 RegisterUserComponent
**Chức năng**: Form đăng ký

**Tính năng**:
- Multi-step registration
- OTP verification
- User information collection
- Terms acceptance

#### 6.3 OtpComponent
**Chức năng**: Xác thực OTP

**Tính năng**:
- OTP input interface
- Resend OTP functionality
- Timer countdown
- Auto-submit on complete

### 7. Form Components

#### 7.1 InputComponent
**Chức năng**: Input field tái sử dụng

**Features**:
- Validation integration
- Error message display
- Various input types
- Styling consistency

#### 7.2 FormInputComponent
**Chức năng**: Enhanced form input với validation

#### 7.3 FormSelectComponent
**Chức năng**: Select dropdown component

#### 7.4 FormCheckboxComponent
**Chức năng**: Checkbox component

### 8. UI Components

#### 8.1 ButtonComponent
**Chức năng**: Button component tái sử dụng

**Variants**:
- Primary, Secondary, Danger
- Loading states
- Icon support
- Size variations

#### 8.2 LoadingComponent
**Chức năng**: Loading indicator

#### 8.3 ImageComponent
**Chức năng**: Image component với lazy loading

#### 8.4 ModalComponent
**Chức năng**: Modal dialog component

### 9. Specialized Components

#### 9.1 SeatPlanComponent
**Chức năng**: Tích hợp seat plan visualization

**Tính năng**:
- External library integration
- Real-time seat status
- Interactive seat selection
- Custom styling
- Event handling

**Integration**:
```typescript
declare let metaticket: any; // External seat plan library

@Component({
  selector: 'seat-plan',
  template: '<div id="seat-plan-container"></div>'
})
export class SeatPlanComponent implements OnInit {
  @Output() seatPlanTrigger = new EventEmitter<SeatPlanTriggerEvent>();
  
  ngOnInit(): void {
    this.initializeSeatPlan();
  }
}
```

#### 9.2 CountdownTimerComponent
**Chức năng**: Đếm ngược thời gian

**Use Cases**:
- Payment timeout
- Session expiration
- Event countdown

#### 9.3 SliderBannerComponent
**Chức năng**: Banner slider cho desktop

#### 9.4 SliderBannerMobileComponent
**Chức năng**: Banner slider cho mobile

## SERVICES LAYER

### 1. Core Services

#### 1.1 HttpClientService
**Chức năng**: HTTP client wrapper

**Features**:
- Request/Response interceptors
- Error handling
- Loading states
- Authentication headers

#### 1.2 LocalStorageService
**Chức năng**: Local storage management

**Methods**:
```typescript
saveData(key: string, data: any): void
getData(key: string): any
removeData(key: string): void
saveBookingSession(data: BookingState): void
getBookingSession(): BookingState | null
```

#### 1.3 NavigationService
**Chức năng**: Navigation utilities

#### 1.4 NotificationService
**Chức năng**: Toast notifications

### 2. Business Services

#### 2.1 AuthService
**Chức năng**: Authentication management

**Key Features**:
- JWT token management
- Login/logout functionality
- User profile management
- Authentication state tracking
- Token refresh

**Methods**:
```typescript
login(credentials: LoginRequest): Observable<LoginResponse>
logout(): void
getToken(): string | null
getUserId(): string | null   // id user hiện tại (đọc từ claim `userId` của JWT); null khi chưa đăng nhập / token lỗi
isAuthenticated(): boolean
get userProfile$(): Observable<UserProfileModel | null>
refreshToken(): Observable<LoginResponse>
```

> **`getUserId()`** decode JWT và trả về id ổn định của user, hoặc `null` khi chưa đăng nhập hoặc token không hợp lệ. Dùng để **scope client state theo từng user** (ví dụ giỏ hàng accessory) sao cho state không bị chia sẻ nhầm giữa các tài khoản.
>
> **Lưu ý về claim:** JWT của khách hàng (do `service.customer.auth` `JWTUtil` phát hành) **không** đặt claim chuẩn `sub` mà mang id trong claim tùy chỉnh `userId` (`.withClaim("userId", userId)`). Vì vậy `getUserId()` đọc `payload.userId` (không phải `payload.sub`).

#### 2.2 ShowService
**Chức năng**: Event/Show management

**API Methods**:
```typescript
getShowDetail(showId: string): Observable<ShowResponse>
searchShowByFilter(body: SearchShowFilter): Observable<GetHighlightShowResponse>
getHighlightShow(body: HighlightShowRequest): Observable<GetHighlightShowResponse>
getTopBannerShow(): Observable<GetHighlightShowResponse>
searchShowByKeyword(param: {keyword: string, page: number, size: number}): Observable<GetHighlightShowResponse>
getEventCategory(): Observable<EventCategoryResponse[]>
getListCity(): Observable<CityResponse[]>
```

**Local Storage**:
```typescript
saveToLocalStorage(show: SearchShowResponse): void
getShowsFromLocalStorage(): SearchShowResponse[]
```

#### 2.3 TicketService
**Chức năng**: Ticket operations

**Key Methods**:
```typescript
getAllTicket(body: GetAllTicketRequest): Observable<TicketsWithVersionResponse>
getTicketByTime(body: GetTicketByTimeRequest): Observable<TicketsWithVersionResponse>
createReservation(body: CreateReservationRequest): Observable<CreateReservationResponse>
reserveReservation(reservationId: string): Observable<any>
getSingleReservation(reservationId: string): Observable<GetSingleReservationResponse>
releaseReservation(reservationId: string): Observable<any>
```

#### 2.4 PaymentService
**Chức năng**: Payment processing

**Methods**:
```typescript
startPayment(body: StartPaymentRequest): Observable<string>
getPaymentStatus(paymentId: string): Observable<PaymentStatusResponse>
updatePaymentResultFromReturnUrl(data: string): Observable<PaymentRedirectResponse>
```

#### 2.5 OrderService
**Chức năng**: Order management

#### 2.6 UserService
**Chức năng**: User profile management

### 3. Store Services (State Management)

#### 3.1 BookingService & BookingStore
**Chức năng**: Quản lý state cho booking flow

**State Structure**:
```typescript
interface BookingState {
  showId: string;
  sessionId: string;
  currentIndex: number;
  reservationId?: string;
  orderId?: string;
  paymentId?: string;
  listSelectedTickets: SeatPlanTriggerItem[];
  tentativePrice: TentativePriceResponse;
  promotionCode?: string;
}
```

**Key Methods**:
```typescript
initial(state: BookingState): void
bookingSeatMap(reservationId: string, listTickets: SeatPlanTriggerItem[], tentativePrice: TentativePriceResponse, promotionCode: string): void
bookingInformation({orderId, code, paymentId}): void
startPayment(): void
bookingCompleted(): void
expireBookingSession(): void
resetPaymentState(): void
```

#### 3.2 HomeSearchService
**Chức năng**: Quản lý state cho tìm kiếm trang chủ

## ROUTING SYSTEM

### Route Configuration
```typescript
export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'su-kien', component: EventComponent },
  { path: 'dat-ve', component: BookingComponent, canActivate: [authGuard] },
  { path: 'show/:id', component: EventDetailComponent },
  { path: 'dang-nhap', component: LoginComponent },
  { path: 'dang-ky', component: RegisterComponent },
  { path: 'quan-ly-ve', component: TicketManagerComponent, canActivate: [authGuard] },
  { path: 'payment-result/:gateway/:code/:pD/:pC', component: PaymentResultComponent },
  // ... other routes
];
```

### Route Guards

#### AuthGuard
**Chức năng**: Bảo vệ routes yêu cầu authentication

```typescript
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  
  if (authService.isAuthenticated()) {
    return true;
  } else {
    router.navigate(['/dang-nhap']);
    return false;
  }
};
```

## DATA MODELS & ENTITIES

### Request Models
```typescript
// Authentication
interface LoginRequest {
  username: string;
  password: string;
  captchaToken?: string;
}

// Show/Event
interface SearchShowFilter {
  page: number;
  size: number;
  isFree?: boolean;
  eventCategoryCode?: string;
  startTime?: string;
  endTime?: string;
  cities?: string[];
}

// Ticket
interface CreateReservationRequest {
  sessionId: string;
  tickets: CreateReservationTicketRequest[];
  promotionCode?: string;
}

// Payment
interface StartPaymentRequest {
  orderId: string;
  paymentType: PaymentType;
  returnUrl: string;
}
```

### Response Models
```typescript
// Authentication
interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

// Show/Event
interface SearchShowResponse {
  id: string;
  name: string;
  description: string;
  imageUrl: string;
  startTime: string;
  endTime: string;
  venue: string;
  city: string;
  minPrice: number;
  maxPrice: number;
}

// Ticket
interface TicketResponse {
  sid: string;
  name: string;
  description: string;
  price: number;
  quantity: number;
  maxQuantityPerOrder: number;
}
```

## CONSTANTS & CONFIGURATION

### API URLs
```typescript
export const apiUrl = {
  // Authentication
  login: '/auth/login',
  refreshToken: '/auth/refresh',
  
  // Show
  getShowDetail: '/show/detail',
  getShowFilter: '/show/filter',
  getHighlightShow: '/show/highlight',
  
  // Ticket
  getAllTicket: '/ticket/all',
  createReservation: '/ticket/reservation',
  
  // Payment
  startPayment: '/payment/start',
  getPaymentStatus: '/payment/status',
};
```

### Route Names
```typescript
export const routeName = {
  root: '',
  event: 'su-kien',
  booking: 'dat-ve',
  login: 'dang-nhap',
  register: 'dang-ky',
  ticketManager: 'quan-ly-ve',
  // accessory storefront ("Sản phẩm")
  product: 'san-pham',                 // product list
  productDetail: 'san-pham',           // detail uses /san-pham/:showCode
  productCheckout: 'san-pham/thanh-toan', // multi-step checkout container
  // ...
};
```

## PIPES & UTILITIES

### Custom Pipes

#### FormatPricePipe
**Chức năng**: Format giá tiền theo định dạng VND

```typescript
@Pipe({ name: 'formatPrice' })
export class FormatPricePipe implements PipeTransform {
  transform(value: number): string {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND'
    }).format(value);
  }
}
```

#### SecondsToTimePipe
**Chức năng**: Convert seconds thành format thời gian

#### NearestSessionDatePipe
**Chức năng**: Từ danh sách session của một show, trả về ngày (đã định dạng) của session có thời gian bắt đầu gần thời điểm hiện tại nhất, chỉ xét các session `showInHome = true`.

**Vị trí**: `lib/pipes/show-sessions/nearest-session-date.pipe.ts` (standalone pipe, `name: 'nearestSessionDate'`).

**Hành vi**:
- Bỏ qua các session có `showInHome = false`.
- Nếu không còn session nào hiển thị ở trang chủ, trả về chuỗi `' '` (một khoảng trắng).
- Ngược lại, chọn session có `startTime` gần `Date.now()` nhất rồi định dạng qua `formatDate`.

```typescript
@Pipe({ name: 'nearestSessionDate', standalone: true })
export class NearestSessionDatePipe implements PipeTransform {
  transform(sessions: SearchSessionResponse[] | null | undefined): string
}
```

> Pipe này tách logic vốn nằm inline trong `GridEventTicketComponent.getDateSessionNearest()` thành dạng tái sử dụng được, giữ nguyên hành vi hiển thị.

#### HasVisibleSessionsPipe
**Chức năng**: Kiểm tra một show có ít nhất một session hiển thị ở trang chủ (`showInHome = true`) hay không.

**Vị trí**: `lib/pipes/show-sessions/has-visible-sessions.pipe.ts` (standalone pipe, `name: 'hasVisibleSessions'`).

**Hành vi**:
- Trả về `true` nếu có bất kỳ session nào có `showInHome = true`.
- Danh sách rỗng, `null` hoặc `undefined` → trả về `false`.

```typescript
@Pipe({ name: 'hasVisibleSessions', standalone: true })
export class HasVisibleSessionsPipe implements PipeTransform {
  transform(sessions: SearchSessionResponse[] | null | undefined): boolean
}
```

> Pipe này tách logic vốn nằm inline trong `GridEventTicketComponent.hasVisibleSessions()` thành dạng tái sử dụng được, dùng chung với `NearestSessionDatePipe` trong template của grid sự kiện.

> **Ghi chú về `GridEventTicketComponent`**: Component này đã chuyển sang `ChangeDetectionStrategy.OnPush` và dùng `trackByShowId` cho `*ngFor`. Hai method `hasVisibleSessions()` / `getDateSessionNearest()` không còn nằm trong component mà được thay bằng `HasVisibleSessionsPipe` / `NearestSessionDatePipe` gọi trực tiếp trong template.

### Utility Functions
```typescript
// Date formatting
export function formatDate(date: string | Date): string

// Time truncation
export function truncateToNearestXMinutes(date: Date, minutes: number): Date
```

## INTERCEPTORS

### AuthInterceptor
**Chức năng**: Tự động thêm authentication headers

```typescript
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.authService.getToken();
    
    if (token) {
      const authReq = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
      return next.handle(authReq);
    }
    
    return next.handle(req);
  }
}
```

## STYLING & THEMING

### TailwindCSS Configuration
- Responsive design utilities
- Custom color palette
- Component-specific styles

### SCSS Modules
- Component-scoped styles
- Mixins và variables
- Theme customization

### Ng-Zorro Integration
- Ant Design components
- Custom theme configuration
- Icon library

## PERFORMANCE OPTIMIZATION

### Lazy Loading
- Route-based code splitting
- Component lazy loading
- Image lazy loading

### Change Detection
- OnPush strategy cho performance components
- Immutable data patterns
- RxJS optimization

### Bundle Optimization
- Tree shaking
- Code splitting
- Asset optimization

## SECURITY

### Authentication
- JWT token management
- Automatic token refresh
- Secure storage practices

### Route Protection
- Authentication guards
- Role-based access control
- Redirect handling

### XSS Prevention
- Angular's built-in sanitization
- Safe HTML rendering
- Input validation

## TESTING

### Unit Testing
- Jasmine + Karma setup
- Component testing
- Service testing
- Pipe testing

### E2E Testing
- Protractor/Cypress setup
- User flow testing
- Cross-browser testing

## DEPLOYMENT

### Build Configuration
```json
{
  "scripts": {
    "build:dev": "ng build --configuration=development",
    "build:staging": "ng build --configuration=staging",
    "build:prod": "ng build --configuration=production"
  }
}
```

### Environment Configuration
```typescript
// environment.ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  metaDomain: 'http://localhost:3000'
};
```

### Docker Configuration
- Multi-stage build
- Nginx serving
- Environment variable injection

## TROUBLESHOOTING

### Common Issues

1. **Seat Plan Integration**
   - Ensure external library is loaded
   - Check DOM element availability
   - Verify event binding

2. **Authentication Issues**
   - Token expiration handling
   - Refresh token flow
   - CORS configuration

3. **Payment Integration**
   - Gateway configuration
   - Return URL handling
   - Status polling

### Debug Tools
- Angular DevTools
- Redux DevTools (for state)
- Network monitoring
- Console logging

## KẾT LUẬN

MetaTicket-Home là một ứng dụng Angular hiện đại với kiến trúc standalone components, cung cấp trải nghiệm người dùng mượt mà cho việc đặt vé sự kiện. Hệ thống được thiết kế với tính mở rộng cao, bảo mật tốt và performance được tối ưu hóa.

### Điểm mạnh
- Kiến trúc modular và scalable
- UI/UX hiện đại với Ng-Zorro
- State management hiệu quả
- Security practices tốt
- Performance optimization

### Hướng phát triển
- PWA implementation
- Advanced caching strategies
- Real-time notifications
- Enhanced analytics
- Mobile app development

## SLUG LINKS (Đường dẫn thân thiện)

Homepage hỗ trợ mở show và booking bằng link slug thân thiện, song song với route cũ theo id/query (route cũ giữ nguyên, không đổi).

### Routes
Khai báo trong `app.routes.ts` với tiền tố `routeName.slugPrefix = 's'`:

| Path | Component | Ghi chú |
|------|-----------|---------|
| `s/:showSlug` | `EventDetailComponent` | Trang chi tiết show theo slug |
| `s/:showSlug/:sessionSlug` | `BookingComponent` | Trang đặt vé theo slug |
| `show/:id`, `dat-ve?show=&session=` | (giữ nguyên) | Route id/query cũ vẫn hoạt động |

### Resolve qua Fetch_By_Slug_Endpoint
- `ShowService.getShowBySlug(showSlug, sessionSlug?)` gọi `GET public/show/fetch/slug?showSlug=&sessionSlug=`.
- Response là `ShowResponse` (phẳng) kèm `matchedSessionId?` — kiểu `ShowBySlugResponse = ShowResponse & { matchedSessionId?: string }`. `sessions[]` mỗi phần tử có `slug`.
- **Client KHÔNG normalize slug**: segment trên URL được gửi nguyên (raw) cho server. Show_Service tự chuẩn hóa (`SlugNormalizer.normalize`) showSlug/sessionSlug trước khi tra cứu, nên link hoa/không canonical vẫn resolve đúng. (Trước đây có `lib/utils/slug.util.ts` ở client nhưng đã bỏ vì thừa.)

### EventDetailComponent (slug mode)
- Phát hiện `params['showSlug']` (so với `params['id']` của route cũ); gọi `getShowBySlug(showSlug)`.
- Loading state khi đang resolve, timeout 10s.
- `404` → trang not-found. Lỗi khác/timeout → error state, không render một phần; route id vẫn dùng được.

### BookingComponent (slug mode)
- Phát hiện `params['showSlug']`/`params['sessionSlug']`; gọi `getShowBySlug(showSlug, sessionSlug)`.
- Gán `showId = res.id`, `sessionId = res.matchedSessionId ?? ''` rồi chạy lại `_getShowDetail()` y như luồng query-param cũ → state booking giống hệt.
- Không có `matchedSessionId` (session-slug không khớp) → not-found, không mở booking.
- `404` → not-found; lỗi khác/timeout → error state, giữ nguyên URL.

### Nút "Đặt vé"
`event-detail.component` chọn link qua `buildBookingTarget(show, session)`:
- Cả `show.slug` và `session.slug` đều có → điều hướng `/s/{show-slug}/{session-slug}`.
- Ngược lại → dùng route cũ `/dat-ve?show={showId}&session={sessionId}`.

Hai cách đều mở đúng cùng một show/session.

### Entities
`show.response.ts`: `ShowResponse.slug: string`, `SessionResponse.slug: string` (luôn có; rỗng `""` khi không có slug), và `ShowBySlugResponse = ShowResponse & { matchedSessionId?: string }`.

## ACCESSORY STOREFRONT ("Sản phẩm")

Storefront bán sản phẩm/phụ kiện đi kèm một "accessory show" (áo, poster, lightstick...), thêm vào `metaticket-home` bên cạnh luồng đặt vé. Đây là **luồng độc lập** với booking: catalog, đơn hàng, mã giảm giá và email xác nhận riêng, nhưng tái sử dụng look-and-feel và payment rails hiện có. Phạm vi hiện tại: storefront + luồng mua ("Bước 7"), **chỉ nhận hàng tại show (pickup-at-show)**, không giao hàng.

Giỏ hàng là **thuần frontend** (client store + `localStorage`), backend không lưu giỏ. Toàn bộ dòng hàng được gửi một lần trong body của checkout; backend (`service.accessory`) validate và định giá trực tiếp từ payload. Frontend gọi backend qua nginx path-routing tiền tố `public/accessory/*`, giống cách gọi `service.show`/`service.order`.

### Routes

Khai báo trong `app.routes.ts` (lazy-loaded), dùng `routeName`. **Hai route duyệt sản phẩm và chi tiết là public**; **route checkout yêu cầu đăng nhập** (gắn `authGuard`). Giỏ hàng vẫn nằm hoàn toàn ở client store, nhưng để tiến hành thanh toán, khách phải đăng nhập:

| Path | Component | Auth | Ghi chú |
|------|-----------|------|---------|
| `san-pham` | `ProductListComponent` | public | Danh sách accessory show đang mở + sản phẩm |
| `san-pham/:showCode` | `ProductDetailComponent` | public | Lưới sản phẩm của **một** accessory show; chọn sản phẩm mở popup chọn biến thể/size + thêm giỏ |
| `san-pham/thanh-toan` | `AccessoryCheckoutComponent` | `authGuard` | Container checkout nhiều bước; **yêu cầu đăng nhập** (`canActivate: [authGuard]` trong `app.routes.ts`) — khách chưa đăng nhập bị điều hướng về `/dang-nhap` |

`routeName`: `product = 'san-pham'`, `productDetail = 'san-pham'`, `productCheckout = 'san-pham/thanh-toan'`. Menu "Sản phẩm" được thêm trên `NavbarComponent` cạnh "Sự kiện".

> **Lưu ý về thứ tự khai báo**: route tĩnh `san-pham/thanh-toan` (`productCheckout`) phải được khai báo **trước** route động `san-pham/:showCode` (`productDetail`) để `thanh-toan` không bị bắt nhầm thành `:showCode`.

### API URLs (`lib/constants/api-url.ts`)

```typescript
getAccessoryShows:    'public/accessory/show/fetch/all',
getAccessoryShow:     'public/accessory/show/fetch',
getAccessory:         'public/accessory/fetch',
validateDiscount:     'accessory/discount/validate',
accessoryCheckout:    'accessory/checkout',
getAccessoryOrder:    'public/accessory/order/fetch',
cancelAccessoryOrder: 'accessory/order/cancel',
```

> **Lưu ý về `public/accessory/fetch` (`getAccessory`)**: endpoint đọc catalog trả về **một** sản phẩm đang `active` kèm tồn kho hiện thời (tồn kho top-level cho sản phẩm single-SKU, tồn kho theo từng biến thể cho sản phẩm có biến thể) và danh sách biến thể của nó. Nó phục vụ **poll tồn kho của popup chi tiết sản phẩm**: khi khách mở một sản phẩm, popup refresh (và poll trong lúc mở) endpoint này để tình trạng còn/hết hàng — và gating "hết hàng ⇒ không thể mua" — phản ánh catalog trực tiếp thay vì snapshot lúc tải danh sách show. Id truyền vào là id đã `Checksum` mã hóa; id thiếu/không hợp lệ ⇒ `400`, id hợp lệ nhưng không khớp sản phẩm active nào (vắng hoặc inactive) ⇒ `404`.

> **Lưu ý về cache của hai endpoint đọc tồn kho (`public/accessory/fetch` và `public/accessory/show/fetch`)**: `service.accessory` trả header `Cache-Control: public, max-age=5, stale-while-revalidate=10` cho hai endpoint này, để Cloudflare/trình duyệt gộp các lần poll tồn kho dày đặc của storefront thành ít lần chạm origin hơn (`stale-while-revalidate` phục vụ giá trị cũ tức thì trong lúc nền lấy giá trị mới). Vì vậy tồn kho hiển thị có thể **cũ tối đa vài giây** — điều này an toàn vì tồn kho ở đây chỉ là gợi ý UX; việc chống bán quá số lượng được đảm bảo dứt điểm ở bước checkout (reserve-on-create). Các endpoint đọc còn lại (`show/fetch/all`, `order/fetch`) không đặt cache này.

> **Lưu ý về xác thực ở `accessory/checkout`, `accessory/discount/validate` và `accessory/order/cancel`**: khác với các endpoint đọc catalog, **cả ba** endpoint checkout, preview mã giảm giá và hủy đơn đều **yêu cầu khách đã đăng nhập** — điều này được phản ánh ngay ở đường dẫn: chúng dùng tiền tố `accessory/*` (**không** có `public/`), trong khi các endpoint đọc catalog vẫn giữ tiền tố `public/accessory/*`. Gateway route các đường dẫn không `public/` này qua tầng xác thực, chèn header `X-Customer-User-Id` sau khi xác thực; nếu header vắng (khách chưa đăng nhập), `service.accessory` từ chối với `401` ("Không xác định người dùng") trước khi xử lý bất kỳ tác vụ nào (checkout không tạo đơn, preview không đánh giá mã, cancel không hủy đơn) — tương tự `createOrder` của `service.order`. Điều này khớp với `authGuard` trên route `san-pham/thanh-toan` ở frontend: đăng nhập được kiểm tra cả ở client (điều hướng về `/dang-nhap`) lẫn ở backend (`401`). Chỉ các endpoint **đọc catalog** (`public/accessory/show/fetch/all`, `public/accessory/show/fetch`, `public/accessory/fetch`, `public/accessory/order/fetch`) mới còn ẩn danh.

> **Lưu ý về `accessory/order/cancel` (`cancelAccessoryOrder`)**: `POST accessory/order/cancel?id=` cho phép khách **tự hủy đơn của mình ở giai đoạn trước thanh toán** (nút "Hủy đơn" ở bước thanh toán). Id truyền vào là id đơn đã `Checksum` mã hóa. Khi thành công, `service.accessory` thực hiện chuyển trạng thái có guard sang `PaymentCancelled` và **giải phóng tồn kho + mã giảm giá đã giữ** đúng một lần (mô hình reserve-on-create). Id thiếu/không hợp lệ hoặc không khớp đơn nào ⇒ `404`; đơn đã qua cửa sổ hủy được phép (thanh toán đang xử lý hoặc đã ở trạng thái terminal) ⇒ `409`.
>
> **Phân quyền theo chủ sở hữu (object-level authorization)**: ngoài yêu cầu đăng nhập, hủy đơn còn được kiểm tra **quyền sở hữu**. Mỗi đơn ghi lại id của khách đã đặt (`AccessoryOrderDocument.customerUserId`, lấy từ header `X-Customer-User-Id` lúc checkout); chỉ **đúng chủ đơn** mới được hủy. Nếu id khách gọi không khớp chủ đơn ⇒ `403` (`NotOrderOwner`, "you are not allowed to cancel this order") và **không thay đổi gì** (không chuyển trạng thái, không giải phóng tồn kho/mã giảm giá). Đơn cũ được tạo trước khi tính năng này ra đời (`customerUserId == null`) **không thể hủy** qua endpoint này — chúng chỉ được giải phóng bởi vòng quét TTL (expiry sweep). Việc quét thứ tự kiểm tra là: giải mã id ⇒ tải đơn (`404` nếu thiếu/không khớp) ⇒ kiểm tra chủ sở hữu (`403`) ⇒ guard trạng thái (`409`).

### AccessoryService (`lib/services/accessory`)

Client HTTP tới `service.accessory` (qua `public/accessory/*`), dùng `HttpClientService`. Không đụng tới giỏ hàng (giỏ ở client store); chỉ cover đọc catalog + preview giảm giá + checkout + xem đơn.

```typescript
getAccessoryShows(): Observable<AccessoryShowResponse[]>
getAccessoryShow(showId: string): Observable<AccessoryShowResponse>
getAccessory(accessoryId: string): Observable<AccessoryResponse>
validateDiscount(body: ValidateDiscountRequest): Observable<ValidateDiscountResponse>
checkout(body: AccessoryCheckoutRequest): Observable<AccessoryCheckoutResponse>
getAccessoryOrder(encodedOrderId: string): Observable<AccessoryOrderResponse>
cancelAccessoryOrder(encodedOrderId: string): Observable<void>
```

> **Lưu ý về `getAccessory()`**: gọi endpoint chi tiết một sản phẩm (`public/accessory/fetch`) để lấy tồn kho **trực tiếp** của một `AccessoryResponse`. `ProductModalComponent` dùng nó cho vòng poll tồn kho (`merge(timer, visibilitychange)` + `switchMap`, `catchError` giữ nguyên snapshot cũ khi lỗi) nên tình trạng còn/hết hàng và gating add-to-cart luôn phản ánh catalog hiện thời trong lúc popup mở. Để giảm tải server khi có nhiều người dùng đồng thời, vòng poll được **gate bằng Page Visibility API** (`STOCK_POLL_INTERVAL_MS = 20s`): các tick khi tab đang ẩn (`document.hidden`) bị bỏ qua, và khi khách quay lại tab (`visibilitychange`) popup refresh tồn kho ngay lập tức.

> **Lưu ý về listing vs. detail**: `getAccessoryShows()` (`show/fetch/all`) chỉ trả **tóm tắt show** (không kèm `accessories`); danh sách sản phẩm chỉ có ở endpoint chi tiết theo show `getAccessoryShow()` (`show/fetch`). Vì vậy `ProductListComponent` sau khi lấy danh sách show sẽ **hydrate** từng show đang mở bằng cách gọi `getAccessoryShow(show.id)` (qua `switchMap` + `forkJoin`) để có `accessories` cho lưới sản phẩm. Nếu detail của một show lỗi, component `catchError` giữ lại tóm tắt show đó với `accessories: []` thay vì làm hỏng cả trang; chỉ khi lấy danh sách show ban đầu lỗi mới hiển thị trạng thái lỗi toàn trang.

> **Lưu ý về refresh tồn kho khi mở giỏ (`ProductDetailComponent`)**: khi khách mở popup giỏ hàng trên trang chi tiết show (`onOpenCart()`), component gọi lại `getAccessoryShow(show.id)` **ngầm** (nền) để nạp lại show cùng tồn kho hiện thời (top-level cho single-SKU, theo từng biến thể cho sản phẩm có biến thể) trước khi validate giỏ, thay vì dựa vào snapshot lúc tải trang. Lần refresh này **không** bật trạng thái loading/lỗi của trang; nếu lỗi thì giữ nguyên dữ liệu show đang có (backend vẫn re-check tồn kho ở bước checkout). Điều này bổ sung cho vòng poll `getAccessory()` của `ProductModalComponent`: popup sản phẩm poll tồn kho **một sản phẩm** khi đang mở, còn mở giỏ thì làm tươi tồn kho của **cả show**.

### Entities (`lib/entities/response/accessory/accessory.response.ts`)

Các response shape phản ánh public projection của backend data model (`AccessoryShowDocument`, `AccessoryDocument`, `AccessoryVariant`). Id là id đã được `Checksum` mã hóa; ObjectId thô của MongoDB không bao giờ rời service.

- **`AccessoryShowResponse`** — một accessory show (chỉ trả khi `status = 'Open'`). Gồm `id`, `code`, `name`, `description?`, `bannerImageUrl?`, `status?`, `pickupNote?`, `allowedPaymentMethods?` (tên enum `PaymentType`; rỗng/vắng ⇒ hiển thị tất cả phương thức), `accessories?` (có ở endpoint chi tiết), và `sessions?` — danh sách buổi diễn hiển thị trên **banner** storefront.
- **`AccessoryShowSessionResponse`** — một buổi diễn (venue/ngày/thành phố) của accessory show, hiển thị trên banner: `id` (id ổn định của buổi diễn; storefront gửi lại giá trị này ở bước checkout dưới dạng `showSessionId` để ghi nhận khách mua cho buổi diễn nào — session-selection), `city` (ví dụ `"TP.HCM"`), `venue` (ví dụ `"Nhà thi đấu Rạch Miễu"`), `date` (epoch millis, khớp với cách xử lý ngày của toàn app).
- **`AccessoryResponse`** — một sản phẩm: `id` (dùng làm `accessoryId` của cart line), `sku`, `name`, `description?`, `imageUrl?` (ảnh chính — dùng cho thẻ sản phẩm trong lưới và thumbnail dòng giỏ hàng), `imageUrls?` (gallery nhiều ảnh cho popup chi tiết sản phẩm; rỗng/vắng ⇒ storefront fallback về `imageUrl`; mỗi phần tử là một Image_Source: URL tuyệt đối hoặc data URI), `price` (VND), `currency?`, `stock` (chỉ dùng khi `variants` rỗng; `-1` = không giới hạn), `variants`, `active`.
- **`AccessoryVariantResponse`** — một biến thể (một tổ hợp thuộc tính có thể mua): `key` (id ổn định trong sản phẩm, ví dụ `"M-BLACK"`), `label` (nhãn hiển thị gộp, ví dụ `"Size M · Đen"`), `stock` (`-1` = không giới hạn), hai chiều thuộc tính **tùy chọn** `size?` (ví dụ `"M"`) và `color?` (ví dụ `"Đen"`), và `active` (biến thể có đang được bán không). Khi có `size`/`color`, storefront render các bộ chọn độc lập (ví dụ chọn size + màu) rồi resolve tổ hợp đã chọn về đúng `key` của biến thể; biến thể một chiều cũ để `size`/`color` là `null`. `active = false` nghĩa là tổ hợp đó đã bị rút khỏi bán (mirror cờ `active` cấp sản phẩm): storefront ẩn/vô hiệu hóa biến thể đó và backend từ chối nó khi checkout, cho phép ngừng bán một size/màu mà không ảnh hưởng các biến thể còn lại. Vắng/biến thể seed trước ⇒ mặc định `true` (vẫn mua được).

### Stores (State Management)

#### AccessoryCartStore / AccessoryCartService / AccessoryCartQuery (`lib/stores/accessory-cart`)

Giỏ hàng **thuần client** (Req 3). Lưu trong Akita store và persist vào `localStorage` mỗi lần thay đổi, không gọi backend. Giỏ được scope theo **một** accessory show (`accessoryShowId`); thêm dòng từ show khác sẽ thay giỏ. Nhận diện dòng hàng theo `(accessoryId, variantKey)`.

Ngoài ra giỏ mang tùy chọn `showSessionId` — buổi diễn (session) mà khách mua sản phẩm cho (session-selection); giá trị này được gửi kèm ở bước checkout và được reset khi giỏ chuyển sang show khác.

> **Giỏ được scope theo từng user.** `AccessoryCartService` phụ thuộc `AuthService`: key `localStorage` được hậu tố bằng id user đang đăng nhập (`AuthService.getUserId()`) dưới dạng `` `${storageKeys.accessoryCart}:${userId}` ``, nên hai tài khoản khác nhau **không dùng chung giỏ** trên cùng trình duyệt. Service subscribe `AuthService.authStatus$` và mỗi khi user thay đổi (đăng nhập / đăng xuất / đổi tài khoản) sẽ **xóa giỏ trong bộ nhớ rồi nạp lại giỏ của user hiện tại** (`restore()`). Khi chưa đăng nhập (`getUserId()` trả `null`) không có key nào để ghi nên service **không persist** — thêm vào giỏ yêu cầu đăng nhập.

```typescript
interface CartLine {
  accessoryId: string;
  variantKey?: string;   // có iff sản phẩm có biến thể; bỏ với single-SKU
  quantity: number;      // 1..99
  // snapshot hiển thị (name/variantLabel/unitPrice/image) — không phải giá trị
  // authoritative; backend re-derive giá từ catalog
}
interface Cart {
  accessoryShowId: string;
  showSessionId?: string; // buổi diễn khách mua cho; gửi ở checkout, reset khi đổi show
  lines: CartLine[];
}

export const ACCESSORY_CART_MIN_QUANTITY = 1;
export const ACCESSORY_CART_MAX_QUANTITY = 99;
```

`AccessoryCartService` (kết quả trả về `AccessoryCartResult` để UI hiển thị phản hồi):
- `restore()` — khôi phục giỏ từ `localStorage` khi tải storefront (Req 3.7).
- `addLine(line, accessoryShowId)` — thêm/gộp dòng; quantity < 1 nâng lên 1, > 99 bị từ chối và giữ nguyên (Req 3.3, 3.6).
- `updateQuantity(accessoryId, variantKey, quantity)` — quantity < 1 xóa dòng (Req 3.5), > 99 bị từ chối.
- `removeLine(accessoryId, variantKey?)`, `clear()` — xóa giỏ + xóa bản lưu ở `localStorage` (Req 3.4).

#### AccessoryCheckoutStore / AccessoryCheckoutService / AccessoryCheckoutQuery (`lib/stores/accessory-checkout`)

State của container checkout nhiều bước (Điền thông tin → Thanh toán → Hoàn tất), tương tự `BookingState`. **Chỉ** giữ step index, mã giảm giá đã preview, thông tin khách, encoded id trả về từ checkout, `ownerUserId` (chủ đơn), `expiresAt` (deadline hiển thị) và `checkoutEntered` (cờ đã vào luồng checkout) — dòng hàng vẫn nằm ở `AccessoryCartStore`.

> **Chủ đơn (`ownerUserId`)**: id của khách đăng nhập đã tạo đơn (đọc từ claim `userId` của JWT qua `AuthService.getUserId()`), được ghi tại `checkoutCompleted(...)`; rỗng khi chưa có đơn. Dùng để **scope resume/lock theo đúng chủ đơn** — một đơn đang chờ chỉ được resume/khóa cho chính tài khoản đã tạo nó, nên khách đăng xuất hoặc một tài khoản khác trên cùng trình duyệt **không** bị kéo vào (hay kẹt trong) đơn checkout đang dở của người khác. Nhất quán với cách `AccessoryCartService` scope giỏ hàng theo user (xem mục `AuthService.getUserId()`).
>
> **Điểm thực thi khi resume (`AccessoryCheckoutComponent.ngOnInit`)**: ngay sau `restore()`, container so `ownerUserId` đã khôi phục với `AuthService.getUserId()` hiện tại. Nếu state có `ownerUserId` **khác** user hiện tại (đơn do tài khoản khác để lại), container gọi `AccessoryCheckoutService.clear()` để **bỏ luồng đã lưu**, đảm bảo user hiện tại bắt đầu sạch và **không rò rỉ** dữ liệu của khách trước (đơn, tên/điện thoại/email). Khách chưa đăng nhập không thể tới route này (`authGuard`), nên chỉ so khớp giữa các tài khoản đã đăng nhập.

> **Cờ `checkoutEntered` (đã vào luồng checkout)**: `true` một khi khách **thực sự bước vào luồng checkout** (đến bước Điền thông tin trở đi), `false` khi khách mới chỉ đang duyệt / preview mã giảm giá trong popup giỏ hàng. Nó được đặt `true` tại các bước tiến của `AccessoryCheckoutService` (ví dụ khi vào bước Điền thông tin và tại `checkoutCompleted(...)`), và reset về `false` khi luồng bị `clear()`/reset **hoặc** khi khách **chủ động rời** bước Điền thông tin bằng nút "Back" (khi chưa có đơn) qua `leaveCheckoutFlow()` (xem dưới). Cờ này là điều kiện để `AppComponent` tự resume checkout khi tải lại app (xem dưới), sao cho một khách chỉ preview mã giảm giá — hoặc đã bấm "Back" thoát bước Điền thông tin — không bị kéo vào trang checkout.

> **TTL hiển thị của đơn (`expiresAt`, đếm ngược checkout)**: state mang thêm `expiresAt` (epoch millis; `0` = không có đơn đang chờ). Nó được đặt tại `checkoutCompleted(...)` thành `Date.now() + accessoryOrderTtlMinutes * 60_000`, với hằng số `accessoryOrderTtlMinutes` (hiện `5`, khai báo trong `lib/constants/app-constants.ts`) **mirror** config HOCON `accessory.orderTtlMinutes` của backend. Đây là giá trị **chỉ để hiển thị**: nó lái đồng hồ đếm ngược ở các bước Thanh toán/Hoàn tất và được persist cùng state (`AccessoryCheckoutQuery.expiresAt$`) nên tải lại trang vẫn giữ đúng thời gian còn lại. Khi đếm ngược về 0, frontend **không** tự hết hạn đơn — nó re-check trạng thái đơn authoritative từ server (`GET public/accessory/order/fetch`); việc thực sự hết hạn đơn do **sweep phía backend** đảm nhận. Độ trễ mạng chỉ khiến deadline phía client trễ hơn server một chút, an toàn vì lần re-check khi về 0 mới là nguồn sự thật.

**Persist vào `localStorage`**: state của luồng checkout được mirror sang `localStorage` dưới key `storageKeys.accessoryCheckout` (`'ACCESSORY_CHECKOUT'`, khai báo trong `lib/constants/storage-keys.ts`) mỗi khi thay đổi, để một lần **tải lại trang** khôi phục đúng bước đang dở (Điền thông tin → Thanh toán → Hoàn tất). Các method liên quan trên `AccessoryCheckoutService`:
- `restore()` — nạp lại state đã lưu vào store sau khi reload.
- `clear()` — xóa bản lưu ở `localStorage` và reset store; gọi khi khách xác nhận Hoàn tất (hoặc khi reset luồng), và khi `AccessoryCheckoutComponent.ngOnInit` phát hiện luồng đã lưu thuộc **chủ đơn khác** (`ownerUserId` không khớp user hiện tại) để tránh rò rỉ dữ liệu giữa các tài khoản.
- `leaveCheckoutFlow()` — đặt `checkoutEntered = false` và persist lại, **không** xóa state; gọi khi khách bấm "Back" rời bước Điền thông tin **lúc chưa tạo đơn** (`AccessoryInformationComponent.onGoBack()`), để lần mở lại homepage sau đó không ép khách quay về checkout. Giỏ hàng, mã giảm giá đã preview và thông tin khách đã nhập được **giữ nguyên** để khách có thể tiếp tục thủ công. Chỉ có ý nghĩa **trước** khi đơn tồn tại — từ bước Thanh toán/Hoàn tất trở đi luồng được giải phóng bằng `clear()`.

> Đây là key `localStorage` thứ hai của accessory storefront, tách biệt với `storageKeys.accessoryCart` (`'ACCESSORY_CART'`) — giỏ hàng và tiến trình checkout được persist độc lập.

> **Redirect checkout một lần khi bootstrap app (`AppComponent`)**: ngoài việc **tải lại chính trang checkout** khôi phục đúng bước (nhờ persist ở trên), `AppComponent.ngOnInit` còn gọi `_applyBootstrapCheckoutRedirect()` để xử lý **lần vào đầu tiên** khi khách **mở lại site** (ví dụ đóng tab giữa chừng rồi vào lại) — vì khóa điều hướng `NavigationStart` (`_enforceCheckoutLock`) chỉ có hiệu lực từ lần điều hướng **thứ hai** trở đi (điều hướng bootstrap ban đầu có thể chạy trước khi component subscribe). Nó đọc state đã persist dưới `storageKeys.accessoryCheckout` qua `LocalStorageService.getData<AccessoryCheckoutState>(...)` và áp dụng **hai trường hợp**:
>
> - **Đơn đang chờ** (`checkoutEntered && currentIndex >= 1`, tức `_hasPendingOrder()`): resume từ **bất kỳ trang vào nào** (homepage hay deep-link), khớp với khóa điều hướng — một đơn đã tạo luôn phải được đưa trở lại checkout.
> - **Chỉ mới ở bước Điền thông tin** (`checkoutEntered` nhưng chưa có đơn): chỉ resume khi vào từ **homepage** ("vào lại trang homepage"), để không "cướp" một deep-link vào trang khác (bước Điền thông tin chưa có đơn và cố ý **không** bị khóa).
>
> Cờ `checkoutEntered` chỉ `true` một khi khách **thực sự** đến trang checkout, nên khách chỉ preview mã giảm giá trong popup giỏ (chưa `checkoutEntered`) không bao giờ bị kéo vào checkout. Redirect được **bỏ qua** với các route vào hợp lệ khi đang khóa (`_isAllowedWhileLocked`: chính trang checkout `routeName.productCheckout`, trang trả về kết quả thanh toán `routeName.paymentResult`, và đăng nhập/đăng xuất), để không phá vỡ điều hướng tường minh hay gây lặp. Container checkout sau đó khôi phục đúng bước từ cùng state đó (và fallback về danh sách sản phẩm nếu, ví dụ, giỏ đã bị làm trống). Chỉ state accessory checkout được đọc và chỉ route accessory checkout được điều hướng tới, nên luồng đặt vé (ticket booking) không bị ảnh hưởng.

> **Khóa điều hướng khi đơn đang chờ xử lý (`AppComponent._enforceCheckoutLock()`)**: bên cạnh resume, `AppComponent.ngOnInit` còn gọi `_enforceCheckoutLock()` để **giữ khách trong luồng checkout** trong khi một đơn đã được tạo và đang chờ xử lý (khách đang ở bước Thanh toán/Hoàn tất). Nó lắng nghe `Router.events` (lọc `NavigationStart` qua `rxjs` `filter`) và, nếu có đơn đang chờ, mọi điều hướng sang route khác đều bị **đẩy ngược** về trang checkout (`/${routeName.productCheckout}`). Mục đích là chặn khách dùng nút điều hướng để rời đi và bắt đầu một đơn **thứ hai**, làm "mồ côi" đơn đầu (theo mô hình reserve-on-create, đơn mồ côi sẽ giữ kép tồn kho/mã giảm giá cho đến khi sweep TTL phía backend dọn).
>
> Phạm vi & an toàn:
> - Chỉ khóa trong **cửa sổ đơn-đang-chờ** (`checkoutEntered && currentIndex >= 1`) — bước Điền thông tin (chưa có đơn) **không** bị khóa, nên nút "Quay lại" của nó vẫn hoạt động.
> - Các route vẫn cho phép khi đang khóa (`_isAllowedWhileLocked`): chính trang checkout, trang trả về kết quả thanh toán (`routeName.paymentResult`), và trang đăng nhập/đăng xuất (`routeName.login`/`routeName.logout`, để `authGuard` của checkout có thể bounce sang) — tránh vòng lặp điều hướng.
> - Mọi lối thoát hợp lệ trên các bước bị khóa (Hủy đơn, quay lại giỏ, tiếp tục mua sắm, popup hết hạn) đều gọi `AccessoryCheckoutService.clear()` **trước** khi điều hướng, nên cờ khóa được gỡ và các nút đó vẫn hoạt động.
> - Luồng đặt vé (ticket booking) chỉ tạm thời không truy cập được trong cửa sổ ngắn này (≤ TTL); store/resume riêng của nó không bị đụng tới.

### Checkout Components (`pages/accessory-checkout`)

> **Lưu ý về luồng bước**: việc **chọn sản phẩm, xem/điều chỉnh giỏ và preview mã giảm giá** nay diễn ra ngay trên các **trang catalog** — trang danh sách tất cả show (`ProductListComponent`) và trang sản phẩm của một show (`ProductDetailComponent`, `san-pham/:showCode`) — qua **popup chọn sản phẩm** (`ProductModalComponent`: chọn biến thể/size + thêm giỏ) và **popup giỏ** (`AccessoryCartComponent`), **không còn là một bước trong container checkout**. Vì vậy `AccessoryCheckoutComponent` chỉ còn **3 bước**: **Điền thông tin → Thanh toán → Hoàn tất**. Khi mở container, `ngOnInit` gọi `AccessoryCartService.restore()` (Req 3.7); nếu giỏ trống thì điều hướng về danh sách sản phẩm (`routeName.product`), ngược lại vào thẳng bước Điền thông tin (`startAtInformation()`, `currentIndex = 0`) trong khi vẫn giữ nguyên discount/subtotal đã preview ở popup giỏ.

`AccessoryCheckoutComponent` là container 3 bước, tương tự `BookingComponent`:

1. **Điền thông tin** (index 0) — `AccessoryInformationComponent`: chỉ họ tên, số điện thoại, email; **không có địa chỉ giao hàng** (pickup-at-show). Submit toàn bộ giỏ tới `POST accessory/checkout` (endpoint yêu cầu đăng nhập, không mang tiền tố `public/`).
2. **Thanh toán** (index 1) — `AccessoryPaymentComponent`: chọn phương thức + start gateway (tái dùng pattern payment của homepage); poll trạng thái được thực hiện ở bước Hoàn tất. Bước này render đồng hồ đếm ngược TTL của đơn qua component dùng chung `AccessoryOrderCountdownComponent` (xem bên dưới), nhận `expiresAt` + `encodedOrderId` từ state checkout.
3. **Hoàn tất** (index 2) — `AccessoryCompletedComponent`: mã đơn, ghi chú email xác nhận đã gửi + nhận hàng tại show, link xem lại đơn; xóa giỏ khi thành công.

Chuyển bước qua `AccessoryCheckoutService`: `checkoutCompleted(...)` đưa từ Điền thông tin (0) sang Thanh toán (1); `goToPaymentTracking()` đưa từ Thanh toán (1) sang Hoàn tất (2) ở trạng thái **PENDING** ngay sau khi start gateway (`currentIndex = 2`, `isStartedPayment = true`, `paymentResult = ''`), để bước Hoàn tất poll trạng thái thanh toán và tự resolve thành công/thất bại; `paymentSucceeded()` đưa từ Thanh toán (1) sang Hoàn tất (2) với `paymentResult = 'Succeed'`; `paymentFailed()` cũng đưa từ Thanh toán (1) sang Hoàn tất (2) nhưng với `paymentResult = 'Failed'` để bước Hoàn tất báo thất bại (thay cho popup thử lại ở bước Thanh toán); `onPreviousIndex()` lùi một bước.

> **Breadcrumb 4 bước vs. container 3 bước**: dù container chỉ **render 3 bước**, thanh tiến trình vẫn hiển thị **đầy đủ 4 bước** (`breadcrumbSteps`: **Chọn → Điền thông tin → Thanh toán → Hoàn tất**), trong đó **Chọn** là bước sản phẩm đã hoàn tất trên trang catalog trước khi vào container. Vì vậy chỉ số bước được render (`steps`, bắt đầu từ `Điền thông tin`) **lệch +1** so với danh sách breadcrumb (ví dụ rendered index 0 = breadcrumb bước 2 "Điền thông tin").

> **Trạng thái triển khai**: các component bước (`AccessoryInformationComponent`, `AccessoryPaymentComponent`, `AccessoryCompletedComponent`), `AccessoryOrderCountdownComponent` (đồng hồ đếm ngược TTL, chỉ dùng ở bước Thanh toán), `AccessoryCartComponent` (nay dùng trong popup giỏ ở trang danh sách sản phẩm), `AccessoryBreadcrumbComponent` (thanh tiến trình) và `AccessoryCheckoutComponent` (container 3 bước) **đã được triển khai**.

#### AccessoryOrderCountdownComponent (đồng hồ đếm ngược TTL của đơn)

`selector: 'accessory-order-countdown'`, standalone. Component **chỉ để hiển thị** đồng hồ đếm ngược tới deadline TTL của đơn, dùng ở bước **Thanh toán** (sau khi đơn đã được tạo). Bước **Hoàn tất** **không** dùng component này — nó tự poll đối chiếu trạng thái đơn/thanh toán (xem `AccessoryCompletedComponent`). Nhận hai input:

- **`@Input() expiresAt = 0`** — deadline tuyệt đối (epoch millis); `0`/falsy ⇒ không hiển thị gì (`visible === false`).
- **`@Input() encodedOrderId = ''`** — id đơn đã `Checksum`-mã hóa, dùng để re-check trạng thái khi đếm ngược về 0.

Mỗi giây cập nhật nhãn `remainingLabel` dạng `"mm:ss"`. Component **không** tự hết hạn đơn — việc hết hạn thực sự do **sweep phía backend** đảm nhận. Khi đếm ngược về 0, nó re-check trạng thái đơn authoritative qua `AccessoryService.getAccessoryOrder(encodedOrderId)`:

- trạng thái terminal-không-thanh-toán (`Expired` / `PaymentFailed` / `PaymentCancelled`) ⇒ mở popup cho khách quay lại giỏ (`onBackToCart()` gọi `AccessoryCheckoutService.clear()` rồi điều hướng về trang sản phẩm của show kèm `?openCart=1`);
- `PaymentSucceed` ⇒ dừng, không popup;
- vẫn pending (sweep có thể trễ deadline vài phút) ⇒ tiếp tục re-check với chu kỳ `5s`, giới hạn `24` lần (~2 phút) để không poll vô hạn.

### Catalog Pages (`pages/product-list`, `pages/product-detail`)

Hai trang catalog chia sẻ **cùng một luồng popup** để chọn sản phẩm và mở giỏ, tách hẳn khỏi container checkout:

- **`ProductListComponent`** (`san-pham`) — liệt kê **tất cả** accessory show đang mở kèm sản phẩm. Vì endpoint listing (`show/fetch/all`) chỉ trả tóm tắt show (không kèm `accessories`), `ngOnInit` **hydrate** từng show bằng `getAccessoryShow(show.id)` (`switchMap` + `forkJoin`); nếu detail của một show lỗi thì `catchError` giữ tóm tắt đó với `accessories: []` thay vì hỏng cả trang. Lỗi khi lấy danh sách show ban đầu → trạng thái lỗi toàn trang.
- **`ProductDetailComponent`** (`san-pham/:showCode`) — lưới sản phẩm của **một** show, lấy qua `getAccessoryShow(showCode)`. Nếu detail không lấy được → trạng thái lỗi, không render một phần (Req 2.5). Trang này còn render một **banner thông tin show** (chỉ hiển thị, không có giá trị authoritative) qua các getter:
  - `bannerThumbs` — tối đa 3 ảnh sản phẩm đầu (`accessories.slice(0, 3)`) làm icon minh họa.
  - `pickupLabel` — `"Pickup tại show {dd/MM} ({city})"` dựng từ `selectedSession` (buổi diễn khách đang chọn; fallback về session đầu tiên khi chưa chọn) với ngày rút gọn qua `_shortDate`; nếu không có session thì fallback về `show.pickupNote`, cuối cùng `"Pickup tại show"`.
  - `subtitleLine` — phụ đề `"Trọn bộ sưu tập · Pre-Order · {pickupLabel}"`.
  - `paymentLabel` — `"Trả trước {các phương thức}"` từ `show.allowedPaymentMethods`, map tên `PaymentType` sang nhãn tiếng Việt ngắn (`QrCode` → `QR/Chuyển khoản/Ví điện tử`, `VietQr` → `VietQR`, `DomesticCard` → `Thẻ ATM nội địa`, `InternationalCard` → `Thẻ quốc tế`); tên không xác định giữ nguyên; danh sách rỗng → chỉ `"Trả trước"`.

  Khi show có `sessions` (`hasSessions`), trang render một **bộ chọn buổi diễn** (session-selection) để khách chọn buổi diễn mình mua sản phẩm cho. Ngay sau khi tải show thành công, `_initSessionSelection()` khởi tạo lựa chọn: nếu giỏ hiện tại thuộc đúng show này và `showSessionId` đã lưu vẫn là một session hợp lệ thì giữ lại, ngược lại mặc định về session đầu tiên; giá trị được đồng bộ vào giỏ qua `AccessoryCartService.setSession(sessionId, showId)` để gửi kèm ở checkout. Đổi lựa chọn (`onSessionChange`) cập nhật cả `selectedSessionId` lẫn giỏ; `sessionLabel` hiển thị nhãn ngắn `"{city} · {dd/MM}"`. Show không có session ⇒ `selectedSessionId = null`.

Cả hai gọi `AccessoryCartService.restore()` khi khởi tạo để giữ badge "Xem giỏ hàng" (`cartCount$` từ `AccessoryCartQuery.totalQuantity$`) đồng bộ qua các lần tải. Chọn một sản phẩm gọi `onOpenProduct(...)` mở `ProductModalComponent`; `onOpenCart()` mở popup giỏ (`AccessoryCartComponent`); `onCartContinue()` điều hướng sang container checkout (`routeName.productCheckout`).

#### ProductModalComponent (popup chọn sản phẩm)

Popup chọn sản phẩm hiển thị khi chọn một sản phẩm trên trang catalog (dùng chung bởi cả `ProductListComponent` và `ProductDetailComponent`). Với sản phẩm có biến thể (ví dụ **Áo thun** có size **S/M/L/XL**), hiển thị bộ chọn biến thể/size và **vô hiệu hóa "Thêm vào giỏ"** cho tới khi chọn biến thể (Req 2.3, 2.4); sản phẩm single-SKU **không** hiển thị bộ chọn (Req 2.2). Thêm vào giỏ ghi dòng hàng kèm `variantKey` (có biến thể) hoặc không kèm `variantKey` (single-SKU) qua `AccessoryCartService` (Req 2.6, 2.7).

Popup hiển thị **gallery ảnh sản phẩm** (getter `images`): dùng `imageUrls` khi có, ngược lại fallback về `imageUrl`, và cuối cùng fallback về thumbnail mặc định (`appImages.imgMetaTicketThumbnail`). Ảnh lớn (`mainImage`) theo `selectedImageIndex`, đổi khi bấm thumbnail (`selectImage(index)`). Nút "Thêm vào giỏ" hiển thị **tổng tiền dòng hàng** (`lineTotal` = `price × quantity`) — chỉ là số liệu hiển thị; backend luôn re-derive giá từ catalog khi checkout.

> **Thêm vào giỏ yêu cầu đăng nhập**: dù trang catalog và popup chọn sản phẩm là public, `onAddToCart()` kiểm tra `AuthService.isAuthenticated()` trước khi ghi dòng hàng. Khách chưa đăng nhập nhận thông báo lỗi ("Vui lòng đăng nhập để thêm sản phẩm vào giỏ hàng.") và bị điều hướng tới `/dang-nhap` (`routeName.login`) kèm `queryParams.returnUrl = /san-pham/{show.id}` để quay lại đúng show sau khi đăng nhập. Điều này khớp với `authGuard` trên route checkout và với việc backend từ chối checkout/preview ẩn danh bằng `401` — giỏ hàng gắn theo từng user và toàn bộ luồng mua là luồng đã xác thực.

#### AccessoryCartComponent (popup giỏ trên trang catalog)

`selector: 'accessory-cart'`, standalone. Xem/điều chỉnh dòng hàng và preview mã giảm giá. Trước đây là bước "Chọn" đầu tiên của container checkout; nay được dùng như **popup giỏ hàng trên các trang catalog** (`ProductListComponent` và `ProductDetailComponent`), tách khỏi container checkout.

- **`@Input() show?: AccessoryShowResponse`**: show đang xem kèm danh sách accessory đang active (từ trang catalog). Dùng để kiểm tra mỗi dòng giỏ (`isLineInvalid(line)`) có còn trỏ tới một sản phẩm/biến thể catalog thực sự và đang active hay không; dòng không hợp lệ bị làm mờ và chặn checkout. Khi input vắng, bỏ qua kiểm tra (không có gì để đối chiếu). Một dòng là **không hợp lệ** khi: giỏ đã trôi sang show khác; sản phẩm biến mất hoặc `active === false`; hoặc — với dòng có biến thể — biến thể `variantKey` **không còn tồn tại HOẶC có `active === false`** (một tổ hợp biến thể inactive được coi là **đã ngừng bán** / withdrawn from sale, y như sản phẩm inactive).
- **`@Input() pickupLabel = 'Pickup tại show'`**: nhãn nhận hàng do trang catalog truyền vào (ví dụ `"Pickup tại show 11/07 (HCM)"`, tái dùng từ `pickupLabel` của banner show / popup chọn sản phẩm), render thành badge `"Pre-Order · {pickupLabel}"` để popup giỏ hiển thị cùng gợi ý fulfillment như popup chi tiết sản phẩm. Mặc định `"Pickup tại show"` khi trang không truyền vào.
- **Chặn checkout theo tồn kho**: đối chiếu số lượng mỗi dòng với tồn kho hiện thời của **ô tồn kho** của dòng đó (`stockFor(line)` — tồn kho của biến thể với dòng có biến thể, ngược lại tồn kho top-level của sản phẩm). Tồn kho không xác định (không có `[show]`, sản phẩm/biến thể đã biến mất) hoặc **không giới hạn** (`-1`) được coi là không có giới hạn hữu hạn để enforce (`stockFor` trả `null`). Dòng có số lượng vượt tồn kho (`lineExceedsStock(line)`) hiển thị gợi ý inline (`stockHint(line)`): `"Sản phẩm đã hết hàng"` khi tồn kho `<= 0`, ngược lại `"Chỉ còn {X} sản phẩm"`. Checkout bị chặn khi bất kỳ dòng nào không hợp lệ **hoặc** vượt tồn kho — getter `blockCheckout` = `hasInvalidLines || hasStockIssues` (với `hasStockIssues` = có ít nhất một dòng vượt tồn kho).
- Mỗi dòng hiển thị tên sản phẩm + nhãn biến thể + số lượng, ví dụ `"Áo thun · Size M ×2"` (label bỏ với single-SKU) — `lineDisplay(line)`.
- Điều chỉnh số lượng qua `AccessoryCartService` (1..99; < 1 xóa dòng; > 99 bị từ chối kèm thông báo lỗi) — `onIncrease` / `onDecrease` / `onRemove`.
- Subtotal tự tính lại từ các dòng và đẩy vào `AccessoryCheckoutService`.
- Preview mã giảm giá qua `POST accessory/discount/validate` (Req 4.1); kết quả (`code`, `amount`, `subtotal`, `total`, `type`, `value`) lưu vào `AccessoryCheckoutService.applyDiscount(...)` để bước Điền thông tin submit. `type`/`value` (`Percentage` → phần trăm; `FixedAmount` → số tiền cố định VND) dùng để dựng nhãn/thông báo giảm giá (`discountLabelSuffix`, `discountBannerText`, `_discountSuccessMessage`). Lỗi `422` (mã không áp dụng được) hiển thị lý do từ backend.
- Khi áp dụng thành công, hiển thị thông báo xác nhận tiếng Việt theo loại mã (`_discountSuccessMessage`): `Percentage` → `"Đã áp dụng mã {code} — giảm {value}%"`; `FixedAmount` → `"Đã áp dụng mã {code} — giảm {amount}đ"` (số tiền định dạng `vi-VN`).
- **Khôi phục discount khi mở lại popup**: `ngOnInit` đọc `AccessoryCheckoutQuery` — nếu đã có `discountCode` được preview trước đó trong phiên, popup hiển thị lại mã + số tiền giảm đã áp dụng (store là nguồn sự thật). Vì `lines$` phát đồng bộ ngay khi subscribe, lần phát **đầu tiên** này được bỏ qua để không xóa nhầm discount vừa khôi phục; chỉ các thay đổi giỏ **thực sự** sau đó mới **hủy** discount đã preview (và xóa ô nhập mã).
- `onContinue()` chặn khi giỏ trống rồi điều hướng sang container checkout (bước Điền thông tin).

#### AccessoryInformationComponent (bước 1 "Điền thông tin")

`selector: 'accessory-information'`, standalone. Thu thập **chỉ** họ tên, số điện thoại, email (Req 5.1); **không có** trường địa chỉ giao hàng (pickup-at-show, Req 5.2). Dùng reactive form với `validators`/`validationRegex` (họ tên, số điện thoại, email).

- Khôi phục giá trị đã nhập từ `AccessoryCheckoutQuery` khi quay lại bước này.
- `onSubmit()` gom toàn bộ dòng giỏ (`accessoryId`, `variantKey?`, `quantity`) + mã giảm giá đã preview thành `AccessoryCheckoutRequest`, post tới `POST accessory/checkout` (Req 6.1). Thành công: lưu `encodedOrderId`/`encodedPaymentId`/`orderCode`/`total` vào `AccessoryCheckoutService.checkoutCompleted(...)` rồi sang bước Thanh toán (Req 6.15, 7.3). Lỗi giữ nguyên giá trị đã nhập (Req 5.3/5.4/5.5).
- Chặn submit khi giỏ trống (điều hướng về trang danh sách sản phẩm).

#### AccessoryPaymentComponent (bước 2 "Thanh toán")

`selector: 'accessory-payment'`, standalone. Chỉ **chọn phương thức thanh toán + start gateway**, tái dùng `PaymentService` dùng chung của homepage (giống `BookingPaymentComponent`, Req 7.3). Việc **poll trạng thái thanh toán đã được chuyển sang bước Hoàn tất** — component này không còn tự poll, không còn popup thử lại, và không còn xóa giỏ.

- Danh sách phương thức thanh toán được giới hạn theo `AccessoryShowResponse.allowedPaymentMethods` (tên enum `PaymentType` của backend, ví dụ `"VietQr"`, `"QrCode"`, `"InternationalCard"`), lấy từ endpoint chi tiết show (`_loadAllowedPaymentMethods()` trong `ngOnInit`). Khi field rỗng/vắng — hoặc lookup lỗi — storefront hiển thị tất cả phương thức khả dụng.
- `startPayment()` gọi `POST payment/start` với encoded `paymentId`, mở tab gateway (`window.open` sau ~500ms), rồi gọi `AccessoryCheckoutService.goToPaymentTracking()` để chuyển **thẳng** sang bước Hoàn tất ở trạng thái PENDING. Việc phân giải thành công / thất bại / còn-đang-xử-lý (bao gồm xóa giỏ khi thành công, Req 3.4) do **bước Hoàn tất** đảm nhận qua polling.
- Không còn phụ thuộc `PaymentStatus`, `NzModalModule`/`NzIconModule`, hay `AccessoryCartService` (đã gỡ khỏi imports/constructor); lỗi khi start gateway hiển thị notification và giữ nguyên bước Thanh toán để thử lại.

#### AccessoryCompletedComponent (bước 3 "Hoàn tất")

`selector: 'accessory-completed'`, standalone. Bước xác nhận kết quả, tương tự `BookingCompletedComponent` (Req 13.1, 13.2). Component **đối chiếu (reconcile) hai trạng thái** thay vì chỉ dựa vào `paymentResult`:

- **Trạng thái đơn hàng** (`orderStatus`, kiểu `AccessoryOrderStatus`) — lấy qua `GET public/accessory/order/fetch` (`AccessoryService.getAccessoryOrder`); **luôn** được kiểm tra.
- **Trạng thái thanh toán** (`paymentStatus`, kiểu `PaymentStatus`) — lấy qua `GET payment/status` (`PaymentService.getPaymentStatus`); **chỉ** kiểm tra khi đơn có payment (`hasPayment = !!encodedPaymentId`).

**Overall status** (`overall`, getter) gộp hai trạng thái trên thành một trong bốn kết quả:

Thứ tự phân loại: **thất bại được xử lý ngay** (không chờ bên còn lại resolve), sau đó mới xét thành công, rồi bất đồng, cuối cùng là đang xử lý.

- `failed` — có ít nhất một bên **thất bại** (payment `Failed`/`Cancelled` hoặc đơn `PaymentFailed`/`PaymentCancelled`/`Expired`) và **không** bên nào thành công. Được xác định ngay khi phát hiện thất bại, không chờ bên còn lại resolve (không phát sinh phí).
- `success` — đơn có payment: cả payment (`Succeed`) và đơn (`PaymentSucceed`) đều thành công; đơn không payment: chỉ cần đơn `PaymentSucceed`.
- `mismatch` — payment và đơn **bất đồng**: một bên **thất bại** trong khi bên kia đã **thành công**. Hiển thị thông báo đề nghị khách **liên hệ MetaTicket** kèm hotline (`supportHotline = '1900 3392'`) và email (`supportEmail = 'support@metaticket.com.vn'`).
- `processing` — chưa có bên nào thất bại, chưa đủ điều kiện thành công/bất đồng: vẫn còn trạng thái đang được poll.

Các getter tiện ích cho template: `isProcessing`/`isSuccess`/`isFailed`/`isMismatch`, cùng `paymentStatusMessage`/`paymentStatusOk` và `orderStatusMessage`/`orderStatusOk` (chuỗi tiếng Việt cho từng trạng thái).

**Polling**: `ngOnInit` gọi `_poll()`, dùng `forkJoin` gộp hai request (mỗi request `catchError(() => of(null))` để một lỗi không làm hỏng cả vòng). Poll lặp mỗi 3 giây (`_pollIntervalMs`) **cho tới khi** mọi trạng thái resolve — **không giới hạn số lần thử**: callback payment→đơn đến trễ (kể cả vài phút sau) trang vẫn tự cập nhật mà không cần thao tác thủ công. Vòng poll chỉ dừng (`_stopped = true`) khi đơn (và payment nếu có) đã terminal, hoặc khi component bị hủy.

> **Poll ngay khi khách quay lại tab**: cổng thanh toán mở ở **tab khác**, nên khách hoàn tất thanh toán ở ngoài trang này. `ngOnInit` đăng ký lắng nghe `document` `visibilitychange` và `window` `focus` (gỡ trong `ngOnDestroy`); khi khách chuyển về lại tab, `_pollNowIfPending()` **hủy timer đang chờ và poll ngay lập tức** nếu còn trạng thái chưa resolve — để cập nhật tức thì thay vì chờ tick kế tiếp. Là no-op khi cả hai trạng thái đã terminal hoặc component đã bị hủy (`_stopped`).

> **Thoát khi bị kẹt thanh toán**: ở trạng thái `processing`, ngoài spinner còn có nút cảnh báo (style vàng) `⚠️ Tôi gặp vấn đề khi thanh toán` (`onRequestLeaveWhileProcessing()`). Nút mở **popup xác nhận** (state `leaveConfirmOpen`) nhắc khách **chụp lại màn hình kèm mã đơn hàng** và liên hệ hỗ trợ (hiển thị `#orderCode`, hotline, email). Popup có hai lựa chọn: "Ở lại chờ kết quả" (`onCancelLeave()` — đóng popup, tiếp tục poll) và "Tôi đã lưu mã đơn, quay về giỏ hàng" (`onConfirmLeave()` — đóng popup rồi gọi `onBackToCart()`). Đây là lối thoát cho trường hợp khách không thể thanh toán và payment kẹt ở PENDING.

Ở trạng thái thành công hiển thị: **mã đơn** (`orderCode`, ví dụ `#TF2026-0148`), ghi chú email xác nhận đã gửi, ghi chú nhận hàng tại show (`pickupNote` từ `order.pickupNote`, fallback `defaultPickupNote`), và link xem lại đơn — `onReviewOrder()` gọi `_fetchOrderOnce()` (`GET public/accessory/order/fetch?id=`) rồi cuộn lên đầu trang.

`_recordOutcome()` chỉ **ghi nhận kết quả một lần** khi biết terminal: lần thành công đầu tiên gọi `AccessoryCartService.clear()` (xóa giỏ, Req 3.4) + `AccessoryCheckoutService.clearDiscount()` + `paymentSucceeded()`; lần thất bại đầu tiên (khi chưa thành công) gọi `paymentFailed()`. Kết quả được persist nên tải lại trang resume đúng trạng thái.

`onConfirmComplete()` xác nhận hoàn tất: gọi `AccessoryCheckoutService.clear()` để **xóa luồng checkout đã lưu** (khỏi storage) rồi điều hướng về danh sách sản phẩm (`routeName.product`). Chừng nào chưa bấm nút này, tải lại trang vẫn giữ khách ở bước "Hoàn tất" (đơn được resume từ state đã lưu).

`onContinueShopping()` (khi cả hai trạng thái đã thành công) gọi `AccessoryCartService.clear()` **và** `AccessoryCheckoutService.clear()` trước khi về danh sách sản phẩm, đảm bảo khách bắt đầu một đơn mới hoàn toàn sạch (giỏ đã được `_recordOutcome()` xóa khi thành công, nên đây là thao tác idempotent phòng vệ để đơn vừa hoàn tất không còn sót lại trong giỏ). Ngược lại, `onBackToCart()` (khi có trạng thái thất bại, **hoặc** khi khách chủ động rời đi lúc còn `processing` qua `onConfirmLeave()`) **chỉ** gọi `AccessoryCheckoutService.clear()` rồi quay về giỏ trên trang chi tiết show (`openCart=1`) để khách chỉnh sửa và thử lại — giỏ được **giữ nguyên**.


#### Hủy đơn hàng từ bước Thanh toán (Customer Cancel Order)

Bước Thanh toán (`AccessoryPaymentComponent`) cung cấp nút **"Hủy đơn"** (thay thế nút "Quay lại" cũ — nút "Quay lại" bị loại bỏ để tránh tạo đơn trùng lặp do reserve-on-create giữ kép tồn kho). Luồng:

1. Khách bấm "Hủy đơn" → `onRequestCancel()` mở popup xác nhận (`cancelConfirmOpen = true`).
2. Khách xác nhận → `onConfirmCancel()` gọi `AccessoryService.cancelAccessoryOrder(encodedOrderId)` — HTTP `POST accessory/order/cancel?id=<encodedOrderId>`.
3. Thành công (200): `AccessoryCheckoutService.clear()` → điều hướng về giỏ trên trang sản phẩm (`_navigateBackToCart()`).
4. Lỗi `409` (đơn đã qua giai đoạn cho phép hủy): hiển thị thông báo lỗi từ backend; đơn vẫn giữ nguyên trạng thái thật (bước Hoàn tất sẽ poll ra kết quả).
5. Khách bấm "Đóng" (dismiss) → `onDismissCancel()` quay về bước Thanh toán như bình thường.

Backend (`POST /accessory/order/cancel?id=`):
- Yêu cầu header `X-Customer-User-Id` (401 nếu vắng).
- Object-level authorization: `order.customerUserId` phải khớp caller (403 `NotOrderOwner` nếu không).
- Chỉ cho phép hủy từ `EXPIRABLE_STATES` (`Created` / `WaitingForPayment`) → chuyển sang `PaymentCancelled`.
- Khi thành công: giải phóng tồn kho đã reserve + revert mã giảm giá (cùng guarded `releaseReservation` như expiry).
- Idempotent: đơn đã `PaymentCancelled` → 200 no-op.
- Đơn đã vượt cửa sổ hủy → 409 `CancelNotAllowed`.
- `cancelAccessoryOrder` được khai báo trong `api-url.ts` (`'accessory/order/cancel'`) và `accessory.service.ts`.

#### Khóa & Resume điều hướng khi đơn đang chờ (Navigation Lock & Resume — `AppComponent`)

`AppComponent` (root component của `metaticket-home`) thực thi hai cơ chế bảo vệ luồng checkout khi có đơn đang chờ thanh toán:

**1. Bootstrap redirect (`_applyBootstrapCheckoutRedirect`)**: chạy một lần duy nhất tại `ngOnInit`. Nếu **pending order** tồn tại cho user đang đăng nhập hiện tại (xem `_hasPendingOrder()` bên dưới), điều hướng từ bất kỳ URL vào nào (homepage, deep-link, v.v.) về trang checkout (`/${routeName.productCheckout}`). Bỏ qua nếu URL vào đã là route hợp lệ khi đang khóa (checkout, payment-result, login, logout).

**2. Ongoing NavigationStart lock (`_enforceCheckoutLock`)**: subscribe `Router.events` lọc `NavigationStart`. Trong khi `_hasPendingOrder() === true`, mọi điều hướng sang route **không** nằm trong danh sách cho phép đều bị đẩy ngược về trang checkout. Mục đích: chặn khách dùng nút điều hướng rời đi và bắt đầu đơn thứ hai (reserve-on-create sẽ giữ kép tồn kho/mã giảm giá đến khi backend sweep dọn).

**3. Phát hiện đơn đang chờ (`_hasPendingOrder`)**: kiểm tra đồng thời ba điều kiện:
- `AuthService.isAuthenticated()` — khách phải đang đăng nhập.
- `ownerUserId` trong state persist khớp `AuthService.getUserId()` — chỉ chính chủ đơn mới bị khóa/resume.
- Trạng thái đơn: `currentIndex === 1 && encodedPaymentId` (đang ở bước Thanh toán) hoặc `currentIndex >= 2 && encodedOrderId` (đang ở bước Hoàn tất).

**Routes cho phép khi đang khóa** (`_isAllowedWhileLocked`): `routeName.productCheckout`, `routeName.paymentResult`, `routeName.login`, `routeName.logout`.

**Phạm vi an toàn**:
- Bước Điền thông tin (chưa có đơn) **không** bị khóa — nút "Quay lại" của nó hoạt động bình thường.
- Mọi lối thoát hợp lệ (Hủy đơn, popup hết hạn, quay lại giỏ, tiếp tục mua sắm) gọi `AccessoryCheckoutService.clear()` **trước** khi điều hướng → cờ khóa được gỡ.
- Luồng đặt vé tạm thời không truy cập được trong cửa sổ ngắn này (≤ TTL); store/resume riêng của nó không bị đụng tới.

#### `ownerUserId` — scope resume/lock theo chủ đơn

Field `ownerUserId: string` trên `AccessoryCheckoutState`, được ghi tại `checkoutCompleted(...)` từ `AuthService.getUserId()`. Giá trị này quyết định đơn **thuộc về ai**: chỉ tài khoản đã tạo đơn mới bị resume/lock. Một tài khoản khác đăng nhập trên cùng trình duyệt **không** bị kéo vào/kẹt trong đơn của người khác.

**Container owner guard** (`AccessoryCheckoutComponent.ngOnInit`): sau `restore()`, so `ownerUserId` đã khôi phục với `AuthService.getUserId()` hiện tại. Nếu khác → `AccessoryCheckoutService.clear()` → bắt đầu sạch (không rò rỉ dữ liệu khách trước).

#### Logout → login (thay vì homepage)

Khi khách đăng xuất, app điều hướng tới trang **login** (`routeName.login`) thay vì homepage. Lý do: nếu đăng xuất đưa về homepage, khóa điều hướng (đọc từ localStorage nơi `ownerUserId` khớp user vừa đăng nhập trước khi `isAuthenticated()` về `false`) có thể kéo khách vừa đăng xuất quay lại checkout. Chuyển sang login đảm bảo `_isAllowedWhileLocked` cho phép route đó đi qua, và `_hasPendingOrder()` trả `false` (do `isAuthenticated() === false`).

#### Banner cảnh báo bước Điền thông tin

`AccessoryInformationComponent` hiển thị banner cảnh báo ngay trước nút submit:

> "Hãy kiểm tra kỹ đơn hàng và thông tin trước khi qua bước thanh toán. Đơn hàng sẽ không thể thay đổi được sau bước này."

Mục đích: nhấn mạnh rằng sau khi sang bước Thanh toán, đơn đã được tạo (reserve-on-create) và **không thể quay lại chỉnh sửa** — chỉ có thể hủy rồi tạo lại.

---

## PHÂN HỆ TIN TỨC (NEWS)

| Tài liệu | Nội dung |
|----------|----------|
| [NEWS.md](./NEWS.md) | **Tài liệu tổng hợp:** kiến trúc UI, đặc tả API, Hybrid SSR & deploy |