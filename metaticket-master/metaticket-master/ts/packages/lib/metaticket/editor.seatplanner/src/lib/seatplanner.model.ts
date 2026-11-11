import {PlanDocument, PlanItem} from "@metaticket/editor.common"
import {Position} from "@viclass/editor.core"

export interface SeatPlanDocument extends PlanDocument{
    displayName : string
    baseImage?: Uint8Array
    baseImgUrl : string
    defs : string
    catNames : string[]
}

export type PrimitiveType = "Position" | "BezierCurve" | "Boundary"

export interface Primitive {

}

export interface PrimitiveWithType {
    type : PrimitiveType
}

export interface BezierCurve extends Primitive, PrimitiveWithType {
    p : Position[]
}


export interface Boundary extends Primitive, PrimitiveWithType {
    e : BezierCurve[]
}

export interface Seat extends PlanItem {
    pos : Position
    sid : string
    displayName : string
    cat : string
    rotation : number
    controlItem? : number
}

export interface Label extends PlanItem {
    pos : Position
    name : string
}

export interface SeatLine extends PlanItem {
    line : BezierCurve,
    labels : Label[]    // list of label attached to the points of the line
    seats : number[]      // id of the seats associated with the seatline
    seatCal? : SeatLineCalParam
    seatNaming? : SeatLineNaming
    seatCat? : string       // the category for seats controlled by this line
    lineName : string        // the name of the seat line (row id)
}

export interface SeatLineNaming {
    start: number
    end : number
    step? : number
}

// because we calculate seats location on bezier curve
// we use the parameters stored in seat cal
export interface SeatLineCalParam {
    start : number  // start position of the first seat from the first point of the line
    end : number   // distance between the seat
    capacity : number   // number of seats to generate
}

export interface SeatZone extends PlanItem {
    sid : string
    boundary : Boundary
    label : string       // label at the center of the seat zone
    lrot? : number      // label rotation
    lstyle? : string
    lpos? : Position     // position of the label relative to the center of control points
    cat : string
}

/**
 * Because of the preview of a seat zone doesn't contain
 * all information of bezier curve, so we create a plan item
 * for the purpose of previewing only. This preview item is not
 * saved to the backend
 */
export interface SeatZoneConstructionPreview extends PlanItem {
    pos : Position[]
}

// ----- use locally for editting ---
export interface ControlPoint extends PlanItem {
    pos : Position
}
