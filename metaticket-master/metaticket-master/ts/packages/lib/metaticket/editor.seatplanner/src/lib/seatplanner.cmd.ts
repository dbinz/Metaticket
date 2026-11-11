import {
    AbstractCommand,
    BoundaryRectangle,
    Cmd,
    CmdMeta,
    CmdOriginType,
    CmdStateMessage,
    CmdStateMessageStatic,
    Position,
    Rectangle
} from "@viclass/editor.core";
import {
    CancelDrawingCmdState,
    InsertDocState,
    InsertLayerState,
    InsertSeatCmdState,
    RemoveDocCmdState,
    SaveItemsCmdState,
    StartItemPreviewCmdState,
    UpdateDocBoundaryCmdState,
    UpdateItemCmdState
} from "./seatplanner.cmd.state";
import {Seat, SeatPlanDocument} from "./seatplanner.model";
import {PlanItem, PlanItemType} from "@metaticket/editor.common";


export enum SeatPlannerCmdType {
    INSERT_DOC = 0,
    INSERT_LAYER = 1,
    REMOVE_DOC = 2,
    UPDATE_DOC_BOUNDARY = 3,

    INSERT_SEAT = 4,
    START_ITEM_PREVIEW = 5,
    UPDATE_ITEM = 6,
    CANCEL_DRAWING = 7,
    SAVE_ITEMS = 8
}

export class AbstractCmd<T extends CmdStateMessage> extends AbstractCommand<SeatPlannerCmdType> {
    override state : T

    constructor(meta : CmdMeta, private stateClass : CmdStateMessageStatic<T>) {
        super(meta)
        this.cmdType = meta.cmdType
        this.state = new this.stateClass()
    }

    override serialize(): Uint8Array {
        return this.state.serializeBinary()
    }

    override deserialize(buf: Uint8Array) : T {
        return this.stateClass.deserializeBinary(buf)
    }
}

export class InsertDocCmd extends AbstractCmd<InsertDocState> {

    constructor(meta : CmdMeta, public localCtrlState? : SeatPlanDocument) {
        super(meta, InsertDocState)

        if (meta.origin == CmdOriginType.local && localCtrlState == null)
            throw new Error("Local command to create new document must include the local state!")
    }

    setState(boundary : BoundaryRectangle, globalId : string) {
        this.state.boundary = boundary
        this.state.globalId = globalId
    }
}

export class InsertLayerCmd extends AbstractCmd<InsertLayerState> {

    constructor(meta : CmdMeta) {
        super(meta, InsertLayerState)
    }

    setState(boundary : BoundaryRectangle, zIndex : number) {
        this.state.boundary = boundary
        this.state.zIndex = zIndex
    }

}

export class RemoveDocCmd extends AbstractCmd<RemoveDocCmdState> {

    constructor(meta : CmdMeta) {
        super(meta, RemoveDocCmdState)
    }

    setState(localId : number) {
        this.state.localId = localId
    }

}

export class UpdateDocBoundaryCmd extends AbstractCmd<UpdateDocBoundaryCmdState> {

    constructor(meta : CmdMeta) {
        super(meta, RemoveDocCmdState)
    }

    setState(boundary: Rectangle, endPoint: boolean = false, sizeChanged: boolean = false, positionChanged: boolean = false) {
        this.state.boundary = boundary
        this.state.sizeChanged = sizeChanged
        this.state.positionChanged = positionChanged
        this.state.endPoint = endPoint
    }
}

export class InsertSeatCmd extends AbstractCmd<InsertSeatCmdState> {
    constructor(meta : CmdMeta) {
        super(meta, RemoveDocCmdState)
    }

    setState(seats: Seat[]) {
        this.state.seats = seats
    }
}

export class StartItemPreviewCmd extends AbstractCmd<StartItemPreviewCmdState> {
    constructor(meta : CmdMeta) {
        super(meta, StartItemPreviewCmdState)
    }

    setExistingItem(item : PlanItem) {
        this.state.existingItem = item
    }

    setNewItem(id : number, pos : Position, itemType : PlanItemType, numCtrlPoint : number) {
        this.state.newItem = {
            itemType : itemType,
            clickPos : pos,
            itemId: id,
            numCtrlPoint : numCtrlPoint
        }
    }
}

export class UpdateItemCmd extends AbstractCmd<UpdateItemCmdState> {

    constructor(meta : CmdMeta) {
        super(meta, UpdateItemCmdState)
    }

    setItem(item : PlanItem, nextState : string) {
        this.state.item = item
        this.state.drawState = nextState
    }
}


export class CancelDrawingCmd extends AbstractCmd<CancelDrawingCmdState> {

    constructor(meta : CmdMeta) {
        super(meta, CancelDrawingCmdState)
    }

    setItems(cancelled : PlanItem[]) {
        this.state.itemIds = cancelled.map(v => v.id)
    }

    setItemIds(cancelledIds : number[]) {
        this.state.itemIds = cancelledIds
    }
}

export class SaveItemsCmd extends AbstractCmd<SaveItemsCmdState> {

    constructor(meta : CmdMeta) {
        super(meta, SaveItemsCmdState)
    }

    setItems(forAdded : PlanItem[], forUpdate : PlanItem[], forRemoved : PlanItem[]) {
        this.state.added = forAdded
        this.state.updated = forUpdate
        this.state.removed = forRemoved
    }

}

export function deserializer(meta : CmdMeta, stateData : Uint8Array) : Cmd<SeatPlannerCmdType> {
    let cmd : Cmd<SeatPlannerCmdType>
    let cmdType = meta.cmdType as SeatPlannerCmdType

    switch(cmdType) {

        case SeatPlannerCmdType.INSERT_DOC:
            cmd = new InsertDocCmd(meta)
            break
        case SeatPlannerCmdType.INSERT_LAYER:
            cmd = new InsertLayerCmd(meta)
            break
        case SeatPlannerCmdType.INSERT_SEAT:
            cmd = new InsertSeatCmd(meta)
            break
        case SeatPlannerCmdType.START_ITEM_PREVIEW:
            cmd = new StartItemPreviewCmd(meta)
            break

        default: throw new Error(`invalid cmd type: ${cmdType}`);

    }

    let state = cmd.deserialize(stateData)
    cmd.state = state

    return cmd
}
