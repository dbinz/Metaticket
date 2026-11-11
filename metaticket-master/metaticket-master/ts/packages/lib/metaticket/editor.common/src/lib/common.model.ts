import {EditorType, VEventData, VEventListener} from "@viclass/editor.core";
import {PlanDocCtrl} from "./common.doc.ctrl";

export declare type PlanEditorType = "SeatPlanner" | "PolicyDesc" | EditorType

export type PlanItemType = "Seat" | "Label" | "SeatLine" | 
                            "SeatZone" | "SeatZoneConstructionPreview" | "ControlPoint" | "ItemPlaceHolder"

export interface PlanItem {
    id : number
    type : PlanItemType
}

export interface PlanViewEventState {
    items : PlanItem[]
    controlEls? : HTMLOrSVGElement[]
    triggerItem? : PlanItem
    triggerEl? : HTMLOrSVGElement
}

export interface PlanDocument {
    id: string
    styleSheet: string
    items : PlanItem[]
    version : number
}

export interface ElementsSelectionState {
    items : PlanItem[]
}

export type ElementSelectionEvent = VEventData<"element-selection-change", PlanDocCtrl, ElementsSelectionState>

export type PlanViewEventType = "item-lowlight" | "item-highlight" | "item-selected" | "item-deselected" | "item-clicked"
export type PlanViewEvent = VEventData<PlanViewEventType, PlanDocCtrl, PlanViewEventState>
export type PlanListener = VEventListener<PlanViewEvent>
