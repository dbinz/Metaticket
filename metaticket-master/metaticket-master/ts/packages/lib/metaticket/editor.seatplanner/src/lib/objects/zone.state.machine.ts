import { MouseEventData, Position, mouseLocation, reliableCmdMeta } from "@viclass/editor.core"
import { ControlStateData, ControlStateMachine } from "./control.state.machine"
import { SPDocCtrl } from "../seatplanner.doc.ctrl"
import { PlanItem } from "@metaticket/editor.common"
import { AbstractCmd, SeatPlannerCmdType, StartItemPreviewCmd, UpdateItemCmd } from "../seatplanner.cmd"
import { BezierCurve, SeatZoneConstructionPreview, SeatZone } from "../seatplanner.model"
import { equidistancePointArrayFromTwoEnds, seatPlannerObjectReg } from "../util"
import { bpBegin, bpIdx, bpLastIdx, bpcIdx } from "./renderer"
import { SeatZoneToolState } from "../tools/seatzone.tool"

export type ZoneState = "START" | "CONSTRUCTING" | "SELECTED" | "HAS_MOUSE_DOWN" | "MOVING_CONTROL" | "RENDERED_NO_CONTROL" | "REMOVED" |
                        "DISABLED"  // when disabled, there is no interaction can be performed

export type ZoneStateData = ControlStateData<ZoneState>

export interface ZonetartState extends ZoneStateData {}

export interface ConstructingStateData extends ZoneStateData {
    // storing the position of the control points
    pos : Position[]
    numCtrlPoint: number
}

export interface ZoneStateWithItem extends ZoneStateData {
    zone : SeatZone
    isHighlighted : boolean
}

export interface ZoneMoveStateData extends ZoneStateWithItem {
    movingPoint : number // index of the moving control point among all the control points
    movingShape : number
    previousPos : Position[]
    lastDelta : Position
    initialMousePos : Position
    initialZone : SeatZone
}

export class ZoneStateViewOnlyMachine implements ControlStateMachine<ZoneState> {
    currentData: ZoneStateData

    constructor(
        private docCtrl : SPDocCtrl, id : number,  newItem : boolean
    ) {
        this.currentData = { state : "START", itemId : id, newItem : newItem }
    }

    initializeFromExistingItem(item : SeatZone) {
        if (this.currentData.state != "START")
            throw new Error("Cannot initialize from other state than 'START'")

        let nState : ZoneStateWithItem = {
            zone: item,
            itemId: item.id,
            newItem: false,
            isHighlighted: false,
            state: "RENDERED_NO_CONTROL"
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
            let data : ZoneStateWithItem = this.currentData as ZoneStateWithItem

            data.state = "SELECTED"
            data.isHighlighted = true

            this.docCtrl.renderMgr.renderSeatZone(data.zone.id)
        }
    }

    applyDisable() : void {
        let objReg = this.docCtrl.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.docCtrl.viewport.id, this.docCtrl.state.id))

        let item = objReg.getEntity(this.currentData.itemId)

        if (!item) {
            throw new Error(`Item ${this.currentData.itemId} couldn't be found`)
        } else {
            let data = this.currentData as ZoneStateWithItem
            data.state = "DISABLED";
            data.newItem = false
            data.isHighlighted = false

            this.docCtrl.renderMgr.renderSeatZone(this.currentData.itemId)

        }
    }

    isDisabled() : boolean {
        return this.currentData.state == "DISABLED"
    }

    applyEnable() : void {
        this.applyCancelDraw()
    }

    applyUpdateItem(cmd: UpdateItemCmd): void {
        throw new Error("Method not implemented.")
    }
    isSaveable(): boolean {
        return false
    }
    applySaved(): void {
        throw new Error("Method not implemented.")
    }
    applyDeleted(): void {
        throw new Error("Method not implemented.")
    }

    applyCancelDraw(): void {
        let objReg = this.docCtrl.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.docCtrl.viewport.id, this.docCtrl.state.id))

        let item = objReg.getEntity(this.currentData.itemId)

        if (!item) {
            throw new Error(`Item ${this.currentData.itemId} couldn't be found`)
        } else {
            let data = this.currentData as ZoneStateWithItem
            data.state = "RENDERED_NO_CONTROL";
            data.newItem = false
            data.isHighlighted = false

            this.docCtrl.renderMgr.renderSeatZone(this.currentData.itemId)

        }
    }
    isRemoved(): boolean {
        return false
    }
    isNewItem(): boolean {
        return false
    }

    get item() : PlanItem | undefined {
        return (this.currentData as ZoneStateWithItem).zone
    }
    
}


