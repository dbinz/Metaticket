# Tài liệu Kỹ thuật - Editor System (Frontend)

## 1. Tổng quan

Editor System là hệ thống các thư viện frontend (TypeScript/Angular) trong monorepo MetaTicket, dùng để xây dựng giao diện vẽ và chỉnh sửa sơ đồ chỗ ngồi (seat map) cho các sự kiện bán vé.

### Trạng thái hiện tại

| Package | Trạng thái | Mô tả |
|---------|-----------|-------|
| `@metaticket/editor.common` | ✅ Active | Thư viện base classes dùng chung cho mọi editor |
| `@metaticket/editor.seatplanner` | ✅ Active | Core logic cho editor vẽ sơ đồ chỗ ngồi |
| `@metaticket/editor.seatplanner.ui` | ✅ Active | Angular UI component cho seatplanner editor |
| `@metaticket/editor.policydesc` | ❌ Deprecated | Editor mô tả chính sách, trước đây dùng trong frontend (Next.js), **không còn support** |
| `@metaticket/portal.seatplanner` | ❌ Deprecated | Portal standalone cũ để vẽ seat map, **không còn sử dụng** |
| `portal.admin > seat_map` | ✅ Active | Module vẽ seat map hiện tại, nằm trong portal.admin |

> **Lưu ý quan trọng**:
> - `editor.policydesc` trước đây được sử dụng trong project `frontend/` (Next.js) nhưng feature frontend đã không còn được support. Tất cả chức năng đã chuyển qua homepage.
> - `portal.seatplanner` (standalone app cũ) đã **không còn được sử dụng**. Môi trường vẽ seat map hiện tại là module `seat_map` trong `portal.admin`.

---

## 2. Kiến trúc tổng quan

```
┌────────────────────────────────────────────────────────────────────────┐
│              Portal Admin (Angular App - portal.admin)                   │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  seat_map module (portal.admin/src/app/metaticket/seat_map)       │ │
│  │                                                                    │ │
│  │  ┌─────────────────────┐  ┌──────────────────────────────────┐   │ │
│  │  │ SeatMapListComponent│  │ SeatMapDrawComponent (standalone) │   │ │
│  │  │ (Danh sách & CRUD)  │  │ (Giao diện vẽ seat map)          │   │ │
│  │  └─────────────────────┘  └───────────────┬──────────────────┘   │ │
│  │                                            │                      │ │
│  │  ┌────────────────────────────────────────┐│                      │ │
│  │  │ SeatMapService (seat-map.service.ts)   ││                      │ │
│  │  │ → Load MFE config, init coordinator   ││                      │ │
│  │  └───────────────────────────────────────┘│                      │ │
│  └────────────────────────────────────────────┼──────────────────────┘ │
└───────────────────────────────────────────────┼────────────────────────┘
                                                │
          ┌─────────────────────────────────────┼──────────────────────┐
          │            Micro Frontend Runtime (MFE)                      │
          │        Webpack Module Federation - Dynamic Remote Loading    │
          └────────────────────┬──────────────────────────┬─────────────┘
                               │                          │
                    ┌──────────▼──────────┐    ┌──────────▼──────────────┐
                    │ editor.seatplanner  │    │ editor.seatplanner.ui   │
                    │   (Core Logic)      │    │   (Angular UI)           │
                    │                     │    │                          │
                    │ • SeatPlannerEditor │    │ • SeatplannerUiComponent │
                    │ • Toolbar & Tools   │    │ • Tool panels (forms)    │
                    │ • Command System    │    │ • factory() export       │
                    │ • Gateway (API)     │    │                          │
                    └──────────┬──────────┘    └──────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   editor.common     │
                    │  (Shared Base)      │
                    │                     │
                    │ • PlanEditor        │
                    │ • PlanDoc / Layer   │
                    │ • PlanItem model    │
                    └─────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │  @viclass/editor.*  │
                    │  (External Core)    │
                    │                     │
                    │ • EditorCoordinator │
                    │ • ViewportManager   │
                    │ • CmdChannel        │
                    │ • SelectionFeature  │
                    └─────────────────────┘
```

---

## 3. Packages chi tiết

### 3.1. `@metaticket/editor.common` (v1.0.0)

