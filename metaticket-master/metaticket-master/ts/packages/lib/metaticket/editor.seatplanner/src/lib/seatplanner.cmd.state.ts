import {BoundaryRectangle, CmdStateMessage, Position, Rectangle} from "@viclass/editor.core";
import {Seat} from "./seatplanner.model";
import {PlanItem, PlanItemType} from "@metaticket/editor.common";

const encoder = new TextEncoder()
const decoder = new TextDecoder()

function deserialize<T extends CmdStateMessage>(state : Uint8Array, initial : T) : T {
    let pojo = JSON.parse(decoder.decode(state))
    Object.assign(initial, pojo)

    return initial
}

abstract class SeatPlannerCmdState<T extends SeatPlannerCmdState<T>> implements CmdStateMessage {

    serializeBinary(): Uint8Array {
        return new Uint8Array(encoder.encode(JSON.stringify(this)))
    }

}

export class InsertDocState extends SeatPlannerCmdState<InsertDocState> {

    boundary? : BoundaryRectangle
    globalId? : string

    static deserializeBinary = (state : Uint8Array) => deserialize(state, new InsertDocState())
}


export class InsertLayerState extends SeatPlannerCmdState<InsertLayerState> {

    boundary? : BoundaryRectangle
    zIndex? : number

    static deserializeBinary = (state : Uint8Array) => deserialize(state, new InsertLayerState())

}

export class RemoveDocCmdState extends SeatPlannerCmdState<RemoveDocCmdState> {

    localId? : number

    static deserializeBinary = (state : Uint8Array) => deserialize(state, new RemoveDocCmdState())
}

export class UpdateDocBoundaryCmdState extends SeatPlannerCmdState<UpdateDocBoundaryCmdState> {

    boundary?: Rectangle
    sizeChanged? : boolean
    positionChanged? : boolean
    endPoint? : boolean

    static deserializeBinary = (state : Uint8Array) => deserialize(state, new UpdateDocBoundaryCmdState())
}


export class InsertSeatCmdState extends SeatPlannerCmdState<InsertSeatCmdState> {

    seats? : Seat[]

    static deserializeBinary = (state : Uint8Array) => deserialize(state, new InsertSeatCmdState())

}

export class StartItemPreviewCmdState extends SeatPlannerCmdState<StartItemPreviewCmdState> {

    newItem? : {
        itemType : PlanItemType
        clickPos : Position
        numCtrlPoint : number
        itemId : number
    }
    existingItem? : PlanItem    // we can start a preview with an existing item

    static deserializeBinary = (state : Uint8Array) => deserialize(state, new StartItemPreviewCmdState())
}

export class UpdateItemCmdState extends SeatPlannerCmdState<UpdateItemCmdState> {
    item? : PlanItem
    drawState? : string
    movingPoint? : number = -1
    movingShape? : number = -1

    static deserializeBinary = (state : Uint8Array) => deserialize(state, new UpdateItemCmdState())
}

export class CancelDrawingCmdState extends SeatPlannerCmdState<CancelDrawingCmdState> {

    itemIds : number[] = []

    static deserializeBinary = (state : Uint8Array) => deserialize(state, new CancelDrawingCmdState())
}

export class SaveItemsCmdState extends SeatPlannerCmdState<SaveItemsCmdState> {

    added : PlanItem[] = []
    updated : PlanItem[] = []
    removed : PlanItem[] = []

    static deserializeBinary = (state : Uint8Array) => deserialize(state, new SaveItemsCmdState())
}
