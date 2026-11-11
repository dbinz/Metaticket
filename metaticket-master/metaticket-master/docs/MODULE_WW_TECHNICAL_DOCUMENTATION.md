# Tài liệu Kỹ thuật - Module WW (Web Wrapper `planner.wrapper.js`)

## 1. Tổng quan

`@metaticket/ww` (thư mục `ts/packages/ww/metaticket/ww`) là **web wrapper**: một bundle JavaScript nhỏ, độc lập, cho phép một website **không thuộc monorepo `ts/`** nhúng sơ đồ chỗ ngồi (seat map) ở chế độ **chỉ xem/chọn ghế**. Theo `README` của package, wrapper "cung cấp entry point re-export các class từ thư viện nội bộ" để trang bên ngoài dùng được mà vẫn đảm bảo các shared module (coordinator, editor) chỉ được nạp một lần qua Module Federation.

Người dùng duy nhất hiện tại là storefront **`metaticket-home`** (trang chi tiết sự kiện và bước chọn ghế khi đặt vé). Storefront **không bundle** wrapper và **không dùng iframe**: nó chèn thẻ `<script>` trỏ tới `{metaDomain}/modules/metaticket/ww/planner.wrapper.js` lúc runtime, rồi gọi `new metaticket.PlannerWrapper(...)`.

| Thuộc tính | Giá trị |
|-----------|---------|
| Package | `@metaticket/ww` v3.0.0 (`ts/packages/ww/metaticket/ww/package.json`) |
| Entry | `src/planner.wrapper.ts` (+ `src/model.ts`) |
| Output | `ts/dist/metaticket/ww/planner.wrapper.js` (+ `.d.ts`, `package.json`) |
| Global export | `window.metaticket.PlannerWrapper`, `window.metaticket.AppStateService` |
| URL runtime | `/modules/metaticket/ww/planner.wrapper.js` |
| Phụ thuộc runtime | `/modules/viclass/ww/mfe.webrt.js` (global `viclass.mfeRT`), `/modules/viclass/mfe/coordinator.embed.js`, `/modules/metaticket/editor.seatplanner/editor.seatplanner.js`, API `/api/public/editor/seatplanner` |

> Phần **editor** (vẽ/sửa sơ đồ trong portal admin, class `SeatPlannerEditor`, state machine, command system) đã có ở [MODULE_EDITOR_TECHNICAL_DOCUMENTATION.md](./MODULE_EDITOR_TECHNICAL_DOCUMENTATION.md). Tài liệu này chỉ mô tả lớp wrapper, cách nó được build/serve/tiêu thụ, và hợp đồng DOM/sự kiện mà trang nhúng dựa vào. Backend tài liệu sơ đồ: [SERVICE_EDITOR_SEATPLANNER_TECHNICAL_DOCUMENTATION.md](./SERVICE_EDITOR_SEATPLANNER_TECHNICAL_DOCUMENTATION.md).

> Tên "ww" là **web wrapper**, không phải Web Worker — bundle chạy trên main thread của trang.

---

## 2. Kiến trúc

```
metaticket-home (Angular, domain metaticket.com.vn / staging.metaticket.vn)
│
│ AppComponent.ngOnInit(): appendScript(async)
│   ├─ {metaDomain}/modules/viclass/ww/mfe.webrt.js      → global viclass.mfeRT
│   └─ {metaDomain}/modules/metaticket/ww/planner.wrapper.js → global metaticket.*
│
│ SeatPlanComponent: new metaticket.PlannerWrapper(edType)
▼
┌──────────────────────────── planner.wrapper.js ────────────────────────────┐
│ PlannerWrapper                                                              │
│   └─ AppStateService                                                         │
│        ├─ mfeConf$ ← response (model.ts: URL module + settings, build-time)  │
│        ├─ viclass.mfeRT.loadRemoteModule(coordinator.embed)                 │
│        └─ new EmbedCoordinator({ edLookups: [SeatPlanner, PolicyDesc] })     │
└───────────────┬──────────────────────────────────────────────────────────────┘
                │ Module Federation (remote, nạp lúc runtime)
     ┌──────────▼───────────┐        ┌──────────────────────────────┐
     │ /modules/viclass/mfe │        │ /modules/metaticket/          │
     │ coordinator.embed.js │        │ editor.seatplanner/           │
     │ (@viclass)           │        │ editor.seatplanner.js         │
     └──────────────────────┘        │ (SeatPlannerEditor, disableEdit)│
                                     └──────────────┬───────────────┘
                                                    │ HTTP (axios)
                                     /api/public/editor/seatplanner/loadDoc?globalId=
                                                    │ nginx (không auth)
                                     service.editor.seatplanner → MongoDB `seatplan`
```