**Đường dẫn**: `ts/packages/lib/metaticket/editor.common`

Thư viện cung cấp các abstract classes và interfaces dùng chung cho tất cả các editor trong hệ thống.

#### Exports chính

| File | Nội dung |
|------|---------|
| `common.editor.ts` | `PlanEditor` - abstract class implement `DocumentEditor`, `SupportSelectFeature`, `SupportInternalSelectFeature` |
| `common.model.ts` | `PlanItem`, `PlanDocument`, `PlanItemType`, `PlanViewEvent` interfaces |
| `common.doc.ts` | `PlanDoc` - base document class implement `VDoc` |
| `common.layer.ts` | `PLanLayer` - base layer class |
| `common.doc.ctrl.ts` | `PlanDocCtrl` - base document controller |
| `util.ts` | Utility functions dùng chung |

#### Data Model

```typescript
type PlanItemType = "Seat" | "Label" | "SeatLine" | 
                    "SeatZone" | "SeatZoneConstructionPreview" | 
                    "ControlPoint" | "ItemPlaceHolder"

interface PlanItem {
    id: number
    type: PlanItemType
}

interface PlanDocument {
    id: string
    styleSheet: string
    items: PlanItem[]
    version: number
}
```

---

### 3.2. `@metaticket/editor.seatplanner` (v3.0.0)

**Đường dẫn**: `ts/packages/lib/metaticket/editor.seatplanner`

Core logic library cho editor vẽ sơ đồ chỗ ngồi. Package này **không phụ thuộc Angular** - chỉ là pure TypeScript.

#### Cấu trúc thư mục

```
editor.seatplanner/src/lib/
├── objects/
│   ├── control.manager.ts       # Quản lý các object controller (selection, drag)
│   ├── control.state.machine.ts # Base state machine cho control
│   ├── curve.state.machine.ts   # State machine cho SeatLine (bezier curve)
│   ├── render.manager.ts        # Quản lý rendering SVG
│   ├── renderer.ts              # SVG renderer
│   ├── seat.state.machine.ts    # State machine cho Seat
│   └── zone.state.machine.ts    # State machine cho SeatZone
├── tools/
│   ├── load.tool.ts             # Tool load/save document, quản lý metadata
│   ├── seat.tool.ts             # Tool tạo/chỉnh sửa ghế đơn
│   ├── seatplanner.tool.ts      # Base class cho tools
│   ├── seatplanner.toolbar.ts   # Toolbar chính, quản lý mouse/keyboard events
│   ├── seatzone.tool.ts         # Tool tạo/chỉnh sửa vùng ghế (zone)
│   ├── segment.tool.ts          # Tool tạo/chỉnh sửa dãy ghế (seat line)
│   └── tool.model.ts            # Type definitions cho tools
├── seatplanner.api.ts           # UIBehaviorCtrl interface
├── seatplanner.cmd.ts           # Command definitions (INSERT, UPDATE, DELETE, etc.)
├── seatplanner.cmd.processor.ts # Xử lý commands
├── seatplanner.cmd.state.ts     # Command state tracking
├── seatplanner.doc.ts           # SPDoc - SeatPlanner Document state
├── seatplanner.doc.ctrl.ts      # SPDocCtrl - Document controller (selection, hit detection, rendering)
├── seatplanner.editor.ts        # SeatPlannerEditor - Main editor class
├── seatplanner.gateway.ts       # HTTP Gateway (axios) gọi backend API
├── seatplanner.model.ts         # Data models (Seat, SeatLine, SeatZone, etc.)
└── util.ts                      # Registry key helpers
```

#### Classes chính

##### SeatPlannerEditor

Class chính của editor, extends `PlanEditor`:

```typescript
interface SeatPlannerConfig extends EditorConfig {
    disableEdit: boolean           // Chế độ chỉ xem (read-only)
    enableViewEvent: boolean       // Bật event hiển thị
    disableJsHighlight: boolean    // Tắt JS-based highlight
    disableDeselectOnEscape: boolean // Tắt deselect khi nhấn Escape
}
```

**Chức năng chính:**
- Tạo/load document từ backend
- Quản lý viewport và layer cho SVG rendering
- Hỗ trợ Selection, History features
- Tạo toolbar với các tools
- Command system (CmdChannel) để xử lý thao tác người dùng

