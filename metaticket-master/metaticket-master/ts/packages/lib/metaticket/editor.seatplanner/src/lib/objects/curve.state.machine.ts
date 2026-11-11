import {MouseEventData, mouseLocation, Position, reliableCmdMeta, reliableSaveCmdMeta} from "@viclass/editor.core"
import {SPDocCtrl} from "../seatplanner.doc.ctrl"
import {BezierCurve, Seat, SeatLine, SeatLineCalParam, SeatLineNaming} from "../seatplanner.model"
import {AbstractCmd, SaveItemsCmd, SeatPlannerCmdType, StartItemPreviewCmd, UpdateItemCmd} from "../seatplanner.cmd"
import {equidistancePointArrayFromTwoEnds, seatPlannerObjectReg} from "../util"
import {Bezier} from "bezier-js"
import {ControlStateData, ControlStateMachine} from "./control.state.machine"
import {PlanItem} from "@metaticket/editor.common"

export type CurveState = "START" | "CREATED_1" | "WAITING_2" | "CREATED_2" | "HAS_MOUSE_DOWN" | "MOVING_CONTROL" | "RENDERED_NO_CONTROL" | "REMOVED"
export type CurveStateData = ControlStateData<CurveState>

export interface CurveStartState extends CurveStateData {}

export interface Created1State extends CurveStartState {
    // storing the position of the first control point
    pos1 : Position
    numCtrlPoint: number
}

export interface HasAllPoints extends CurveStateData {
    seatLine : SeatLine
}

export interface CurveMoveStateData extends HasAllPoints {
    movingPoint : number // index of the moving control point among all the control points
    movingShape : number
    previousPos : Position[]
    lastDelta : Position
    initialMousePos : Position
    initialLinePoints : Position[]
}

export class CurveStateMachine implements ControlStateMachine<CurveState> {

    currentData : CurveStateData

    constructor(
        private docCtrl : SPDocCtrl, id : number,  newItem : boolean
    ) {
        this.currentData = { state : "START", itemId : id, newItem : newItem }
    }

