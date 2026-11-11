import {MouseEventData, mouseLocation, reliableCmdMeta} from "@viclass/editor.core"
import {SPDocCtrl} from "../seatplanner.doc.ctrl"
import {Seat} from "../seatplanner.model"
import {AbstractCmd, SeatPlannerCmdType, StartItemPreviewCmd, UpdateItemCmd} from "../seatplanner.cmd"
import {seatPlannerObjectReg} from "../util"
import {ControlStateData, ControlStateMachine} from "./control.state.machine"
import {SeatToolState} from "../tools/seat.tool"
import {PlanItem} from "@metaticket/editor.common"

export type SeatState = "START" | "SELECTED" | "HAS_MOUSE_DOWN" | "MOVING_CONTROL" | "RENDERED_NO_CONTROL" | "REMOVED" |
                        "DISABLED"  // when disabled, there is no interaction can be performed

export type SeatStateData = ControlStateData<SeatState>

export interface HasAllData extends SeatStateData {
    seat : Seat
    isHighlighted : boolean, // true when in "SELECTED / HAS_MOUSE_DOWN / MOVING_CONTROL"
}

export class SeatStateViewOnlyMachine implements ControlStateMachine<SeatState> {
    currentData : SeatStateData

    constructor(
        private docCtrl : SPDocCtrl, id : number, newItem : boolean
    ) {
        this.currentData = { state : "START", itemId : id, newItem : newItem}
    }

    initializeFromExistingItem(item: Seat): void {
        if (this.currentData.state != "START")
            throw new Error("Cannot initialize from other state than 'START'")
        let nState : HasAllData = {
            seat: item,
            itemId: item.id,
            newItem: false,
            state: "RENDERED_NO_CONTROL",
            isHighlighted : false
        }

        this.currentData = nState
    }

    matchEvent(event: MouseEventData<any>): void | AbstractCmd<any> | 1 {
        if (this.isDisabled()) return 1
        switch (this.currentData.state) {
            case "SELECTED":
                return

            case "HAS_MOUSE_DOWN":
            case "MOVING_CONTROL":
                throw new Error(`Seat has illegal state ${this.currentData.state} in view only mode`)
        }
    }

    applyStartPreview(c: StartItemPreviewCmd): void {
        if (this.currentData.state != "START" && this.currentData.state != "RENDERED_NO_CONTROL")
            return  // already showing preview

        if (c.state.existingItem) { // control an existing item
            let data : HasAllData = this.currentData as HasAllData

            data.state = "SELECTED"
            data.isHighlighted = true
            Object.assign(data.seat, c.state.existingItem as Seat)

            this.docCtrl.renderMgr.renderSeat(data.seat.id)
        }
    }

    applyUpdateItem(cmd: UpdateItemCmd): void {
        throw new Error("Update command is not allowed in view only mode")
    }

    isSaveable(): boolean {
        return false
    }

    applySaved(): void {
        throw new Error("Save command is not allowed in view only mode")
    }

    applyDisable() : void {
        let objReg = this.docCtrl.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.docCtrl.viewport.id, this.docCtrl.state.id))

        let item = objReg.getEntity(this.currentData.itemId)

        if (!item) {
            throw new Error(`Item ${this.currentData.itemId} couldn't be found`)
        } else {
            let data = this.currentData as HasAllData
            data.state = "DISABLED";
            data.newItem = false
            data.isHighlighted = false

            this.docCtrl.renderMgr.renderSeat(this.currentData.itemId)

        }
    }

    applyEnable() : void {
        this.applyCancelDraw()
    }

    applyDeleted(): void {
        let objReg = this.docCtrl.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.docCtrl.viewport.id, this.docCtrl.state.id))

        let item = objReg.getEntity(this.currentData.itemId)

        if (!item) {
            this.applyDeleted()
        } else {
            let data = this.currentData as HasAllData
            data.state = "RENDERED_NO_CONTROL";
            data.newItem = false
            data.isHighlighted = false

            this.docCtrl.renderMgr.renderSeat(this.currentData.itemId)

        }
    }

    applyCancelDraw(): void {
        let objReg = this.docCtrl.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.docCtrl.viewport.id, this.docCtrl.state.id))

        let item = objReg.getEntity(this.currentData.itemId)

        if (!item) {
            throw new Error(`Item ${this.currentData.itemId} couldn't be found`)
        } else {
            let data = this.currentData as HasAllData
            data.state = "RENDERED_NO_CONTROL";
            data.newItem = false
            data.isHighlighted = false

            this.docCtrl.renderMgr.renderSeat(this.currentData.itemId)

        }
    }

    isRemoved(): boolean {
        return false
    }

    isNewItem(): boolean {
        return false
    }

    isDisabled() : boolean {
        return this.currentData.state == "DISABLED"
    }

    get item() : PlanItem | undefined {
        return (this.currentData as HasAllData).seat
    }

}