##### SeatPlannerToolBar

Toolbar quản lý input handling (mouse + keyboard):
- Click ngoài vùng control → Save + Cancel
- Escape → Cancel drawing
- Delegate click events cho active tool

##### Tools

| Tool | Type Key | Chức năng |
|------|----------|-----------|
| **SeatTool** | `seat` | Tạo ghế đơn bằng cách click trên canvas. Cho phép chỉnh sửa Seat ID và Display Name |
| **SegmentTool** | `segment` | Tạo dãy ghế (SeatLine) bằng bezier curve. Generate ghế tự động theo capacity, đặt tên theo range |
| **SeatZoneTool** | `seatzone` | Tạo vùng ghế (zone) bằng boundary. Cho phép config label, category, rotation |
| **LoadTool** | `loadtool` | Quản lý document: đổi tên, categories, upload ảnh nền, submit style/defs |

##### Data Models

```typescript
interface SeatPlanDocument extends PlanDocument {
    displayName: string
    baseImage?: Uint8Array
    baseImgUrl: string         // URL ảnh nền sơ đồ
    defs: string               // SVG <defs> definitions
    catNames: string[]         // Danh sách tên category
}

interface Seat extends PlanItem {
    pos: Position
    sid: string                // Seat ID (mã ghế)
    displayName: string
    cat: string                // Category name
    rotation: number
}

interface SeatLine extends PlanItem {
    line: BezierCurve          // Đường bezier curve định vị dãy ghế
    seats: number[]            // IDs của các ghế trên dãy
    seatCal?: SeatLineCalParam // Thông số tạo ghế
    seatNaming?: SeatLineNaming // Thông số đặt tên
    lineName: string           // Tên dãy (row ID)
    seatCat?: string           // Category cho ghế trên dãy
}

interface SeatZone extends PlanItem {
    sid: string
    boundary: Boundary         // Vùng boundary bao quanh zone
    label: string              // Label hiển thị
    cat: string                // Category
    lrot?: number              // Label rotation
    lstyle?: string            // Label style class
}
```

##### SeatPlannerGateway

HTTP client giao tiếp với backend Kotlin service:

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| `createDoc()` | `GET /createDoc` | Tạo document mới |
| `loadDoc(globalId)` | `GET /loadDoc?globalId=` | Load document theo ID |
| `submitStyle(globalId, style)` | `POST /saveDocStyle?globalId=` | Lưu CSS stylesheet |
| `submitDef(globalId, def)` | `POST /saveDocDef?globalId=` | Lưu SVG definitions |
| `saveDocName(globalId, name)` | `POST /saveDocName?globalId=` | Đổi tên document |
| `saveCategories(globalId, cats)` | `POST /saveCategories?globalId=` | Lưu danh sách categories |
| `uploadImage(globalId, file)` | `POST /uploadImage?globalId=` | Upload ảnh nền |
| `updateBaseImgUrl(globalId, url)` | `POST /updateBaseImgUrl?globalId=` | Cập nhật URL ảnh nền |

---

### 3.3. `@metaticket/editor.seatplanner.ui` (v3.0.0)

**Đường dẫn**: `ts/packages/lib/metaticket/editor.seatplanner.ui`

Angular UI component cung cấp giao diện tương tác cho SeatPlanner Editor. Được load dưới dạng remote module qua Webpack Module Federation.

#### Entry Point (MFE)

```typescript
// ts/packages/mfe/metaticket/mfe/src/editor.seatplanner.ui.ts
import { factory as seatPlannerUIFactory } from "@metaticket/editor.seatplanner.ui"
export default seatPlannerUIFactory
```

#### Factory Function

```typescript
export function factory(
    viewContainerRef: ViewContainerRef, 
    injector: Injector
): Promise<ComponentRef<EditorUIComponent>> {
    let module = createNgModule(SeatplannerUiModule, injector)
    return new Promise((rs) => {
        let t = runInInjectionContext(module.injector, () => 
            viewContainerRef.createComponent(SeatplannerUiComponent, {
                environmentInjector: module,
                injector: injector,
            })
        )
        rs(t)
    })
}
```

