import { MouseEventData } from "@viclass/editor.core";
import { AbstractCmd, StartItemPreviewCmd, UpdateItemCmd } from "../seatplanner.cmd";
import { PlanItem } from "@metaticket/editor.common";

export interface ControlStateMachine<StateType> {

    currentData : ControlStateData<StateType>
    readonly item? : PlanItem    // the item that this machine is controlling, it might not be available at some state of the machine

    initializeFromExistingItem(item : PlanItem) : void

    /**
     * Match mouse event and return necessary command to modify the state
     * @param event
     * @returns 
     * - a command if the state needs to be modified
     * - 1 if it is recording the event, it means the event is significant and it
     * want to notify the tool about this
     * - void if the event doesn't have any effects
     */
    matchEvent(event : MouseEventData<any>) : AbstractCmd<any> | void | 1

    applyStartPreview(cmd : StartItemPreviewCmd) : void
    applyUpdateItem(cmd : UpdateItemCmd) : void

    isSaveable() : boolean
    applySaved() : void
    applyDeleted() : void

    applyCancelDraw() : void

    isRemoved() : boolean
    isNewItem() : boolean

}

export interface ControlStateData<StateType> {
    state : StateType
    itemId : number
    newItem : boolean
}