export class SeatStateMachine implements ControlStateMachine<SeatState> {

    currentData : SeatStateData

    constructor(
        private docCtrl : SPDocCtrl, id : number, newItem : boolean
    ) {
        this.currentData = { state : "START", itemId : id, newItem : newItem}
    }

    /**
     * Match mouse event and return necessary command to modify the state
     * @param event
     * @returns
     */
    matchEvent(event : MouseEventData<any>) : AbstractCmd<any> | void | 1 {

        switch (this.currentData.state) {
            case "SELECTED":
                return this.transitionToStartMoving(event)

            case "HAS_MOUSE_DOWN":
                return this.transitionFromHasMouseDown(event)

            case "MOVING_CONTROL":
                return this.transitionFromMovingControl(event)
        }

    }

    isRemoved(): boolean {
        return this.currentData.state == "REMOVED"
    }

    initializeFromExistingItem(item : Seat) {
        if (this.currentData.state != "START")
            throw new Error("Cannot initialize from other state than 'START'")
        let nState : HasAllData = {
            seat: item,
            itemId: item.id,
            newItem: false,
            state: "RENDERED_NO_CONTROL",
            isHighlighted : false
        }

        this.currentData = nState
    }


    transitionFromMovingControl(event: MouseEventData<any>): AbstractCmd<any> | void | 1 {
        let pos = this.docCtrl.layer!.svgPos(mouseLocation(event))
        let state = this.currentData as HasAllData

        switch (event.nativeEvent.type) {
            case 'mousemove':
            case 'mouseup':

                state.seat.pos = pos

                let cmdMeta = reliableCmdMeta(this.docCtrl.viewport, this.docCtrl.state.id, this.docCtrl.state.id, SeatPlannerCmdType.UPDATE_ITEM)
                let cmd = new UpdateItemCmd(cmdMeta)

                if (event.nativeEvent.type == "mousemove")
                    cmd.setItem(state.seat, "MOVING_CONTROL")
                else {
                    cmd.setItem(state.seat, "SELECTED")
                }

                return cmd

            case 'click':
                return 1

            default:
                return
        }
    }

    transitionFromHasMouseDown(event : MouseEventData<any>) : AbstractCmd<any> | void | 1 {

        switch (event.nativeEvent.type) {
            case 'mousemove':
                this.currentData.state = "MOVING_CONTROL"
                console.log("MOVING CON TROL")
                return 1

            case 'mouseup':
            case 'click':
            case 'mousedown':
                this.currentData.state = "SELECTED"

                return 1
            default:
                return
        }
    }

    transitionToStartMoving(event: MouseEventData<any>): AbstractCmd<any> | void | 1 {
        let cState = this.currentData as HasAllData
        let rMan = this.docCtrl.renderMgr
        let renderer = rMan.rendererOf(cState.itemId)

        if (!renderer) return

        switch (event.nativeEvent.type) {
            case 'mousedown':

                if (!event.nativeEvent.target || !(event.nativeEvent.target instanceof SVGElement)) return

                let g = event.nativeEvent.target.parentNode

                if (!(g instanceof SVGGElement)) return

                let indexShape = renderer.controlShapes.indexOf(g)
                if (indexShape < 0) return

                let newData : HasAllData = {
                    itemId : cState.itemId,
                    newItem: cState.newItem,
                    state: "HAS_MOUSE_DOWN",
                    seat : cState.seat,
                    isHighlighted: true,
                }

                console.log("HAS MOUSE DOWN")

                this.currentData = newData

                return 1    // return 1 here to notify that something matched but not a command returned
            default:
                return
        }
    }

