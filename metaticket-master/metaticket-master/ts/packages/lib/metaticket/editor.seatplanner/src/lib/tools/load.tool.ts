import {
    FEATURE_SELECTION,
    SingleSelectionEvent,
    ToolState,
    VEventListener,
    ViewportContentEvent
} from "@viclass/editor.core";
import {SeatPlannerTool} from "./seatplanner.tool";
import {SeatPlannerToolType} from "./tool.model";
import {SeatPlannerToolBar} from "./seatplanner.toolbar";
import {SeatPlannerGateway} from "../seatplanner.gateway";
import {SPDocCtrl} from "../seatplanner.doc.ctrl";

export class LoadTool extends SeatPlannerTool<LoadToolState> {
    override toolType: SeatPlannerToolType = "loadtool";
    override toolState: LoadToolState = new LoadToolState();
    override mouseHandling = undefined;
    override keyboardHandling = undefined;

    curFocused? : SPDocCtrl

    private contentListener? : VEventListener<ViewportContentEvent>

    constructor(tb : SeatPlannerToolBar, private gw : SeatPlannerGateway) {
        super(tb)
    }

    override onViewportAttached(): void {
        this.tb.viewport.contentEventEmitter.registerListener(this.getContentListener())
    }

    override onViewportDetached(): void {
        this.tb.viewport.contentEventEmitter.unregisterListener(this.getContentListener())
    }

    async submitStyle(style : string) {
        if (!this.curFocused) return
        await this.gw.submitStyle(this.curFocused.state.globalId, style)
    }

    async submitDef(def : string) {
        if (!this.curFocused) return
        await this.gw.submitDef(this.curFocused.state.globalId, def)
    }

    async saveDocName(name: string) {
        if (!this.curFocused) return

        await this.gw.saveDocName(this.curFocused.state.globalId, name)

        // update local state
        this.curFocused.state.doc.displayName = name
        this.toolState!.docName = name
        this.tb.update(this.toolType, this.toolState!)
    }

    async saveCategories(categories: string[]) {
        if (!this.curFocused) return

        await this.gw.saveCategories(this.curFocused.state.globalId, categories)

        // update local state
        this.curFocused.state.doc.catNames = categories
        this.toolState!.categories = categories
        this.tb.update(this.toolType, this.toolState!)
    }

    async uploadImage(file: File) {
        if (!this.curFocused) return

        let res = await this.gw.uploadImage(this.curFocused.state.globalId, file)
        if (res && res.url) {
            await this.updateBaseImgUrlLocal(res.url)
        }
    }

    async updateBaseImgUrl(url: string) {
        if (!this.curFocused) return

        await this.gw.updateBaseImgUrl(this.curFocused.state.globalId, url)
        await this.updateBaseImgUrlLocal(url)
    }

    private async updateBaseImgUrlLocal(url: string) {
        if (!this.curFocused) return

        this.curFocused.state.doc.baseImgUrl = url
        this.toolState!.baseImgUrl = url
        this.tb.update(this.toolType, this.toolState!)

        // Re-render the image
        this.curFocused.renderMgr.renderImageUrl(url)
    }

    getContentListener() : VEventListener<ViewportContentEvent> {
        if (!this.contentListener) this.contentListener = this.generateContentListener()
        return this.contentListener
    }

    private generateContentListener() : VEventListener<ViewportContentEvent> {
        let self = this
        return new (class implements VEventListener<ViewportContentEvent> {
            onEvent(eventData: ViewportContentEvent): ViewportContentEvent | Promise<ViewportContentEvent> {

                if (eventData.state?.source == FEATURE_SELECTION &&
                    eventData.state!.originalEvent.eventType == "doc-selected"
                ) {
                    let originalEvent = eventData.state!.originalEvent as SingleSelectionEvent

                    if (originalEvent.state?.selectCtx.doc && originalEvent.state?.selectCtx.doc instanceof SPDocCtrl) {
                        self.curFocused = originalEvent.state?.selectCtx.doc

                        // Update tool state from document
                        self.toolState!.docName = self.curFocused.state.doc.displayName || ""
                        self.toolState!.categories = self.curFocused.state.doc.catNames || []
                        self.toolState!.baseImgUrl = self.curFocused.state.doc.baseImgUrl || ""
                        self.tb.update(self.toolType, self.toolState!)
                    }
                }

                return eventData
            }
            onUnregister?: (() => void) | undefined;

        })()
    }

}

export class LoadToolState implements ToolState {
    docName: string = ""
    categories: string[] = []
    baseImgUrl: string = ""
}
