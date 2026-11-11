import {
    CoordinatorEvent,
    CoordinatorEventType,
    DefaultToolBar,
    KeyboardEventData,
    KeyboardEventListener,
    KeyboardHandlingItem,
    MouseEventData,
    MouseEventListener,
    MouseHandlingItem,
    reliableCmdMeta,
    reliableSaveCmdMeta,
    ToolEventData,
    ToolEventListener,
    ToolState,
    UserInputHandlerType,
    VEventListener,
    ViewportManager,
} from "@viclass/editor.core";
import {SeatPlannerTool} from "./seatplanner.tool";
import {SeatPlannerEditor} from "../seatplanner.editor";
import {SeatPlannerToolType} from "./tool.model";
import {AbstractCmd, CancelDrawingCmd, SaveItemsCmd, SeatPlannerCmdType} from "../seatplanner.cmd";
import {SPDocCtrl} from "../seatplanner.doc.ctrl";
import {PlanItem} from "@metaticket/editor.common";


export type SeatPlannerToolEventData = ToolEventData<SeatPlannerToolBar, SeatPlannerToolType>

/**
 * Toolbar handle all common actions that tools needs
 *
 * 1. Handling escape key
 * 2. Handling clicking outside of any controlling machines
 * 3. Handling matching machine - this used to be inside each individual tool, but that is a bit not intiutive for user because the tool needs to be active
 * before it can be used
 *
 */
export class SeatPlannerToolBar extends DefaultToolBar<SeatPlannerToolType, SeatPlannerTool<ToolState>> {

    override mouseHandler = this.mouseHandlerFactory()
    override keyboardHandler = this.keyboardHandlerFactory()
    toolListener?: ToolEventListener<SeatPlannerToolBar, any>
    coordEventListener?: VEventListener<CoordinatorEvent>
    override keyboardHandling: KeyboardHandlingItem[] = []
    override mouseHandling: MouseHandlingItem[] = [];
    override type : UserInputHandlerType = "Toolbar" 

    constructor(public editor: SeatPlannerEditor) {
        super();

        if (!editor.conf.disableEdit) { // only in edit mode then we handle these actions
            this.mouseHandling.push( { event: "click", button: 0, keys:['nokey'] } )
            this.mouseHandling.push( { event: "click", button: 0, keys:['shift'] } )
            this.mouseHandling.push( { event: "mousemove", button: 0, keys:['nokey'] } )
            this.mouseHandling.push( { event: "mousemove", button: 0, keys:['shift'] } )
            this.mouseHandling.push( { event: "mouseup", button: 0, keys:['nokey'] } )
            this.mouseHandling.push( { event: "mouseup", button: 0, keys:['shift'] } )
            this.mouseHandling.push( { event: "mousedown", button: 0, keys:['nokey'] } )
            this.mouseHandling.push( { event: "mousedown", button: 0, keys:['shift'] } )
        }

        this.keyboardHandling.push( { event: "keyup", keys: ["esc"], global: false })
    }

    override attachViewport(viewport: ViewportManager): void {
        super.attachViewport(viewport)
        this.coordEventListener = new this._coordEventListener(this)
        this.editor.coord.registerCoordEventListener(this.coordEventListener)

        for (let t of this.tools.values()) {
            t.onViewportAttached(viewport)
        }

    }

    override detachViewport(viewport: ViewportManager): void {
        super.detachViewport(viewport)
        if (this.coordEventListener) {
            this.editor.coord.unregisterCoordEventListener(this.coordEventListener)
        }

        for (let t of this.tools.values()) {
            t.onViewportDetached(viewport)
        }
    }

    mouseHandlerFactory() {
        let self = this
        return new (class implements MouseEventListener<any> {

            onEvent(eventData: MouseEventData<any>): MouseEventData<any> | Promise<MouseEventData<any>> {

                let focused = self.editor.getFocusedDocs(self.viewport.id)
                if (!focused || focused.length != 1) return eventData
                let machines = focused[0].objCtrlMgr.getControlling()
                let cmd : AbstractCmd<any> | undefined = undefined

                if (machines) {
                    for (let m of machines.values()) {
                        let match = m.matchEvent(eventData)
                        if (match === 1) {
                            eventData.continue = false
                            return eventData
                        } else if (match) {
                            self.editor.cmdChannel.receive(match)
                            eventData.continue = false
                            return eventData
                        }
                    }
                }

                switch (eventData.nativeEvent.type) {
                    case 'click': {

                        if (machines.size > 0) {    // we don't allow multiple selection with click and no ctrl
                            self.sendSaveCommandForControlling(focused[0])
                            self.sendCancelCommand(focused[0])

                            eventData.continue = false
                        } else {
                            if (focused[0].checkHit(eventData, focused[0].layer!))
                                return eventData // if hit something, don't start new, instead let the select tool handle
                        }

                        break;
                    }
                }


                return eventData
            }

            onUnregister?: (() => void) | undefined;

        })()
    }

    keyboardHandlerFactory() {
        let self = this
        return new (class implements KeyboardEventListener<any> {

            onEvent(eventData: KeyboardEventData<any>): KeyboardEventData<any> | Promise<KeyboardEventData<any>> {

                let focused = self.editor.getFocusedDocs(self.viewport.id)
                if (!focused || focused.length != 1) return eventData

                if (!self.editor.conf.disableDeselectOnEscape) {    // if user disable deselect using esc, then don't do anything
                    if (!self.editor.conf.disableEdit)
                        self.sendSaveCommandForControlling(focused[0])

                    self.sendCancelCommand(focused[0])
                }

                return eventData
            }

            onUnregister?: (() => void) | undefined;

        })()
    }

    sendSaveCommandForControlling(doc : SPDocCtrl) {
        let machines = doc.objCtrlMgr.getControlling()

        if (machines.size > 0) {

            let forAdd : PlanItem[] = []
            let forUpdate : PlanItem[] = []

            for (let m of machines.values()) {
                if (m.isSaveable()) {

                    if (m.isNewItem())
                        forAdd.push(m.item!)
                    else forUpdate.push(m.item!)
                }
            }

            let meta = reliableSaveCmdMeta(doc.viewport, doc.state, doc.state.id, doc.state.id, SeatPlannerCmdType.SAVE_ITEMS)
            let cmd = new SaveItemsCmd(meta)
            cmd.setItems(forAdd, forUpdate, [])

            this.editor.cmdChannel.receive(cmd)
        }
    }

    sendCancelCommand(doc : SPDocCtrl) {

        let meta = reliableCmdMeta(doc.viewport, doc.state.id, doc.state.id, SeatPlannerCmdType.CANCEL_DRAWING)
        let cmd = new CancelDrawingCmd(meta)

        this.editor.cmdChannel.receive(cmd)

    }

    private _coordEventListener = class implements VEventListener<CoordinatorEvent> {

        constructor(private toolbar: SeatPlannerToolBar) {
        }

        onEvent(eventData: CoordinatorEvent): CoordinatorEvent | Promise<CoordinatorEvent> {

            let eType = eventData.eventType as CoordinatorEventType
            let eventState = eventData.state

            if (!this.toolbar.viewport || eventState!.vmId != this.toolbar.viewport.id) return eventData

            switch (eType) {
                case "viewport-removed": {
                    this.toolbar.detachViewport(this.toolbar.viewport)
                    break;
                }
            }

            return eventData
        }

    }
}