export class ZoneStateMachine implements ControlStateMachine<ZoneState> {

    currentData : ZoneStateData

    constructor(
        private docCtrl : SPDocCtrl, id : number,  newItem : boolean
    ) {
        this.currentData = { state : "START", itemId : id, newItem : newItem }
    }

    get item() : PlanItem | undefined {
        return (this.currentData as ZoneStateWithItem).zone
    }

    initializeFromExistingItem(item : SeatZone) {
        if (this.currentData.state != "START")
            throw new Error("Cannot initialize from other state than 'START'")
        let nState : ZoneStateWithItem = {
            zone: item,
            itemId: item.id,
            newItem: false,
            isHighlighted: false,
            state: "RENDERED_NO_CONTROL"
        }

        this.currentData = nState
    }

    matchEvent(event: MouseEventData<any>): void | AbstractCmd<any> | 1 {
        switch (this.currentData.state) {
            case "CONSTRUCTING":
                return this.decideConstructingOrFinish(event)
            case "HAS_MOUSE_DOWN":
                return this.transitFromHasMouseDown(event)
            case "MOVING_CONTROL":
                return this.transitFromMovingControl(event)
            case "SELECTED":
                return this.decideIfControlIsPressed(event)
        }
    }

    decideConstructingOrFinish(event: MouseEventData<any>): void | AbstractCmd<any> | 1 {
        if (event.nativeEvent.type == "mousemove" || event.nativeEvent.type == "click") {
            // generate a new primitive line
            let s = this.currentData as ConstructingStateData
            let b = s.pos
            let lastPoint = b[b.length-1]
            let pos = this.docCtrl.layer!.svgPos(mouseLocation(event))
            
            if (event.getKeys.includes('shift')) {
                let pos1 : Position
                let firstPoint = b[b.length - 2]

                if (Math.abs(pos.x - firstPoint.x) < Math.abs(pos.y - firstPoint.y)) {
                    pos1 = { x : firstPoint.x, y: pos.y }
                } else {
                    pos1 = { x : pos.x, y: firstPoint.y }
                }

                pos = pos1
            }

            b[b.length - 1] = pos

            let cmdMeta = reliableCmdMeta(this.docCtrl.viewport, this.docCtrl.state.id, this.docCtrl.state.id, SeatPlannerCmdType.UPDATE_ITEM)
            let cmd = new UpdateItemCmd(cmdMeta)

            let preview : SeatZoneConstructionPreview = {
                id : s.itemId,
                pos : b,
                type : "SeatZoneConstructionPreview"
            }

            if (event.nativeEvent.type == "mousemove") {
                cmd.setItem(preview, "CONSTRUCTING")
            } else if (event.nativeEvent.type == "click") {

                let rMan = this.docCtrl.renderMgr
                let renderer = rMan.rendererOf(s.itemId)

                let pos = this.docCtrl.layer!.svgPos(mouseLocation(event))

                if (!renderer) throw new Error("No renderer available for seat zone "+s.itemId)

                if (event.nativeEvent.target == renderer.controlPoints[0] ) {
                    // clicked on the begining point

                    
                    if (b.length > 3) { // this means the user want to close the loop, so we remove the last point
                        b.splice(b.length - 1)
                        let item = this.generateItemFromPreview(b, s.numCtrlPoint)
                        cmd.setItem(item, "SELECTED")
                    }
                    // this means the user click on the begining point, but the array doesn't have enough point yet, so do nothing, 
                    // just tell the tool that this action already capture and the event doesn't fall through to other tool
                    else return 1   
                } else {
                    // clicked somewhere else
                    b.push(pos)
                    cmd.setItem(preview, "CONSTRUCTING")
                }
            }
            return cmd
        }
    }