Điểm quan trọng: bản thân `planner.wrapper.js` chỉ ~27KB (rxjs + glue code). Toàn bộ logic render nằm ở `editor.seatplanner.js` và `coordinator.embed.js`, được nạp **sau** khi `initialize()` được gọi. Vì vậy thay đổi cách render/chọn ghế thường nằm ở `editor.seatplanner` (bundle MFE), không phải ở `ww`.

---

## 3. Public API

**File:** `ts/packages/ww/metaticket/ww/src/planner.wrapper.ts`

### 3.1 `PlannerWrapper`

```typescript
class PlannerWrapper {
    state: AppStateService
    globalDocId: string | undefined

    constructor(eType: PlanEditorType)                 // "SeatPlanner" | "PolicyDesc"

    initialize(el: HTMLDivElement, globalDocId: string): Promise<SPDoc state>
    controllable(): boolean
    registerListener(globalDocId: string, listener: VEventListener<PlanViewEvent>): Promise<void>
    setDisabled(disabled: boolean, globalDocId: string, itemIds: string[]): Promise<void>
    clearSelection(globalDocId: string, itemIds?: string[]): Promise<void>
    startZoom(zoomMode: ZoomMode): Promise<void>       // "zoom-in" | "zoom-out"
    startPan(): Promise<void>
    clearTool(): Promise<void>
}
```

| Method | Hành vi |
|--------|---------|
| `constructor(eType)` | Tạo `AppStateService(eType)`. Coordinator chưa được tạo cho tới khi `initialize()` chạy. |
| `initialize(el, globalDocId)` | Đẩy config MFE vào `mfeConf$` → nạp `coordinator.embed` qua `viclass.mfeRT.loadRemoteModule` → `coord.initialize()` + `start()` → `createViewport(el, edType)` → `switchViewportMode('EditMode')` → `enableAllTools(viewport, "common")` → `loadDocOnViewport({ edType, gId: globalDocId })` → `selectDoc`. Với `SeatPlanner`, zoom mặc định 25% (`SEAT_PLANNER_DEFAULT_ZOOM_LEVEL = 4`, vì `ZoomTool` nhận nghịch đảo của %). Trả `docCtrl.state`. Ném `Error` nếu không tìm thấy doc ctrl. |
| `controllable()` | `true` nếu viewport của editor type có `type: 'board'` (SeatPlanner); `false` cho `inline` (PolicyDesc). |
| `registerListener(gId, listener)` | Đăng ký listener vào `PlanDocCtrl.viewEE`. Listener là object có method `onEvent(event)`. Ném lỗi nếu doc chưa load. |
| `setDisabled(disabled, gId, ids)` | Bật/tắt trạng thái disabled cho ghế/zone. **`ids` là `sid` (mã ghế/zone)**, không phải id số nội bộ — xem mục 5.3. |
| `clearSelection(gId, ids?)` | Bỏ chọn các `sid` trong `ids`, hoặc bỏ chọn tất cả nếu không truyền. |
| `startZoom(mode)` | Focus tool `zoom` của common toolbar với `zoomMode`, blur doc ctrl (click lúc này là zoom, không chọn ghế). |
| `startPan()` | Focus tool `pan`, blur doc ctrl. |
| `clearTool()` | Bỏ focus mọi tool, select lại doc → click quay về chọn ghế. |

Mọi method (trừ `constructor`, `controllable`) đều `await` `coordinator$`/`viewport$` (ReplaySubject), nên gọi trước khi `initialize()` xong sẽ **chờ** chứ không lỗi ngay; nhưng nếu `initialize()` chưa bao giờ được gọi thì promise treo vĩnh viễn.