    /**
     * Disable a seat can only be called programmatically
     * and not as a result of user's interaction. This also is a local operation only
     * */
    disableSeat() {
        this.currentData.state = "DISABLED"
        this.docCtrl.renderMgr.renderSeat(this.currentData.itemId)
    }

    applyUpdateItem(c : UpdateItemCmd) {

        let nextState = c.state.drawState!

        if (nextState == "MOVING_CONTROL" || (nextState == "SELECTED" && this.currentData.state == "MOVING_CONTROL")) { // then it means we are transitioning from has mouse down or from moving control it self

            let curState = this.currentData as HasAllData

            curState.state = nextState
            Object.assign(curState.seat, c.state.item)  // update the existing seat
        }

        this.docCtrl.renderMgr.renderSeat(this.currentData.itemId)
    }

    updateSeatFromToolState(cs : SeatToolState) {
        let cmdMeta = reliableCmdMeta(this.docCtrl.viewport, this.docCtrl.state.id, this.docCtrl.state.id, SeatPlannerCmdType.UPDATE_ITEM)
        let cmd = new UpdateItemCmd(cmdMeta)

        // keep remaining state, update the internal item
        let s :Seat = this.item! as Seat
        s.displayName = cs.displayName
        s.sid = cs.seatId
        cmd.setItem(this.item!, this.currentData.state)

        this.docCtrl.editor.cmdChannel.receive(cmd)

    }

    applyStartPreview(c: StartItemPreviewCmd) {
        if (this.currentData.state != "START" && this.currentData.state != "RENDERED_NO_CONTROL")
            return  // already showing preview

        if (c.state.existingItem) { // control an existing item
            let data : HasAllData = this.currentData as HasAllData

            data.state = "SELECTED"
            data.isHighlighted = true
            Object.assign(data.seat, c.state.existingItem as Seat)

            this.docCtrl.renderMgr.renderSeat(data.seat.id)
        } else if (c.state.newItem) {
            let data : HasAllData = {
                state: "SELECTED",
                itemId : c.state.newItem.itemId,
                newItem: true,
                seat: {
                    cat: "",
                    id: c.state.newItem.itemId,
                    pos: c.state.newItem.clickPos,
                    sid: "",
                    displayName: "",
                    rotation: 0,
                    type: "Seat"
                },
                isHighlighted: true
            }
            this.currentData = data

            this.docCtrl.renderMgr.renderSeat(data.itemId)
        }
    }

    applyCancelDraw() {
        let objReg = this.docCtrl.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.docCtrl.viewport.id, this.docCtrl.state.id))

        let item = objReg.getEntity(this.currentData.itemId)

        if (!item) {
            this.applyDeleted()
        } else {
            let data = this.currentData as HasAllData
            data.state = "RENDERED_NO_CONTROL";
            data.newItem = false
            data.isHighlighted = false

            this.docCtrl.renderMgr.renderSeat(this.currentData.itemId)

        }

    }

    applyDeleted() {
        this.currentData.state = "REMOVED"   // transition to removed
        this.docCtrl.renderMgr.renderSeat(this.currentData.itemId)
        this.docCtrl.renderMgr.removeItem(this.currentData.itemId)
    }

    applySaved() {
        this.currentData.newItem = false
    }

    isNewItem(): boolean {
        return this.currentData.newItem
    }

    isSaveable(): boolean {
        return this.currentData.state == "SELECTED" && this.item != null // only in selected state it might have some change
    }

    isDisabled() : boolean {
        return this.currentData.state == "DISABLED"
    }

    get item() : PlanItem | undefined {
        return (this.currentData as HasAllData).seat
    }
}