    generateItemFromPreview(pos : Position[], numCtrlPoint : number) : SeatZone {
        let curves : BezierCurve[] = []
        for (let i = 0; i < pos.length-1; i++) {
            let p1 = pos[i]
            let p2 = pos[i+1]
            let line : BezierCurve = {
                type: "BezierCurve",
                p: equidistancePointArrayFromTwoEnds(p1, p2, numCtrlPoint)    // minus the two end
            }
            curves.push(line)
        }

        curves.push({
            type: "BezierCurve",
            p: equidistancePointArrayFromTwoEnds(pos[pos.length-1], pos[0], numCtrlPoint)    // minus the two end
        })

        return {
            id : this.currentData.itemId,
            boundary : {
                e: curves,
                type: "Boundary"
            },
            cat: "",
            label: "",
            sid: "",
            type: "SeatZone"
        }
    }

    applyStartPreview(c: StartItemPreviewCmd): void {
        if (this.currentData.state != "START" && this.currentData.state != "RENDERED_NO_CONTROL")
            return  // already showing preview

        if (c.state.existingItem) { // control an existing item
            let data : ZoneStateWithItem = this.currentData as ZoneStateWithItem

            data.state = "SELECTED"
            //Object.assign(data.zone, c.state.existingItem as SeatZone)

            this.docCtrl.renderMgr.renderSeatZone(data.zone.id)
        } else if (c.state.newItem) {
            let p1 = c.state.newItem.clickPos
            let p2 : Position = Object.assign({}, p1)
            let data : ConstructingStateData = {
                state: "CONSTRUCTING",
                pos: [p1, p2],
                itemId : c.state.newItem.itemId,
                numCtrlPoint : c.state.newItem.numCtrlPoint,
                newItem: true
            }
            this.currentData = data

            this.docCtrl.renderMgr.renderSeatZone(data.itemId)
        }
    }

    applyUpdateItem(cmd: UpdateItemCmd): void {
        if (this.currentData.state == "CONSTRUCTING") {
            let s = this.currentData as ConstructingStateData
            s.pos = (cmd.state.item as SeatZoneConstructionPreview)?.pos

            if (cmd.state.drawState == "CONSTRUCTING") {
                this.docCtrl.renderMgr.renderSeatZone(s.itemId)
            } else if (cmd.state.drawState == "SELECTED") {
                let newData : ZoneStateWithItem = {
                    isHighlighted: false,
                    newItem : this.currentData.newItem,
                    itemId: this.currentData.itemId,
                    state : "SELECTED",
                    zone : Object.assign({}, cmd.state.item as SeatZone)
                }
                this.currentData = newData
                this.docCtrl.renderMgr.renderSeatZone(s.itemId)
            }
        } else if (cmd.state.drawState == "MOVING_CONTROL" || cmd.state.drawState == "SELECTED") {
            if (this.currentData.state != "MOVING_CONTROL" && this.currentData.state != "HAS_MOUSE_DOWN" && this.currentData.state != "SELECTED")
                throw new Error("Command seems to have wrong state of "+cmd.state.drawState+" while current data is having state "+this.currentData.state)

            let data = this.currentData as ZoneStateWithItem

            if (!cmd.state.item) {
                throw new Error("Command doesn't contain the necessary item for updating")
            }

            data.zone = cmd.state.item as SeatZone
            data.state = cmd.state.drawState

            this.docCtrl.renderMgr.renderSeatZone(data.itemId)
        }
    }