### 3.2 `AppStateService`

Giữ trạng thái coordinator cho một instance wrapper:

| Thành viên | Mô tả |
|-----------|-------|
| `mfeConf$` | `ReplaySubject<MFEConfResponse>` — nhận `response` từ `model.ts` khi `initialize()` |
| `editorList` | `['SeatPlanner', 'PolicyDesc']` — các editor được truyền vào `edLookups` |
| `edConf$`, `coordModuleLookup$` | Tách lookup editor và lookup `EmbedCoordinator` từ `mfeConf$` |
| `coordinator$`, `viewport$` | `ReplaySubject(1)` phát coordinator/viewport khi sẵn sàng |
| `boardType` | `'board'` / `'inline'` theo `settings.viewports[edType].type` |

Coordinator được tạo với `id: "metaticket-embed-coord"` và merge `settings` của lookup `EmbedCoordinator`. **Mỗi `new PlannerWrapper()` tạo một coordinator mới** — trang hiển thị nhiều sơ đồ cùng lúc sẽ có nhiều coordinator; storefront hiện chỉ hiển thị một.

### 3.3 Cấu hình module (`model.ts`) — chốt lúc build

```typescript
const endPoint = process.env['END_POINT'] ?? window.location.origin
const editorSeatplannerEndPoint = process.env['EDITOR_SEATPLANNER_END_POINT'] ?? `${endPoint}/api`
const editorPolicydescEndPoint  = process.env['EDITOR_POLICYDESC_END_POINT']  ?? `${endPoint}/api`
```

| Item | `remoteEntry` | Settings chính |
|------|---------------|----------------|
| `EmbedCoordinator` | `${endPoint}/modules/viclass/mfe/coordinator.embed.js` | `editorTypeMapping` (SeatPlanner id/channelCode 4012, PolicyDesc 4011 — **mã editor của viclass, không phải port mạng**), `viewports` (SeatPlanner `board`, PolicyDesc `inline`), `tools: []` |
| `SeatPlanner` | `${endPoint}/modules/metaticket/editor.seatplanner/editor.seatplanner.js` | `apiUri: ${…}/public/editor/seatplanner`, `docViewMode: 'full-viewport'`, `disableEdit: true`, `disableJsHighlight: false`, `disableDeselectOnEscape: true` |
| `PolicyDesc` | `${endPoint}/modules/metaticket/editor.seatplanner/editor.policydesc.js` | `apiUri: ${…}/public/editor/policydesc`, các cờ giống trên |

- Bản phát hành hiện tại build bằng `ww:dist` **không có file `.env.prod`**, nên `dotenv-webpack` thay `process.env` bằng stub và mọi URL rơi về **`window.location.origin`** (đã kiểm trong `ts/dist/metaticket/ww/planner.wrapper.js`).
- Hệ quả: URL của coordinator/editor/API được tính theo **origin của trang đang nhúng**, không phải origin nơi tải `planner.wrapper.js`. Storefront chạy cùng domain với `/modules/...` và `/api/...` (nginx phục vụ cả hai) nên hoạt động; nếu nhúng từ domain khác thì phải build lại với `END_POINT` (và CORS — nginx đã thêm `Access-Control-Allow-Origin *` cho `/modules`).
- `PolicyDesc` vẫn còn trong config nhưng storefront không còn dùng (enum `EdType` của storefront đã comment `PolicyDesc`).

### 3.4 Đăng ký global

Webpack build với `output.library = { name: "metaticket", type: "assign-properties" }` (`webpack.default.js`): sau khi module chạy, **mọi export** của entry (`PlannerWrapper`, `AppStateService`) được gán vào `window.metaticket`, giữ lại thuộc tính có sẵn. Đây là cơ chế thực sự làm `metaticket.PlannerWrapper` tồn tại.

Cuối `planner.wrapper.ts` còn đoạn:

```typescript
Object.assign(window.metaticket, { PlannerWrapper: typeof PlannerWrapper })
```

`typeof PlannerWrapper` ở đây là toán tử JavaScript → chuỗi `"function"`. Giá trị này bị export của webpack ghi đè ngay sau đó nên không gây lỗi, nhưng **đừng dựa vào dòng này**: nếu đổi `library.type` hoặc bỏ export, `metaticket.PlannerWrapper` sẽ thành chuỗi.

