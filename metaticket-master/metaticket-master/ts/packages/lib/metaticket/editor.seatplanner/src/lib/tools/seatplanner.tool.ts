import {SeatPlannerToolBar} from "./seatplanner.toolbar";
import {SeatPlannerToolType} from "./tool.model";
import {
    KeyboardEventListener,
    KeyboardHandlingItem,
    MouseEventListener,
    MouseHandlingItem,
    NativeEventTarget,
    Tool,
    ToolBar,
    ToolState,
    TouchEventListener,
    TouchHandlingItem,
    UserInputHandlerType,
    VEventListener,
    ViewportContentEvent,
    ViewportManager
} from "@viclass/editor.core";

export abstract class SeatPlannerTool<TState extends ToolState> implements Tool {

    abstract readonly toolType : SeatPlannerToolType

    childToolbar?: ToolBar<any, Tool> | undefined;
    abstract toolState?: ToolState | undefined;
    abstract mouseHandling?: MouseHandlingItem[] | undefined;
    abstract keyboardHandling?: KeyboardHandlingItem[] | undefined;
    _contentListener? : VEventListener<ViewportContentEvent>

    constructor(protected tb : SeatPlannerToolBar) {}
    type: UserInputHandlerType = "Tool";
    touchHandling?: TouchHandlingItem[] | undefined;
    cursor?: string | undefined;
    keyboardHandler?: KeyboardEventListener<NativeEventTarget<any>> | undefined;
    mouseHandler?: MouseEventListener<NativeEventTarget<any>> | undefined;
    touchHandler?: TouchEventListener<NativeEventTarget<any>> | undefined;

    onBlur() {}
    onFocus() {}

    onViewportAttached(vm : ViewportManager): void {
        if (this._contentListener)
            vm.contentEventEmitter.registerListener(this._contentListener)
    }

    onViewportDetached(vm: ViewportManager): void {
        if (this._contentListener)
            vm.contentEventEmitter.unregisterListener(this._contentListener)
    }
}