    /**
     * Match mouse event and return necessary command to modify the state
     * @param event
     * @returns
     */
    matchEvent(event : MouseEventData<any>) : AbstractCmd<any> | void | 1 {

        switch (this.currentData.state) {
            case "CREATED_1":
                return this.transitionFromCreated1(event)

            case "WAITING_2":
                return this.transitionFromWaiting(event)

            case "CREATED_2":
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

    initializeFromExistingItem(item : SeatLine) {
        if (this.currentData.state != "START")
            throw new Error("Cannot initialize from other state than 'START'")
        let nState : HasAllPoints = {
            seatLine: item,
            itemId: item.id,
            newItem: false,
            state: "RENDERED_NO_CONTROL"
        }

        this.currentData = nState
    }


    transitionFromMovingControl(event: MouseEventData<any>): AbstractCmd<any> | void | 1 {
        let pos = this.docCtrl.layer!.svgPos(mouseLocation(event))
        let state = this.currentData as CurveMoveStateData

        switch (event.nativeEvent.type) {
            case 'mousemove':
            case 'mouseup':

                let delta = { x: pos.x - state.initialMousePos.x, y : pos.y - state.initialMousePos.y }

                if (event.getKeys.includes('shift')) {

                    let xMove = Math.abs(state.previousPos[0].x - pos.x)
                    let yMove = Math.abs(state.previousPos[0].y - pos.y)


                    if (state.lastDelta.x == 0) xMove = xMove / 1.5    // if last delta is a movement that stick to y, we favor continue in y
                    if (state.lastDelta.y == 0) yMove = yMove / 1.5  // if last delta is a movement taht stick to x, we favor continue in x

                    if (xMove < yMove) {    // detect whether moving in x or y direction
                        delta.x = 0
                    } else if (xMove > yMove) {
                        delta.y = 0
                    } else {    // break tie with last delta
                        if (state.lastDelta.x == 0) delta.x = 0
                        else if (state.lastDelta.y == 0) delta.y = 0
                        else {
                            if (Math.abs(delta.x) < Math.abs(delta.y)) delta.x = 0
                            else delta.y = 0
                        }
                    }
                }

                state.lastDelta = delta
                state.previousPos.push(pos)
                if (state.previousPos.length > 5) state.previousPos.shift()

                let p = state.seatLine.line.p

                if (state.movingPoint >= 0) {
                    p[state.movingPoint].x = state.initialLinePoints[state.movingPoint].x + delta.x
                    p[state.movingPoint].y = state.initialLinePoints[state.movingPoint].y + delta.y
                } else {
                    for (let i = 0; i < p.length; i++) {   // update all control point
                        p[i] = { x : state.initialLinePoints[i].x + delta.x, y : state.initialLinePoints[i].y + delta.y }
                    }
                }



                let cmdMeta = reliableCmdMeta(this.docCtrl.viewport, this.docCtrl.state.id, this.docCtrl.state.id, SeatPlannerCmdType.UPDATE_ITEM)
                let cmd = new UpdateItemCmd(cmdMeta)

                if (event.nativeEvent.type == "mousemove")
                    cmd.setItem(state.seatLine, "MOVING_CONTROL")
                else {
                    cmd.setItem(state.seatLine, "CREATED_2")
                    console.log("Returning command for mouse up / click")
                }

                return cmd

            case 'click':
                return 1;

            default:
                return
        }
    }

    transitionFromHasMouseDown(event : MouseEventData<any>) : AbstractCmd<any> | void | 1 {

        switch (event.nativeEvent.type) {
            case 'mousemove':
                this.currentData.state = "MOVING_CONTROL"

                return 1

            case 'mouseup':
            case 'click':
            case 'mousedown':
                this.currentData.state = "CREATED_2"

                return 1
            default:
                return
        }
    }

    transitionToStartMoving(event: MouseEventData<any>): AbstractCmd<any> | void | 1 {
        let cState = this.currentData as HasAllPoints
        let rMan = this.docCtrl.renderMgr
        let renderer = rMan.rendererOf(cState.itemId)

        let pos = this.docCtrl.layer!.svgPos(mouseLocation(event))

        if (!renderer) return

        switch (event.nativeEvent.type) {
            case 'mousedown':

                if (!event.nativeEvent.target || !(event.nativeEvent.target instanceof SVGElement)) return

                let indexPoint = renderer.controlPoints.indexOf(event.nativeEvent.target)
                let indexShape = -1

                if (indexPoint < 0) {
                    indexShape = renderer.controlShapes.indexOf(event.nativeEvent.target)
                    if (indexShape < 0) return
                }

                let initialPoints = cState.seatLine.line.p.map(v => {
                    let c : Position = {x : v.x, y : v.y}
                    return c
                })

                let newData : CurveMoveStateData = {
                    itemId : cState.itemId,
                    newItem: cState.newItem,
                    state: "HAS_MOUSE_DOWN",
                    seatLine : cState.seatLine,
                    movingPoint: indexPoint,
                    movingShape : indexShape,
                    initialMousePos : pos,
                    previousPos: [pos],
                    lastDelta : {x : 0, y : 0},
                    initialLinePoints: initialPoints
                }
                this.currentData = newData

                return 1    // return 1 here to notify that something matched but not a command returned
            default:
                return
        }
    }

    transitionFromWaiting(event: MouseEventData<any>): UpdateItemCmd | void {

        if (event.nativeEvent.type == "mousemove" || event.nativeEvent.type == "click") {
            let state = this.currentData as HasAllPoints

            let pos = this.docCtrl.layer!.svgPos(mouseLocation(event))
            let oldPointArray = state.seatLine.line.p

            if (event.getKeys.includes('shift')) {
                let pos1 : Position
                let firstPoint = oldPointArray[0]

                if (Math.abs(pos.x - firstPoint.x) < Math.abs(pos.y - firstPoint.y)) {
                    pos1 = { x : firstPoint.x, y: pos.y }
                } else {
                    pos1 = { x : pos.x, y: firstPoint.y }
                }

                pos = pos1
            }


            let line : BezierCurve = {
                type: "BezierCurve",
                p: equidistancePointArrayFromTwoEnds(oldPointArray[0], pos, oldPointArray.length-2)    // minus the two end
            }

            let seatLine : SeatLine = this.defaultSeatLine(state.itemId, line)

            let cmdMeta = reliableCmdMeta(this.docCtrl.viewport, this.docCtrl.state.id, this.docCtrl.state.id, SeatPlannerCmdType.UPDATE_ITEM)
            let cmd = new UpdateItemCmd(cmdMeta)

            if (event.nativeEvent.type == "mousemove")
                cmd.setItem(seatLine, "WAITING_2")
            else if (event.nativeEvent.type == "click") {
                cmd.setItem(seatLine, "CREATED_2")
            }
            return cmd
        }

    }

    defaultSeatLine(id : number, line : BezierCurve) : SeatLine {
        return {
            id : id,
            labels: [],
            seats: [],
            line: line,
            type: "SeatLine",
            lineName: ""
        }
    }

    transitionFromCreated1(event: MouseEventData<any>): UpdateItemCmd {

        let state = this.currentData as Created1State

        let pos = this.docCtrl.layer!.svgPos(mouseLocation(event))

        let line : BezierCurve = {
            type: "BezierCurve",
            p: equidistancePointArrayFromTwoEnds(state.pos1, pos, state.numCtrlPoint)
        }

        let seatLine : SeatLine = this.defaultSeatLine(state.itemId, line)

        let cmdMeta = reliableCmdMeta(this.docCtrl.viewport, this.docCtrl.state.id, this.docCtrl.state.id, SeatPlannerCmdType.UPDATE_ITEM)
        let cmd = new UpdateItemCmd(cmdMeta)
        cmd.setItem(seatLine, "WAITING_2")

        return cmd
    }

    applyUpdateItem(c : UpdateItemCmd) {

        let nextState = c.state.drawState!

        if (nextState == "WAITING_2" || (nextState == "CREATED_2" && this.currentData.state == "WAITING_2") ) { // transferring from created or waiting 2, meaning this is not an existing item
            let data : HasAllPoints = {
                newItem: true,
                state: nextState,
                itemId: c.state.item!.id,
                seatLine: c.state.item as SeatLine
            }
            this.currentData = data
        } else if (nextState == "MOVING_CONTROL" || (nextState == "CREATED_2" && this.currentData.state == "MOVING_CONTROL")) { // then it means we are transitioning from has mouse down or from moving control it self

            let curState = this.currentData as CurveMoveStateData

            curState.state = nextState
            Object.assign(curState.seatLine, c.state.item)  // update the existing seatline
        }

        this.docCtrl.renderMgr.renderSeatLine(this.currentData.itemId)
    }

    applySaved() {
        this.currentData.newItem = false
    }

    applyStartPreview(c: StartItemPreviewCmd) {
        if (this.currentData.state != "START" && this.currentData.state != "RENDERED_NO_CONTROL")
            return  // already showing preview

        if (c.state.existingItem) { // control an existing item
            let data : HasAllPoints = this.currentData as HasAllPoints

            data.state = "CREATED_2"
            Object.assign(data.seatLine, c.state.existingItem as SeatLine)

            this.docCtrl.renderMgr.renderSeatLine(data.seatLine.id)
        } else if (c.state.newItem) {
            let data : Created1State = {
                state: "CREATED_1",
                pos1: c.state.newItem.clickPos,
                itemId : c.state.newItem.itemId,
                numCtrlPoint : c.state.newItem.numCtrlPoint,
                newItem: true
            }
            this.currentData = data

            this.docCtrl.renderMgr.renderSeatLine(data.itemId)
        }
    }

    applyCancelDraw() {
        let objReg = this.docCtrl.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.docCtrl.viewport.id, this.docCtrl.state.id))

        let item = objReg.getEntity(this.currentData.itemId)

        if (!item) {
            this.applyDeleted()
        } else {
            this.currentData.state = "RENDERED_NO_CONTROL";
            (this.currentData as HasAllPoints).newItem = false
            this.docCtrl.renderMgr.renderSeatLine(this.currentData.itemId)
        }
    }