---

## 4. Sự kiện (listener)

Listener nhận `PlanViewEvent` (định nghĩa ở `@metaticket/editor.common` → `common.model.ts`), phát từ `SPDocCtrl` (`editor.seatplanner/src/lib/seatplanner.doc.ctrl.ts`):

```typescript
type PlanViewEventType = "item-lowlight" | "item-highlight" | "item-selected" | "item-deselected" | "item-clicked"

interface PlanViewEventState {
    items: PlanItem[]                 // Seat/SeatZone (có sid, cat, displayName...)
    controlEls?: HTMLOrSVGElement[]
    triggerItem?: PlanItem            // phần tử gây ra sự kiện
    triggerEl?: HTMLOrSVGElement      // <g> của phần tử đó — dùng để định vị popup
}
```

| Sự kiện | Khi nào | `state` |
|---------|---------|---------|
| `item-highlight` | Hover vào ghế/zone không bị disabled | `items: [obj]`, `controlEls` |
| `item-lowlight` | Rời hover | `items: [obj]` |
| `item-selected` | Tập chọn có phần tử **mới** | `items` = toàn bộ đang chọn, `triggerItem`, `triggerEl` |
| `item-deselected` | Có phần tử bị bỏ khỏi tập chọn | `items` = các phần tử vừa bỏ, `triggerItem`, `triggerEl` |
| `item-clicked` | Khai báo trong type, hiện `SPDocCtrl` không phát | — |

Ghế/zone ở trạng thái disabled không phát highlight và không chọn được. Ở chế độ `disableEdit`, chọn một ghế/zone sẽ chọn luôn các item cùng `sid` (`objects/control.manager.ts`).

Cách storefront dùng (`metaticket-home/src/app/lib/components/seat-plan/seat-plan.component.ts`):

```typescript
declare let metaticket: any;

this._seatPlanner = new metaticket.PlannerWrapper(this.type);        // session.edType
this._seatPlanner.initialize(el, this.seatPlanId).then(() => {
  this._seatPlanner.registerListener(this.seatPlanId,
      { onEvent: (data) => this._listener(data) });                  // SeatPlanListener
  // disable mọi ticket có remCap <= 0
  this._seatPlanner.setDisabled(true, this.seatPlanId, soldOutSids);
});
```

- `item-selected` → hiển thị popup cạnh `triggerEl.getBoundingClientRect()`, ghép `triggerItem.sid` với `TicketResponse.sid` để lấy hạng vé/giá/số lượng còn.
- `item-deselected` cùng `sid` với popup → đóng popup; khác → phát ra component cha.
- Event handler chạy ngoài Angular zone, nên storefront bọc bằng `NgZone.run`.
- Polling tồn vé: `updateTicketsStatus()` gọi `setDisabled(false, …còn vé)` rồi `setDisabled(true, …hết vé)`.
- Nút zoom/pan của storefront gọi `startZoom('zoom-in' | 'zoom-out')`, `startPan()`, `clearTool()`.
- Trang chi tiết sự kiện dùng cùng component với `isView = true` (bỏ qua mọi sự kiện, chỉ xem).

DTO phía storefront: `src/app/lib/dto/seat-plan-event.ts` (`SeatPlanEvent`, `SeatPlanTriggerEvent`).

---

## 5. Hợp đồng DOM/CSS của sơ đồ được render

Sơ đồ là **SVG thường trong DOM của trang** (không có shadow DOM, không iframe), nằm trong phần tử `el` truyền vào `initialize()`. Renderer: `editor.seatplanner/src/lib/objects/renderer.ts`, `render.manager.ts`, `util.ts`.

### 5.1 Phần tử và class