#### SeatplannerUiComponent

Angular component implement `EditorUIComponent` interface. Quản lý:

- **Toolbar UI**: Hiển thị buttons S (Seat), SL (Segment), SZ (SeatZone), L (Load)
- **Tool panels**: Form inputs tương ứng cho mỗi tool active
- **State management**: RxJS observables cho trạng thái từng tool

**Observables chính:**

| Observable | Mô tả |
|-----------|-------|
| `spToolBar$` | Reference đến SeatPlannerToolBar |
| `activeTool$` | Tool đang active |
| `segment$` | State của SegmentTool (capacity, naming, line name) |
| `seat$` | State của SeatTool (seat id, display name) |
| `seatzone$` | State của SeatZoneTool (zone id, category, rotation) |
| `load$` | State của LoadTool (doc name, categories, background) |

**Giao diện UI cho từng tool:**

1. **Load Tool Panel**: Đổi tên document, quản lý categories, upload/set ảnh nền, import style/defs file
2. **Segment Tool Panel**: Nhập capacity/margin → tạo ghế trên line; nhập range start/end/step → đặt tên
3. **Seat Tool Panel**: Chỉnh sửa Seat ID và Display Name cho ghế đang chọn
4. **SeatZone Tool Panel**: Chỉnh sửa Zone ID, label, category, rotation, style class

---

### 3.4. Seat Map Module (trong portal.admin) — MÔI TRƯỜNG VẼ HIỆN TẠI

**Đường dẫn**: `ts/packages/portal/metaticket/portal.admin/src/app/metaticket/seat_map`

Đây là module **hiện đang được sử dụng** để vẽ seat map, nằm trong portal.admin (ứng dụng quản trị chính).

#### Cấu trúc

```
seat_map/
├── app.model.ts              # MFE configuration (remote module entries)
├── seat_map.module.ts        # NgModule, routing definitions
├── seat-map.service.ts       # SeatMapService - init coordinator & manage state
├── draw/
│   ├── seat_map_draw.component.ts    # Component vẽ seat map (standalone)
│   ├── seat_map_draw.component.html  # Template với viewport + side panel
│   └── seat_map_draw.component.scss  # Styles
└── list/
    ├── seat_map_list.component.ts    # Danh sách seat maps (CRUD)
    ├── seat_map_list.component.html  # Template smart table
    └── seat_map_list.component.scss  # Styles
```

#### Routing

| Path | Component | Mô tả |
|------|-----------|-------|
| `seat-map/list` | SeatMapListComponent | Danh sách tất cả seat maps |
| `seat-map/draw` | SeatMapDrawComponent | Tạo seat map mới |
| `seat-map/draw/:id` | SeatMapDrawComponent | Mở và chỉnh sửa seat map theo ID |

Tất cả routes đều được bảo vệ bởi `MetaticketAuthGuardService`.

#### SeatMapService

Service quản lý state cho editor, tương tự `AppStateService` trong portal cũ:

```typescript
@Injectable({ providedIn: 'root' })
export class SeatMapService {
    edType = 'SeatPlanner'
    
    mfeConf$: ReplaySubject<MFEConfResponse>   // MFE configuration
    edConf$                                     // Editor lookups
    edUIConf$                                   // Editor UI lookups
    zoomUIConf$                                 // Zoom tools UI config
    commonUIConf$                               // Common tools UI config
    coordModuleLookup$                          // Coordinator module lookup
    coordinator$: ReplaySubject<EmbedCoordinator>  // Coordinator instance
    
    initialize()  // Emit MFE config → trigger coordinator loading
}
```

**Flow khởi tạo:**
1. `initialize()` → emit `response` vào `mfeConf$`
2. `coordModuleLookup$` + `edConf$` combine → load remote EmbedCoordinator module
3. Tạo coordinator instance với config → `initialize()` → `start()`
4. Emit coordinator vào `coordinator$`

#### SeatMapDrawComponent (Standalone Component)

Component chính để vẽ seat map. Là **standalone component** (không cần NgModule):

```typescript
@Component({
    standalone: true,
    selector: '[draw-seatmap]',
    imports: [EditorUILoaderModule, CommonModule, FormsModule, ...]
})
export class SeatMapDrawComponent implements AfterViewInit { ... }
```