    decideIfControlIsPressed(event : MouseEventData<any>) : void | AbstractCmd<any> | 1 {
        let cState = this.currentData as ZoneStateWithItem
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

                let initialZone : SeatZone = {
                    id : cState.zone.id,
                    cat : cState.zone.cat,
                    label : cState.zone.label,
                    type: "SeatZone",
                    boundary : {
                        type: "Boundary",
                        e: []
                    },
                    sid: cState.zone.sid,
                    lpos : cState.zone.lpos,
                    lrot : cState.zone.lrot,
                    lstyle : cState.zone.lstyle
                }

                let curves = cState.zone.boundary.e as BezierCurve[]
                for (let i = 0; i < curves.length; i++) {
                    let points : Position[] = []
                    let ps = curves[i].p
                    for (let j = 0; j < ps.length; j++ ) {
                        points.push({x : ps[j].x, y : ps[j].y })
                    }
                    let c : BezierCurve = {
                        type : "BezierCurve",
                        p : points
                    }
                    initialZone.boundary.e.push(c)
                }


                let newData : ZoneMoveStateData = {
                    itemId : cState.itemId,
                    newItem: cState.newItem,
                    state: "HAS_MOUSE_DOWN",
                    zone : cState.zone,
                    movingPoint: indexPoint,
                    movingShape : indexShape,
                    initialMousePos : pos,
                    previousPos: [pos],
                    lastDelta : {x : 0, y : 0},
                    initialZone: initialZone,
                    isHighlighted : false
                }
                this.currentData = newData

                return 1    // return 1 here to notify that something matched but not a command returned
            default:
                return
        }
    }

    transitFromHasMouseDown(event : MouseEventData<any>) : void | AbstractCmd<any> | 1 {
        switch (event.nativeEvent.type) {
            case 'mousemove':
                this.currentData.state = "MOVING_CONTROL"

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

    transitFromMovingControl(event : MouseEventData<any>) : void | AbstractCmd<any> | 1 {

        let pos = this.docCtrl.layer!.svgPos(mouseLocation(event))
        let state = this.currentData as ZoneMoveStateData
        let newState : ZoneMoveStateData = Object.assign({}, state)

        let rMan = this.docCtrl.renderMgr
        let renderer = rMan.rendererOf(state.itemId)

        switch (event.nativeEvent.type) {
            case 'mousemove':
            case 'mouseup':

                let delta = { x: pos.x - state.initialMousePos.x, y : pos.y - state.initialMousePos.y } // how much has been moved from the initial mouse position

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

                newState.lastDelta = delta
                newState.previousPos.push(pos)
                if (newState.previousPos.length > 5) newState.previousPos.shift()

                let newZone : SeatZone = {
                    id : state.itemId,
                    cat : state.zone.cat,
                    label: state.zone.label,
                    boundary: {
                        e: [],
                        type: "Boundary"
                    },
                    type: "SeatZone",
                    sid : state.zone.sid,
                    lpos : state.initialZone.lpos? { x : state.initialZone.lpos.x, y : state.initialZone.lpos.y } : undefined,
                    lrot : state.initialZone.lrot,
                    lstyle : state.initialZone.lstyle
                }
                newState.zone = newZone

                if (state.movingShape == 1) {   // move label
                    for (let i = 0; i < state.zone.boundary.e.length; i++) {
                        newZone.boundary.e.push(state.zone.boundary.e[i])
                    }
                    
                    if (!newZone.lpos) newZone.lpos = { x : 0, y : 0 }

                    newZone.lpos.x += delta.x
                    newZone.lpos.y += delta.y

                } else {

                    let affectedCurves : number[] = []
                    let affectedPointIdx = -1
                    if (state.movingShape >= 0) {
                        affectedCurves.push(-1)
                    } else if (state.movingPoint >= 0) {
                        // extract the information stored in the element of the control point to determine
                        // the bezier curve being affected by the movement
                        let cpE = renderer?.controlPoints[state.movingPoint] as SVGElement

                        if (!cpE) throw new Error("Unable to find the control point element of the moved point index.")

                        // pushing order matters, 
                        // first, push the curve that the moving point belongs
                        if (cpE.dataset[bpIdx]) affectedCurves.push(parseInt(cpE.dataset[bpIdx] as string))

                        // then if this is the begining point, it is also affecting the last curve of the boundary
                        if (cpE.dataset[bpBegin]) affectedCurves.push(state.zone.boundary.e.length-1)
                        // else if it is the last point of a curve, then it is also affecting next curve after the curve it belongs to 
                        else if (cpE.dataset[bpLastIdx]) affectedCurves.push(parseInt(cpE.dataset[bpIdx] as string)+1)

                        if (cpE.dataset[bpcIdx]) affectedPointIdx = parseInt(cpE.dataset[bpcIdx] as string)

                        if (affectedCurves.length > 2 || affectedCurves.length == 0) {
                            throw new Error("The number of affected curve is "+affectedCurves.length+". There must be some inconsistency.")
                        }

                        if (affectedPointIdx == -1)
                            throw new Error("The moved point within curve is not defined!!")

                    } else return

                    // copy the bezier curves
                    for (let i = 0; i < state.zone.boundary.e.length; i++) {
                        if (
                            i == affectedCurves[0] || i == affectedCurves[1] || // moving some points on a particular curve
                            affectedCurves[0] == -1 // moving all curves at same time
                        ) {
                            let newPoints : Position[] = []
                            let oldCurve = state.initialZone.boundary.e[i] as BezierCurve

                            for (let j = 0; j < oldCurve.p.length; j++) {
                                newPoints.push({x : oldCurve.p[j].x, y: oldCurve.p[j].y})

                                if (affectedCurves[0] == -1 || // all boundary points are moved
                                    (affectedCurves.length == 1 && j == affectedPointIdx) || // a control point within the a curve is moved
                                    (affectedCurves.length == 2 && i == affectedCurves[0] && j == affectedPointIdx) ||
                                    (affectedCurves.length == 2 && i == affectedCurves[1] && j == 0) ||
                                    // in the case of moving the first point, it will affect the last point of the last curve too
                                    (affectedCurves.length == 2 && i == affectedCurves[1] && 
                                        i == state.zone.boundary.e.length-1 && affectedPointIdx == 0 && 
                                        j == oldCurve.p.length-1)
                                ) {
                                    newPoints[j].x += delta.x
                                    newPoints[j].y += delta.y
                                }   
                            }

                            let newCurve : BezierCurve = {
                                type: "BezierCurve",
                                p : newPoints
                            }

                            newZone.boundary.e.push(newCurve)
                        } else {
                            newZone.boundary.e.push(state.zone.boundary.e[i])
                        }
                        
                    }
                }

                this.currentData = newState

                let cmdMeta = reliableCmdMeta(this.docCtrl.viewport, this.docCtrl.state.id, this.docCtrl.state.id, SeatPlannerCmdType.UPDATE_ITEM)
                let cmd = new UpdateItemCmd(cmdMeta)

                if (event.nativeEvent.type == "mousemove")
                    cmd.setItem(newState.zone, "MOVING_CONTROL")
                else {
                    cmd.setItem(newState.zone, "SELECTED")
                }
                return cmd

                

            case 'click':
                return 1;

            default:
                return
        }

    }

    isSaveable(): boolean {
        return this.currentData.state == "SELECTED" && this.item != null // only in selected state it might have some change
    }

    applySaved(): void {
        let newData : ZoneStateWithItem = {
            isHighlighted: false,
            itemId : this.currentData.itemId,
            newItem : false,
            state : this.currentData.state,
            zone: (this.currentData as ZoneStateWithItem).zone
        }

        this.currentData = newData
    }

    applyDeleted() {
        this.currentData.state = "REMOVED"   // transition to removed
        this.docCtrl.renderMgr.renderSeatZone(this.currentData.itemId)
        this.docCtrl.renderMgr.removeItem(this.currentData.itemId)
    }

    applyCancelDraw(): void {
        let objReg = this.docCtrl.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.docCtrl.viewport.id, this.docCtrl.state.id))

        let item = objReg.getEntity(this.currentData.itemId)

        if (!item) {
            this.applyDeleted()
        } else {
            this.currentData.state = "RENDERED_NO_CONTROL";
            (this.currentData as ZoneStateWithItem).newItem = false
            this.docCtrl.renderMgr.renderSeatZone(this.currentData.itemId)
        }
    }

    isRemoved(): boolean {
        return this.currentData.state == "REMOVED"
    }

    isNewItem(): boolean {
        return this.currentData.newItem
    }

    isDisabled() : boolean {
        return this.currentData.state == "DISABLED"
    }

    updateSeatZoneFromToolState(cs : SeatZoneToolState) {

        if (this.currentData.state != "SELECTED") return

        let cmdMeta = reliableCmdMeta(this.docCtrl.viewport, this.docCtrl.state.id, this.docCtrl.state.id, SeatPlannerCmdType.UPDATE_ITEM)
        let cmd = new UpdateItemCmd(cmdMeta)

        // keep remaining state, update the internal item
        let s : SeatZone = this.item! as SeatZone
        s.label = cs.displayName
        s.sid = cs.seatId
        s.cat = cs.seatCat
        s.lrot = cs.labelRot
        s.lstyle = cs.labelStyle
        cmd.setItem(this.item!, this.currentData.state)

        this.docCtrl.editor.cmdChannel.receive(cmd)
    
    }

}