| Phần tử | Selector | Ghi chú |
|---------|----------|---------|
| Mọi phần tử tương tác | `._s_c_[data-i-id][data-i-type]` | `markerClass = "_s_c_"`; `data-i-id` = id **số** nội bộ của item trong document; `data-i-type` = `Seat` / `SeatZone` / ... |
| Ghế | `g._s_c_.seat.s-<cat>` | `<cat>` là category (hạng vé) của ghế, rỗng → `s-default`. Bên trong: `<use href="#chair">` (hình ghế từ `<defs>`) + `<text>` (displayName hoặc sid) |
| Zone (khu vực đứng/vé không ghế) | `g._s_c_.sz-g.sz-<cat>` | Bên trong: `<path>` boundary + `<text>` label (class thêm từ `lstyle`) |
| Trạng thái disabled | `.disable` trên `<g>` ghế/zone | Do `setDisabled(true, …)` |
| Trạng thái hover/chọn | `.highlight` trên `<g>` ghế/zone | Hai class này loại trừ nhau (renderer `replace` class cũ) |
| Stylesheet của sơ đồ | `<style>` đầu SVG | Lấy từ `document.styleSheet` (lưu ở Mongo `seatplan`) |
| Định nghĩa hình | `<defs>` đầu SVG | Lấy từ `document.defs`, phải có symbol `#chair` |
| Ảnh nền | `<image>` | Từ `baseImgUrl` (thường `/seatplanner-image/...`) |

### 5.2 Biến CSS (quy ước của dữ liệu, không phải của code)

Màu sắc **không** hardcode trong renderer mà do stylesheet của từng sơ đồ quyết định. Toàn bộ sơ đồ trong dump `database/metaticket.seatplan.json` theo cùng quy ước biến CSS:

| Biến | Áp cho |
|------|--------|
| `--seat-color`, `--seat-outline-color`, `--seat-label-color` | Ghế (`.seat.s-<cat>`) |
| `--zone-color`, `--zone-outline-color`, `--zone-outline-stroke-width`, `--zone-label-color` | Zone (`.sz-g.sz-<cat>`) |

Stylesheet thường định nghĩa `.highlight` / `.disable` với `!important` (ví dụ `.highlight { --seat-color: …!important }`). Muốn tô màu riêng từ trang nhúng (ví dụ trạng thái khóa/tặng vé) thì dùng selector **cụ thể hơn** `.disable`/`.highlight` của stylesheet sơ đồ và đặt lại các biến trên, kèm `!important`; không sửa thuộc tính `fill` trực tiếp vì stylesheet sơ đồ có thể dùng biến ở nhiều chỗ.

### 5.3 Ba loại ID — dễ nhầm

| ID | Ở đâu | Dùng cho |
|----|-------|---------|
| `globalDocId` | `session.seatPlanId` (ObjectId của document `seatplan`) | `initialize`, mọi method của wrapper |
| `sid` | Thuộc tính `sid` của Seat/SeatZone; khớp `TicketResponse.sid` của `service.ticket` | `setDisabled`, `clearSelection`, ghép ghế ↔ vé |
| `data-i-id` | Attribute DOM, id số nội bộ của item | Chỉ để tra ngược DOM → item; **không** truyền vào API wrapper |

Một `sid` có thể ứng với nhiều item (ví dụ zone vẽ thành nhiều mảnh); `setDisabled`/`clearSelection` áp lên tất cả item cùng `sid`. Ghế/zone không có `sid` sẽ không bao giờ bị ảnh hưởng bởi hai hàm này.

---

## 6. Build

Script trong `ts/package.json`:

| Lệnh | Config webpack | Mode | Ghi chú |
|------|----------------|------|---------|
| `yarn ww:build` | `webpack.default.js` | development, `eval-cheap-source-map` | Không có `dotenv-webpack` |
| `yarn ww:build-local` | `webpack.local.js` | development | Thêm `Dotenv({ path: ./.env.local })` |
| `yarn ww:dist` | `webpack.prod.js` | production, Terser, không source map | Thêm `Dotenv({ path: ./.env.prod })` — **bản dùng để deploy** |
| `yarn ww:watch` / `ww:watch-local` | như trên + `--watch` | | |
| `yarn build` / `yarn dist` | gồm cả `ww:build` / `ww:dist` | | Chạy song song editors + editor.ui + ww + mfe |

Chi tiết `webpack.default.js`:

- Entry `planner.wrapper` → `ts/dist/metaticket/ww/planner.wrapper.js`; `ts-loader` sinh kèm `.d.ts`.
- `CopyPlugin` chép `package.json` vào output; `CleanWebpackPlugin` xóa output cũ trước mỗi lần build.
- `uniqueName: "@metaticket/ww"`, `scriptType: 'text/javascript'` (script thường, không phải ES module).

Điều kiện build:

- `ts/tsconfig.json` map `@metaticket/editor.common` → `dist/metaticket/editor.common` và `@viclass/*` → `local_modules/viclass/*`. Phải build `editor.common` trước (`yarn editor:build` hoặc `yarn dist`) thì `ww` mới resolve được type.
- Thư mục package **không có** `.env.local`/`.env.prod` trong repo; muốn đổi `END_POINT` thì tạo file này lúc build (không commit giá trị riêng của môi trường).
- `webpack.default.js` (dùng bởi `ww:build`) không thay `process.env`, nên đoạn `process.env['END_POINT']` trong `model.ts` không được xử lý lúc build — nhiều khả năng ném `ReferenceError: process is not defined` trên trình duyệt (chưa kiểm chứng). Chỉ deploy output của `ww:dist`.

---

## 7. Deploy và serve

### 7.1 Server (nginx)

Không có location riêng cho `ww`; nó được phục vụ bởi location chung trong frag `nginx/{prod,staging}/metaticket-frags/metaticket-modules`:

```nginx
location /modules/metaticket {
    add_header Access-Control-Allow-Origin *;
    alias <static-root>/modules/metaticket;
}
```

| Môi trường | File phải có |
|-----------|--------------|
| Production | `/opt/metaticket/static/modules/metaticket/ww/planner.wrapper.js` |
| Staging | `/staging/metaticket/static/modules/metaticket/ww/planner.wrapper.js` |

Frag này được include ở cả site storefront và site admin (xem [NGINX_CONFIGURATION.md](./NGINX_CONFIGURATION.md)). Các bundle phụ thuộc phải có cùng lúc: `modules/viclass/ww/mfe.webrt.js`, `modules/viclass/mfe/coordinator.embed.js`, `modules/metaticket/mfe/editor.seatplanner.js` (URL `/modules/metaticket/editor.seatplanner/...` được alias về thư mục `mfe`).

Quy trình upload chung cho module frontend: [FRONTEND_DEPLOYMENT.md](./FRONTEND_DEPLOYMENT.md). Lưu ý đường dẫn đích phải khớp alias nginx ở trên (`static/modules/...`, số nhiều).

Nginx không đặt `Cache-Control` cho `/modules`, và tên file `planner.wrapper.js` **không có hash**: sau khi deploy bản mới, trình duyệt/CDN có thể vẫn dùng bản cũ một thời gian. Khi thay đổi không tương thích ngược, cân nhắc purge cache CDN.

### 7.2 Local (`nginx-docker`)

`metaticket/nginx-docker` có toggle `CONF_MODULE_WW` (`sample.dev.env`, mặc định `module.ww.remote`):

- `module.ww.local` → `location /modules/metaticket/ww { alias /metaticket/ww/; }` — dùng bản build tại máy (mount `ts/dist/metaticket`).
- `module.ww.remote` → proxy tới dev server.

Xem [nginx-docker/README.md](../nginx-docker/README.md). Storefront chạy local phải truy cập qua `https://devlocal.metaticket.vn` (`environment.dev.ts` → `metaDomain`) để `window.location.origin` khớp với nơi phục vụ `/modules` và `/api`.

### 7.3 Cách storefront nạp script

`metaticket-home/src/app/app.component.ts`:

```typescript
protected readonly webrtUrl   = this.metaDomain + pathName.webrtUrl;    // /modules/viclass/ww/mfe.webrt.js
protected readonly plannerUrl = this.metaDomain + pathName.plannerUrl;  // /modules/metaticket/ww/planner.wrapper.js

ngOnInit(): void {
  if (!this._isBrowser) return;          // SSR: không chèn script
  this.appendScript(this.webrtUrl);
  this.appendScript(this.plannerUrl);
}
```

`appendScript` tạo `<script async>` trong `<head>`. `metaDomain` theo môi trường: `https://metaticket.com.vn` (production), `https://staging.metaticket.vn` (staging và mặc định), `https://devlocal.metaticket.vn` (dev). Đường dẫn ở `src/app/lib/constants/path-name.ts`.

