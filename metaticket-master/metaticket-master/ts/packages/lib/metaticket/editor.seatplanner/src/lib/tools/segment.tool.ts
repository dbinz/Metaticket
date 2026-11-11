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
import {SeatPlannerToolBar} from "./seatplanner.toolbar";
import {SPDocCtrl} from "../seatplanner.doc.ctrl";
import {SeatLine, SeatLineCalParam, SeatLineNaming} from "../seatplanner.model";
import {SeatPlannerCmdType, StartItemPreviewCmd} from "../seatplanner.cmd";
import {seatPlannerObjectReg} from "../util";
import {CurveStateMachine} from "../objects/curve.state.machine";
import {ElementSelectionEvent} from "@metaticket/editor.common";

export class SegmentTool extends SeatPlannerTool<SegmentToolState> {

    readonly toolType = 'segment'
    override toolState = new SegmentToolState();
    override mouseHandling: MouseHandlingItem[] = [];
    override keyboardHandling: KeyboardHandlingItem[] = [];

    override mouseHandler = this.mouseHandlerFactory()

    hasMouseDown : boolean = false

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

    generateSeatsForSelectedLine(calParam : SeatLineCalParam) {
        let focused = this.tb.editor.getFocusedDocs(this.tb.viewport.id)
        if (!focused || focused.length != 1) throw new Error("No document selected")
        let curves = focused[0].objCtrlMgr.getControlling()

        if (curves.size > 1) throw new Error("Must select only a single line to generate seat")

        for (let m of curves.values()) {
            if (m instanceof CurveStateMachine)
                m.generateSeats(calParam)
        }
    }

    generateSeatNamesForSelectedLine(naming : SeatLineNaming, lineName : string, seatCat : string) {
        let focused = this.tb.editor.getFocusedDocs(this.tb.viewport.id)
        if (!focused || focused.length != 1) throw new Error("No document selected")
        let curves = focused[0].objCtrlMgr.getControlling()

        if (curves.size > 1) throw new Error("Must select only a single line to generate seat")

        for (let m of curves.values()) {
            if (m instanceof CurveStateMachine)
                m.updateNaming(lineName, naming, seatCat)
        }
    }

    createStartNewPreviewCommand(docCtrl : SPDocCtrl, pos : Position) : StartItemPreviewCmd {
        let meta = reliableCmdMeta(docCtrl.viewport, docCtrl.state.id, docCtrl.state.id, SeatPlannerCmdType.START_ITEM_PREVIEW)
        let cmd = new StartItemPreviewCmd(meta)
        let objId = this.tb.editor.regMan.registry(seatPlannerObjectReg(docCtrl.viewport.id, docCtrl.state.id)).getAndIncrementId()

        cmd.setNewItem(objId, pos, "SeatLine", this.toolState.numCtrlPoint)

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

                        self.refreshStateData(items[0] as SeatLine, doc);
                    }
                }

                return eventData;
            }
            onUnregister?: (() => void) | undefined;

        })()
    }

    override onFocus(){
        super.onFocus();
        this.refreshStateDataFromSelectedLine()
    }

    refreshStateData(item: SeatLine, doc : SPDocCtrl) {
        Object.assign(this.toolState.seatCal, item.seatCal)
        this.toolState.lineName = item.lineName
        this.toolState.seatCat = item.seatCat || ""
        Object.assign(this.toolState.naming, item.seatNaming)
    }

    refreshStateDataFromSelectedLine() {
        let focused = this.tb.editor.getFocusedDocs(this.tb.viewport.id)
        if (!focused || focused.length != 1) throw new Error('No document selected');
        let curves = focused[0].objCtrlMgr.getControlling();

        if (curves.size > 1) return;

        if (curves.size == 1) {
            for (let m of curves.values()) {
                if (m instanceof CurveStateMachine) {
                    this.refreshStateData(m.item as SeatLine, focused[0]);
                    break;
                }
            }
        }
        this.toolState.availableCats = [...focused[0].state.doc.catNames]
        this.tb.update(this.toolType, this.toolState)
    }

}


export class SegmentToolState implements ToolState {

    numCtrlPoint : number = 0
    seatCal : SeatLineCalParam = {
        capacity : 0,
        start: 0,
        end: 0
    }

    lineName : string = ""

    naming : SeatLineNaming = {
        start: 0,
        end: 0
    }

    seatCat : string = ""
    availableCats : string[] = [""]

    constructor() {}

}