    applyDeleted() {
        this.currentData.state = "REMOVED"   // transition to removed
        this.docCtrl.renderMgr.renderSeatLine(this.currentData.itemId)
        this.docCtrl.renderMgr.removeItem(this.currentData.itemId)
    }

    private findDuplicateSeats(tentativeNaming : Pick<Seat, "id" | "sid" | "displayName" | "cat">[]) : Seat[] {
        let result : Seat[] = []
        for (let n of tentativeNaming) {
            for (let item of this.docCtrl.state.doc.items) {
                if (item.type == "Seat" && item.id != n.id && (item as Seat).sid == n.sid) {
                    result.push(item as Seat)
                    continue
                }
            }
        }

        return result
    }

    updateNaming(lineName : string, naming : SeatLineNaming, seatCat : string) {
        let notAllowedState : CurveState[] = ["START", "CREATED_1", "WAITING_2", "REMOVED"]
        if (notAllowedState.includes(this.currentData.state)) return

        let data : HasAllPoints = this.currentData as HasAllPoints

        let objReg = this.docCtrl.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.docCtrl.viewport.id, this.docCtrl.state.id))

        // find the seats currently controlled by the seat lines
        let line = data.seatLine
        let seatsUpdated : Seat[] = []

        let step = naming.step || 1

        // name validation
        let tentativeNaming = []
        for (let i = 0; i < line.seats.length; i++) {
            let num = (naming.start >= naming.end) ? naming.start-i*step : naming.start+i*step

            let s = objReg.getEntity(line.seats[i]) as Seat
            if (!s) throw new Error(`Unable to find seat with id ${line.seats[i]}`)

            if (naming.start >= naming.end && num < naming.end || naming.start < naming.end && num > naming.end) {
                tentativeNaming.push({ id: s.id, sid : "", displayName : "", cat : "" })
            } else {
                tentativeNaming.push({ id: s.id, sid : lineName+num, displayName : num+"", cat : seatCat })
            }
        }

        let duplicates = this.findDuplicateSeats(tentativeNaming)
        if (duplicates.length > 0) {
            throw {
                duplicates : duplicates,
            }
        }

        // if OK, assign names!
        line.lineName = lineName
        line.seatCat = seatCat

        if (!line.seatNaming) line.seatNaming = { start : 0, end : 0 }
        Object.assign(line.seatNaming,naming)

        for (let i = 0; i < line.seats.length; i++) {
            let num = (naming.start >= naming.end) ? naming.start-i*step : naming.start+i*step

            let s = objReg.getEntity(line.seats[i]) as Seat
            if (!s) throw new Error(`Unable to find seat with id ${line.seats[i]}`)

            if (naming.start >= naming.end && num < naming.end || naming.start < naming.end && num > naming.end) {
                s.sid = ""
                s.displayName = ""
                s.cat = ""
            } else {
                s.sid = lineName+num
                s.displayName = num+""
                s.cat = seatCat
            }

            seatsUpdated.push(s)
        }

        let meta = reliableSaveCmdMeta(this.docCtrl.viewport, this.docCtrl.state, this.docCtrl.state.id, this.docCtrl.state.id, SeatPlannerCmdType.SAVE_ITEMS)
        let cmd = new SaveItemsCmd(meta)
        let updated : PlanItem[] = seatsUpdated
        let added : PlanItem[] = []

        if (data.newItem) added.push(line)
        else updated.push(line)

        cmd.setItems(added, updated, [])
        this.docCtrl.editor.cmdChannel.receive(cmd)
    }

    generateSeats(cal : SeatLineCalParam) {
        let notAllowedState : CurveState[] = ["START", "CREATED_1", "WAITING_2", "REMOVED"]
        if (notAllowedState.includes(this.currentData.state)) return

        let data : HasAllPoints = this.currentData as HasAllPoints

        // find the seats currently controlled by the seat lines
        let line = data.seatLine

        let seatsRemoved : Seat[] = []
        let seatsUpdated : Seat[] = []
        let seatsAdded : Seat[] = []
        let curSeats : Seat[] = []

        let objReg = this.docCtrl.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.docCtrl.viewport.id, this.docCtrl.state.id))

        for (let i = 0; i < line.seats.length; i++) {
            let s = objReg.getEntity(line.seats[i]) as Seat
            if (!s) throw new Error(`Unable to find seat with id ${line.seats[i]}`)
            curSeats.push(s)
        }

        if (curSeats.length > cal.capacity) {
            for (let i = cal.capacity; i < curSeats.length; i++) {
                seatsRemoved.push(curSeats[i])
            }
            line.seats.splice(cal.capacity)
        } else if (cal.capacity > curSeats.length) {
            for (let i = curSeats.length; i < cal.capacity; i++) {
                let newId = objReg.getAndIncrementId()
                let s : Seat = {
                    id: newId,
                    cat: "",
                    pos: { x : 0, y : 0 },
                    rotation: 0,
                    sid: "",
                    displayName : "",
                    type: "Seat",
                    controlItem: line.id
                }

                seatsAdded.push(s)
                line.seats.push(s.id)
            }
        }

        // calculate positions of all the seats
        let bz = new Bezier(line.line.p)

        let unit = (cal.capacity == 1)?((1 - cal.end) - cal.start) : ((1 - cal.end) - cal.start)/(cal.capacity - 1)

        for (let i = 0; i < cal.capacity; i++) {
            let frac = cal.start + i*unit
            if (frac > 1) frac = 1
            let newP = bz.get(frac)
            newP.x = Math.round((newP.x + Number.EPSILON) * 100) / 100
            newP.y = Math.round((newP.y + Number.EPSILON) * 100) / 100
            if (i < curSeats.length) {
                seatsUpdated.push(curSeats[i])
                curSeats[i].pos = { x : newP.x, y : newP.y }
            } else if (i >= curSeats.length) {
                seatsAdded[i - curSeats.length].pos = { x : newP.x, y : newP.y }
            }
        }

        if (!line.seatCal) line.seatCal = { capacity : 0, start: 0, end: 0 }

        Object.assign(line.seatCal,cal) // avoid using the parameters input directly e.g. line.seatCal = cal because this 'cal' can come from the UI

        // generate necessary command

        if (seatsUpdated.length == 0 && seatsAdded.length == 0 && seatsRemoved.length == 0) return

        let meta = reliableSaveCmdMeta(this.docCtrl.viewport, this.docCtrl.state, this.docCtrl.state.id, this.docCtrl.state.id, SeatPlannerCmdType.SAVE_ITEMS)

        let cmd = new SaveItemsCmd(meta)
        let updated : PlanItem[] = seatsUpdated
        let added : PlanItem[] = seatsAdded

        // at the same time add / save the line
        if (data.newItem) added.push(line)
        else updated.push(line)

        cmd.setItems(added, updated, seatsRemoved)

        this.docCtrl.editor.cmdChannel.receive(cmd)
    }

    isNewItem(): boolean {
        return this.currentData.newItem
    }

    isSaveable(): boolean {
        return this.currentData.state == "CREATED_2" && this.item != null
    }

    get item() : PlanItem | undefined {
        return (this.currentData as HasAllPoints).seatLine
    }
}