---

## 8. Tương thích và rủi ro khi thay đổi

| Rủi ro | Chi tiết | Cách giảm |
|--------|---------|-----------|
| Không có version trong URL | Mọi phiên bản storefront đang mở đều nạp cùng `planner.wrapper.js` mới nhất | Chỉ **thêm** method/tham số tùy chọn; không đổi tên/chữ ký method hiện có (`initialize`, `registerListener`, `setDisabled`, `clearSelection`, `startZoom`, `startPan`, `clearTool`) |
| Storefront gọi qua `any` | `declare let metaticket: any` — sai chữ ký không bị TypeScript bắt | Đổi API phải rà `seat-plan.component.ts` và mọi component dùng `metaticket.PlannerWrapper` |
| Thứ tự nạp script | Hai script `async` có thể tải xong theo thứ tự bất kỳ; `viclass.mfeRT` chỉ cần có khi `initialize()` chạy, còn `metaticket` phải có khi component khởi tạo | Trang nhúng nên chờ `window.metaticket?.PlannerWrapper` (và `window.viclass`) trước khi khởi tạo nếu vào thẳng trang có sơ đồ |
| Hợp đồng DOM | Trang nhúng có thể tô màu theo `.seat`, `.sz-g`, `.s-<cat>`, `.disable`, `.highlight`, `data-i-id`, biến `--seat-*`/`--zone-*` | Đổi tên class/attribute trong `editor.seatplanner` là breaking change với storefront; ghi rõ khi release |
| Sự kiện | Storefront dựa vào `item-selected`/`item-deselected` + `triggerEl`, `triggerItem.sid` | Không đổi tên sự kiện/field; thêm field mới thì giữ field cũ |
| Bundle phụ thuộc deploy riêng | `ww`, `editor.seatplanner` (mfe), `coordinator.embed` (viclass) được copy lên server độc lập | Deploy `editor.seatplanner` mới phải tương thích với `ww` đang chạy và ngược lại; khi đổi interface chung (`editor.common`), deploy cả hai cùng lúc |
| URL theo origin trang | Mặc định dùng `window.location.origin` (mục 3.3) | Nhúng ở domain khác phải build với `END_POINT` |
| Nội dung sơ đồ được chèn bằng `innerHTML` | `styleSheet`, `defs`, displayName ghế được gán qua `innerHTML` trong `render.manager.ts`/`renderer.ts` | Dữ liệu sơ đồ chỉ do operator nhập qua portal; không bao giờ cho dữ liệu người dùng cuối chảy vào các field này |
| Mỗi instance một coordinator | Tạo `PlannerWrapper` mới mỗi lần component init; không có `destroy()` | Tránh tạo lại liên tục trên cùng trang; nếu cần nhiều lần, cân nhắc bổ sung API hủy |
| `console.log` còn sót | `registerListener` log `"register doc ctrl"`, coordinator log khi khởi động | Không ảnh hưởng chức năng |

---

## 9. Checklist khi sửa `ww`

1. Sửa `src/planner.wrapper.ts` / `src/model.ts`; giữ nguyên chữ ký public (mục 8).
2. `yarn editor:build` (nếu `editor.common` đổi) → `yarn ww:dist` trong `ts/`.
3. Kiểm tra `ts/dist/metaticket/ww/planner.wrapper.js` có `PlannerWrapper` trong export và URL module đúng ý (`END_POINT` hay `window.location.origin`).
4. Chạy local qua `nginx-docker` với `CONF_MODULE_WW=module.ww.local`, mở storefront tại `https://devlocal.metaticket.vn`, thử: tải sơ đồ, ghế hết vé bị disabled, chọn/bỏ chọn, popup, zoom/pan, trang chi tiết sự kiện (chế độ xem).
5. Upload `planner.wrapper.js` (+ `.d.ts`, `package.json`) lên `static/modules/metaticket/ww/` của staging trước, kiểm tra lại luồng đặt vé, rồi mới lên production. Giữ bản cũ để rollback (ví dụ `cp -a` thư mục `ww` trước khi ghi đè).
