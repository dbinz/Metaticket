import {
    FEATURE_SELECTION,
    KeyboardHandlingItem,
    MouseEventData,
    MouseEventListener,
    MouseHandlingItem,
    mouseLocation,
    Position,
    reliableCmdMeta,
    ToolState,
    VEventListener,
    ViewportContentEvent
} from "@viclass/editor.core";
import {SeatPlannerTool} from "./seatplanner.tool";
import {SPDocCtrl} from "../seatplanner.doc.ctrl";
import {SeatPlannerToolBar} from "./seatplanner.toolbar";
import {SeatPlannerCmdType, StartItemPreviewCmd} from "../seatplanner.cmd";
import {seatPlannerObjectReg} from "../util";
import {Seat} from "../seatplanner.model";
import {SeatStateMachine} from "../objects/seat.state.machine";
import {ElementSelectionEvent} from "@metaticket/editor.common";

export class SeatTool extends SeatPlannerTool<SeatToolState> {
    readonly toolType = 'seat'
    override toolState = new SeatToolState();
    override mouseHandling: MouseHandlingItem[] = [];
    override keyboardHandling: KeyboardHandlingItem[] = [];

    override mouseHandler = this.mouseHandlerFactory()

    constructor(tb : SeatPlannerToolBar) {
        super(tb)
        this.mouseHandling.push( { event: "click", button: 0, keys:['nokey'] } )
        this.mouseHandling.push( { event: "mousemove", button: 0, keys:['nokey'] } )
        this.mouseHandling.push( { event: "mouseup", button: 0, keys:['nokey'] } )
        this.mouseHandling.push( { event: "mousedown", button: 0, keys:['nokey'] } )
        this._contentListener = this.contentListener()
    }

    mouseHandlerFactory() {
        let self = this
        return new (class implements MouseEventListener<any> {

            onEvent(eventData: MouseEventData<any>): MouseEventData<any> | Promise<MouseEventData<any>> {

                let focused = self.tb.editor.getFocusedDocs(self.tb.viewport.id)
                if (!focused || focused.length != 1) return eventData
                self.tb.mouseHandler.onEvent(eventData)

                if (!eventData.continue) return eventData

                switch (eventData.nativeEvent.type) {
                    case 'click': {

                        if (focused[0].checkHit(eventData, focused[0].layer!))
                            return eventData // if hit something, don't start new, instead let the select tool handle

                        let source = eventData.source
                        if (source == focused[0].layer) {
                            let pos = focused[0].layer!.svgPos(mouseLocation(eventData))
                            let cmd = self.createStartNewPreviewCommand(focused[0], pos)
                            eventData.continue = false
                            self.tb.editor.cmdChannel.receive(cmd)
                        }


                        break;
                    }
                }


                return eventData
            }

            onUnregister?: (() => void) | undefined;

        })()
    }

    contentListener() : VEventListener<ViewportContentEvent> {
        let self = this
        return new (class implements VEventListener<ViewportContentEvent> {
            onEvent(eventData: ViewportContentEvent): ViewportContentEvent {
                // when there is a selected document

                if (eventData.state) {
                    let source = eventData.state.source
                    if (source == FEATURE_SELECTION) {  // if this event comes from select tool
                        // check if there are multiple selected document, if yes, need to
                        let focused = self.tb.editor.getFocusedDocs(eventData.source.id)
                        if (focused && focused?.length > 1) {
                            // disable tool
                        }
                    } else if (source == SPDocCtrl.name) {
                        let items = (eventData.state.originalEvent as ElementSelectionEvent).state!.items

                        if (items.length == 1 && items[0].type == "Seat") {
                            self.refreshStateData(items[0] as Seat)
                        }
                    }
                }

                return eventData
            }
            onUnregister?: (() => void) | undefined;

        })()
    }

    createStartNewPreviewCommand(docCtrl : SPDocCtrl, pos : Position) : StartItemPreviewCmd {
        let meta = reliableCmdMeta(docCtrl.viewport, docCtrl.state.id, docCtrl.state.id, SeatPlannerCmdType.START_ITEM_PREVIEW)
        let cmd = new StartItemPreviewCmd(meta)
        let objId = this.tb.editor.regMan.registry(seatPlannerObjectReg(docCtrl.viewport.id, docCtrl.state.id)).getAndIncrementId()

        cmd.setNewItem(objId, pos, "Seat", 0)

        return cmd
    }

    refreshStateData(item: Seat) {
        this.toolState.seatId = item.sid
        this.toolState.displayName = item.displayName || ""

        this.tb.update(this.toolType, this.toolState)
    }

    updateFocusedFromState(cs : SeatToolState) {

        let focused = this.tb.editor.getFocusedDocs(this.tb.viewport.id)
        if (!focused || focused.length != 1) throw new Error("No document selected")
        let machines = focused[0].objCtrlMgr.getControlling()

        if (machines.size > 1) throw new Error("Must select only a single seat to update content")

        for (let m of machines.values()) {
            if (m instanceof SeatStateMachine)
                m.updateSeatFromToolState(cs)
        }

    }

}

export class SeatToolState implements ToolState {

    seatId : string = ""
    displayName : string = ""

    constructor() {}

}