**Lifecycle:**
1. `ngAfterViewInit()` → `seatmapService.initialize()`
2. Chờ coordinator ready → `initializeUI(coord)`
3. Kiểm tra route params: nếu có `:id` → tự động `loadDoc()`

**Chức năng:**
- `initializeUI(coord)`: Tạo viewport, load UI (zoom, common tools, editor UI), switch EditMode, enable tools
- `createDoc()`: Tạo document mới trên viewport
- `loadDoc()`: Load document theo `loadId` (từ input hoặc route param)
- `focusDoc()`: Focus vào document đã load, emit `doc$` cho side panel

**Giao diện:**
- **Bên trái (chính)**: Canvas viewport chứa SVG editor + UI toolbar overlays
- **Bên phải (sidebar 300px)**: 
  - Khi chưa load: Input để nhập ID + nút "Mở Seat Map"
  - Khi đã load: Hiển thị thông tin document (ID, Name, Image URL, Categories)

#### SeatMapListComponent

Component quản lý danh sách seat maps:

**Chức năng:**
- Hiển thị bảng danh sách tất cả seat maps (dùng `angular2-smart-table`)
- Nút **"Tạo mới"**: Gọi `MetaticketSeatplannerService.createSeatPlan()` → tạo seat map mới trên server
- Nút **"Vẽ"** (mỗi hàng): Navigate đến `/portal/seat-map/draw/:id`
- Nút **"Xóa"** (mỗi hàng): Dialog xác nhận → gọi `deleteSeatPlan(id)`

**Columns hiển thị:**
| Column | Mô tả |
|--------|-------|
| ID | ID của seat map |
| Tên | Display name |
| Categories | Danh sách categories (join bởi dấu phẩy) |

#### MFE Configuration (app.model.ts)

Config cho remote modules, API endpoint dùng `privateApi('/editor/seatplanner')`:

| Module | Remote Entry | Chức năng |
|--------|-------------|-----------|
| `editor.seatplanner` | `/modules/metaticket/editor.seatplanner/editor.seatplanner.js` | Core editor logic |
| `editor.seatplanner.ui` | `/modules/metaticket/editor.seatplanner.ui/editor.seatplanner.ui.js` | Editor UI panels |
| `editor.seatplanner.ui.style` | `/modules/metaticket/themes/editor.seatplanner.ui/editor.seatplanner.ui.style.js` | Editor UI styles |
| `coordinator.embed` | `/modules/viclass/mfe/coordinator.embed.js` | Điều phối editors |
| `editorui.commontools` | `/modules/viclass/mfe/editorui.commontools.js` | Common toolbar UI |

---

## 4. Command System

Editor sử dụng **Command Pattern** để xử lý mọi thao tác chỉnh sửa:

```
User Action → Tool creates Command → CmdChannel.receive(cmd) → CmdProcessor processes → State update + Gateway call
```

### Command Types (`SeatPlannerCmdType`)

| Command | Mô tả |
|---------|-------|
| `INSERT_DOC` | Tạo document mới |
| `INSERT_LAYER` | Thêm layer cho document |
| `REMOVE_DOC` | Xóa document |
| `START_ITEM_PREVIEW` | Bắt đầu preview item mới (trước khi save) |
| `SAVE_ITEMS` | Lưu items (add/update/delete) |
| `CANCEL_DRAWING` | Hủy thao tác vẽ hiện tại |
| `UPDATE_DOC_BOUNDARY` | Cập nhật boundary của document |

---

## 5. Rendering Architecture

### SVG-based Rendering

- Document được render trên **SVG layer** (BoundedSVGLayerCtrl hoặc UnboundedSVGLayerCtrl)
- `RenderManager` quản lý việc render items lên SVG
- `ObjectCtrlManager` quản lý state machines cho từng item (cho phép drag, resize)

### State Machines

Mỗi loại plan item có state machine riêng:
- `SeatStateMachine` - Quản lý state cho ghế đơn
- `CurveStateMachine` - Quản lý state cho bezier curve (seat line)
- `ZoneStateMachine` - Quản lý state cho seat zone

### Hit Detection

