import { FEATURE_SELECTION, KeyboardHandlingItem, MouseEventData, MouseEventListener, MouseHandlingItem, Position, ToolState, VEventListener, ViewportContentEvent, mouseLocation, reliableCmdMeta } from "@viclass/editor.core";
import { SeatPlannerTool } from "./seatplanner.tool";
import { SeatPlannerToolBar } from "./seatplanner.toolbar";
import { StartItemPreviewCmd, SeatPlannerCmdType } from "../seatplanner.cmd";
import { SPDocCtrl } from "../seatplanner.doc.ctrl";
import { seatPlannerObjectReg } from "../util";
import { ElementSelectionEvent } from "@metaticket/editor.common";
import { SeatZone } from "../seatplanner.model";
import { ZoneStateMachine } from "../objects/zone.state.machine";

export class SeatZoneTool extends SeatPlannerTool<SeatZoneToolState> {
    readonly toolType = 'seatzone'
    override toolState = new SeatZoneToolState();
    override mouseHandling: MouseHandlingItem[] = [];
    override keyboardHandling?: KeyboardHandlingItem[] = [];

    override mouseHandler = this.mouseHandlerFactory()

    constructor(tb : SeatPlannerToolBar) {
        super(tb)
        this.mouseHandling.push( { event: "click", button: 0, keys:['nokey'] } )
        this.mouseHandling.push( { event: "click", button: 0, keys:['shift'] } )
        this.mouseHandling.push( { event: "mousemove", button: 0, keys:['nokey'] } )
        this.mouseHandling.push( { event: "mousemove", button: 0, keys:['shift'] } )
        this.mouseHandling.push( { event: "mouseup", button: 0, keys:['nokey'] } )
        this.mouseHandling.push( { event: "mouseup", button: 0, keys:['shift'] } )
        this.mouseHandling.push( { event: "mousedown", button: 0, keys:['nokey'] } )
        this.mouseHandling.push( { event: "mousedown", button: 0, keys:['shift'] } )

        this._contentListener = this.contentListener()
    }

    mouseHandlerFactory() {
        let self = this
        return new (class implements MouseEventListener<any> {

            onEvent(eventData: MouseEventData<any>): MouseEventData<any> | Promise<MouseEventData<any>> {

                let focused = self.tb.editor.getFocusedDocs(self.tb.viewport.id)
                if (!focused || focused.length != 1) throw new Error("No document selected")

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

    createStartNewPreviewCommand(docCtrl : SPDocCtrl, pos : Position) : StartItemPreviewCmd {
        let meta = reliableCmdMeta(docCtrl.viewport, docCtrl.state.id, docCtrl.state.id, SeatPlannerCmdType.START_ITEM_PREVIEW)
        let cmd = new StartItemPreviewCmd(meta)
        let objId = this.tb.editor.regMan.registry(seatPlannerObjectReg(docCtrl.viewport.id, docCtrl.state.id)).getAndIncrementId()

        cmd.setNewItem(objId, pos, "SeatZone", this.toolState.numCtrlPoint)

        return cmd
    }

    contentListener() : VEventListener<ViewportContentEvent> {
        let self = this
        return new (class implements VEventListener<ViewportContentEvent> {
            onEvent(eventData: ViewportContentEvent): ViewportContentEvent {
                // when there is a selected document

                if (eventData.state) {
                    let source = eventData.state.source;
                    if (source == FEATURE_SELECTION) {
                        // if this event comes from select tool
                        // check if there are multiple selected document, if yes, need to
                        let focused = self.tb.editor.getFocusedDocs(eventData.source.id);
                        if (focused && focused?.length > 1) {
                            // disable tool
                        }
                    } else if (source == SPDocCtrl.name) {
                        let items = (eventData.state.originalEvent as ElementSelectionEvent).state!.items;
                        let doc = (eventData.state.originalEvent as ElementSelectionEvent).source as SPDocCtrl;

                        if (items.length == 1 && items[0].type == 'SeatZone') {
                            self.refreshStateData(items[0] as SeatZone, doc);
                        }
                    }
                }

                return eventData;
            }
            onUnregister?: (() => void) | undefined;
        })();
    }

    override onFocus(){
         super.onFocus();
        let focused = this.tb.editor.getFocusedDocs(this.tb.viewport.id)
        if (!focused || focused.length != 1) throw new Error("No document selected")
        this.toolState.availableCats = [...focused[0].state.doc.catNames]

        this.tb.update(this.toolType, this.toolState)
    }

    refreshStateData(item: SeatZone, doc : SPDocCtrl) {
        this.toolState.displayName = item.label
        this.toolState.seatCat = item.cat || ""
        this.toolState.seatId = item.sid
        this.toolState.labelRot = item.lrot
        this.toolState.labelStyle = item.lstyle
        this.tb.update(this.toolType, this.toolState)
    }

    updateFocusedFromState(cs : SeatZoneToolState) {

        let focused = this.tb.editor.getFocusedDocs(this.tb.viewport.id)
        if (!focused || focused.length != 1) throw new Error("No document selected")
        let machines = focused[0].objCtrlMgr.getControlling()

        if (machines.size > 1) throw new Error("Must select only a single seat to update content")

        for (let m of machines.values()) {
            if (m instanceof ZoneStateMachine)
                m.updateSeatZoneFromToolState(cs)
        }

    }

    updateZoneConfig(cs : SeatZoneToolState) {
        this.toolState.numCtrlPoint = cs.numCtrlPoint
    }
}

export class SeatZoneToolState implements ToolState {

    numCtrlPoint : number = 0
    seatId : string = ""

    displayName : string = ""
    labelRot? : number  // label rotation
    labelStyle? : string // additional style for a seat zone

    seatCat : string = ""
    availableCats : string[] = [""]

    constructor() {}

}