Khi user click trên canvas:
1. `SPDocCtrl.checkHit()` kiểm tra target element có class `.marker` và `data-iId`
2. Nếu hit → trigger selection event
3. Nếu không hit → tool tạo item mới tại vị trí click

---

## 6. External Dependencies

| Package | Mô tả |
|---------|-------|
| `@viclass/editor.core` | Framework editor core: EditorCoordinator, ViewportManager, CmdChannel, Feature system |
| `@viclass/editorui.loader` | UI loading framework: EditorUILoaderComponent, EditorUIComponent interface |
| `@viclass/editor.coordinator/embed` | EmbedCoordinator: điều phối nhiều editors trên cùng viewport |
| `@viclass/ww/mfeRT` | MFE Runtime: loadRemoteModule cho Webpack Module Federation |
| `@nebular/theme` | UI component library (NbCard, NbButton, NbToastr,...) - dùng trong portal.admin |
| `angular2-smart-table` | Data table component - dùng hiển thị danh sách seat maps |

---

## 7. Flow sử dụng (User Flow)

### Tạo seat map mới

1. Admin vào `/portal/seat-map/list`
2. Click **"Tạo mới"** → gọi API tạo seat map trên server
3. Seat map mới xuất hiện trong bảng
4. Click icon **Vẽ** → navigate đến `/portal/seat-map/draw/:id`
5. Editor tự load document, hiển thị canvas trống (hoặc có ảnh nền)
6. Sử dụng tools: Segment (tạo dãy ghế), Seat (ghế đơn), SeatZone (vùng ghế), Load (quản lý metadata)

### Chỉnh sửa seat map

1. Từ danh sách, click icon **Vẽ** → mở editor với seat map đã có dữ liệu
2. Hoặc nhập trực tiếp ID vào input trên trang draw
3. Sử dụng tools để thêm/sửa/xóa ghế, dãy ghế, vùng ghế
4. Dữ liệu tự động lưu qua Gateway API khi user hoàn thành thao tác (save on action)

---

## 8. Cách tích hợp Editor vào module mới

### Bước 1: Import dependencies

```typescript
import { EditorUILoaderModule } from '@viclass/editorui.loader';
import { SeatMapService } from './seat-map.service';
```

### Bước 2: Khởi tạo coordinator

```typescript
// Trong component
ngAfterViewInit() {
    this.seatmapService.initialize();
    this.seatmapService.coordinator$.pipe(
        tap(coord => this.initializeUI(coord))
    ).subscribe();
}
```

### Bước 3: Tạo viewport và load UI

```typescript
async initializeUI(coord: EmbedCoordinator) {
    let viewport = coord.createViewport(this.viewportElement, 'SeatPlanner');
    
    // Load UI modules
    loader.createUI([commonUIConf, editorUIConf]);
    
    // Enable edit mode
    coord.switchViewportMode(viewport.id, 'EditMode');
    coord.enableAllTools(viewport.id, 'common');
    coord.enableAllTools(viewport.id, 'editor');
}
```

### Bước 4: Connect toolbar khi UI loaded

```typescript
onEditorUILoaderEvent(event: EditorUILoaderEvent) {
    if (event.eventType === 'ui-loaded') {
        let loadedUI = event.source.getUI(event.state!);
        let tb = coord.getEditorToolbar(viewport.id);
        loadedUI.connectToolbar(tb);
        event.source.showUI(event.state!);
    }
}
```

### API integration

Backend API base URL: `privateApi('/editor/seatplanner')` (resolve từ environment config).

Tất cả API calls sử dụng `withCredentials: true` để gửi cookie authentication.

---

## 9. Build & Development

### Build editor.seatplanner.ui styles

```bash
npx tailwindcss -c ./tailwind.config.js -o ./style.scss
```

### Package versions

| Package | Version |
|---------|---------|
| `@metaticket/editor.common` | 1.0.0 |
| `@metaticket/editor.seatplanner` | 3.0.0 |
| `@metaticket/editor.seatplanner.ui` | 3.0.0 |

### Deprecated packages (không sử dụng)

- `@metaticket/editor.policydesc` (v1.0.0) — từng dùng trong `frontend/` (Next.js)
- `@metaticket/portal.seatplanner` — portal standalone cũ, đã chuyển vào portal.admin
