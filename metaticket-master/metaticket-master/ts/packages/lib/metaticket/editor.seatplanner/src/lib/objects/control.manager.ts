import {CmdOriginType, EntityRegistry, Position, reliableCmdMeta} from "@viclass/editor.core";
import {
    AbstractCmd,
    CancelDrawingCmd,
    SaveItemsCmd,
    SeatPlannerCmdType,
    StartItemPreviewCmd,
    UpdateItemCmd
} from "../seatplanner.cmd";
import {SPDocCtrl} from "../seatplanner.doc.ctrl";
import {SeatPlannerConfig, SeatPlannerEditor} from "../seatplanner.editor";
import {Seat, SeatLine, SeatPlanDocument, SeatZone} from "../seatplanner.model";
import {seatPlannerDocReg, seatPlannerObjectReg} from "../util";
import {CurveStateMachine} from "./curve.state.machine";
import {SeatStateMachine, SeatStateViewOnlyMachine} from "./seat.state.machine";
import {ControlStateMachine} from "./control.state.machine";
import {PlanItem, PlanItemType} from "@metaticket/editor.common";
import { ZoneStateMachine, ZoneStateViewOnlyMachine } from "./zone.state.machine";


/**
 * Contains all the control data for items
 */
export class ObjectCtrlManager {

    machines : Map<number, ControlStateMachine<string>> = new Map()
    selected : Map<number, ControlStateMachine<string>> = new Map()

    constructor(private ed : SeatPlannerEditor, private doc : SPDocCtrl) {

    }

    initializeDrawStates(seatPlan : SeatPlanDocument) {

        for (let item of seatPlan.items) {
            if (item.type == "SeatLine" && !this.ed.conf.disableEdit) {
                this.initializeCurveMachineForItem(item as SeatLine)
            } else if (item.type == "Seat") {
                this.initializeSeatMachineForItem(item as Seat)
            } else if (item.type == "SeatZone") {
                this.initializeZoneMachineForItem(item as SeatZone)
            }
        }

    }

    getControlling()  {
        return this.selected
    }

    createInnerControlPointsFromEnds(docCtrl : SPDocCtrl, pos1 : Position, pos2 : Position) : Position[] {
        return []
    }

    addToMachineMap(m : ControlStateMachine<string>, itemId : number) {
        this.machines.set(itemId, m)
    }

    getMachine(itemId : number) : ControlStateMachine<string> | undefined {
        return this.machines.get(itemId)
    }

    deleteFromMap(itemId : number) {
        this.machines.delete(itemId)
    }

    processCommand(cmd : AbstractCmd<any>) {
        let vm = cmd.meta.viewport
        let doc = this.ed.regMan.registry<SPDocCtrl>(seatPlannerDocReg(vm.id)).getEntity(cmd.meta.targetId)

        switch (cmd.cmdType) {
            case SeatPlannerCmdType.START_ITEM_PREVIEW: {
                let c = cmd as StartItemPreviewCmd

                let itemType = c.state.existingItem?.type || c.state.newItem?.itemType
                let itemId = c.state.existingItem?.id || c.state.newItem?.itemId
                if (!itemId || !itemType) throw new Error("Something wrong. Item id can't be null!")

                let m : ControlStateMachine<string> | undefined

                if (c.state.newItem) {
                    switch(itemType) {
                        case "SeatLine":
                            m = this.initializeCurveMachineForNew(c, doc)
                            break
                        case "Seat":
                            m = this.initializeSeatMachineForNew(c, doc)
                            break
                        case "SeatZone":
                            m = this.initializeZoneMachineForNew(c, doc)
                            break
                        default:
                            throw new Error(`Item type ${itemType} not supported yet`)
                    }
                } else {
                    m = this.getMachine(c.state.existingItem!.id)
                    if (!m) throw new Error("Unable to find draw state")
                }

                m.applyStartPreview(c)

                this.selected.set(itemId, m)

                if ((doc.editor.conf as SeatPlannerConfig).disableEdit && (m.item?.type == "Seat" || m.item?.type == "SeatZone")) {
                    // in view mode, we will also select everything else of the same sid
                    let curItem = m.item as (Seat | SeatZone)
                    let sameSids = doc.state.doc.items.filter(i => (i.id != curItem.id && (i.type == "Seat" || i.type == "SeatZone") && (i as (Seat | SeatZone)).sid == curItem.sid))

                    if (sameSids.length > 0) {
                        sameSids.forEach(item => {
                            let sameSidM = this.getMachine(item.id)
                            if (sameSidM) {
                                c.setExistingItem(item)
                                sameSidM.applyStartPreview(c)
                                this.selected.set(item.id, sameSidM)
                            }
                        })
                    }
                }

                this.doc.triggerSelectionChange(m.item)

                break
            }

            case SeatPlannerCmdType.UPDATE_ITEM: {
                let c = cmd as UpdateItemCmd
                let id = c.state.item?.id

                if (!id) throw new Error("Something wrong. Item id can't be null!")

                let m = this.getMachine(id)

                // if remote command and state machine is not yet available, just ignore
                if (!m && cmd.meta.origin == CmdOriginType.local) throw new Error(`Something wrong. Unable to locate the item ${id} draw state machine.`)
                else if (m) m.applyUpdateItem(c)

                break
            }

            case SeatPlannerCmdType.SAVE_ITEMS: {
                let c = cmd as SaveItemsCmd

                let objReg = this.ed.regMan.registry<PlanItem>(seatPlannerObjectReg(this.doc.viewport.id, this.doc.state.id))

                for (let item of c.state.added) {
                    this.performAdd(item, objReg)
                }

                this.performRemove(c.state.removed, objReg)

                for (let item of c.state.updated) {
                    let target = objReg.getEntity(item.id)
                    if (target.type == "ItemPlaceHolder") {
                        throw new Error("Some items is marked updated but has no content inside object registry")
                    } else {
                        Object.assign(target, item)
                        this.doc.renderMgr.renderSingleItem(item)
                    }
                }

                break
            }

            case SeatPlannerCmdType.CANCEL_DRAWING: {

                let objReg = this.ed.regMan.registry<PlanItem>(seatPlannerObjectReg(this.doc.viewport.id, this.doc.state.id))

                let c = cmd as CancelDrawingCmd

                let ids = c.state.itemIds || []

                let triggerItem : PlanItem | undefined

                if (ids.length == 0) {

                    // cancel all curve
                    for (let m of this.selected.values()) {
                        ids.push(m.currentData.itemId)
                    }

                } else if (ids.length == 1) {
                    // if there is only one item being deselected, we consider it the trigger item
                    // so that when an item is clicked when it is selected, we know which one is deselected
                    triggerItem = this.selected.get(ids[0])?.item

                    if ((doc.editor.conf as SeatPlannerConfig).disableEdit && (triggerItem?.type == "Seat" || triggerItem?.type == "SeatZone")) {
                        // in view mode, we will also deselect everything else of the same sid
                        let curItem = triggerItem as (Seat | SeatZone)
                        let sameSids = doc.state.doc.items.filter(i => (i.id != curItem.id && (i.type == "Seat" || i.type == "SeatZone") && (i as (Seat | SeatZone)).sid == curItem.sid))
    
                        if (sameSids.length > 0) {
                            sameSids.forEach(item => {
                                ids.push(item.id)   // we add all same sid items for cancelling too
                            })
                        }
                    }
                }

                // cancel all curve
                for (let id of ids) {
                    let target = objReg.getEntity(id)

                    if (target && target.type == "ItemPlaceHolder")
                        objReg.removeEntity(id)
                    let m = this.selected.get(id)

                    if (m) {
                        m.applyCancelDraw()
                        if (m.isRemoved()) {
                            this.deleteFromMap(id)
                        }
                        this.selected.delete(id)
                    }
                }

                this.doc.triggerSelectionChange(triggerItem)

                break
            }

        }
    }

    performAdd(item : PlanItem, objReg : EntityRegistry<PlanItem>) {
        let target = objReg.getEntity(item.id)
        if (!target) {
            // if this is some content added through a different process than user interaction
            this.addNewItemFromBean(objReg, item)
        } else if (target.type == "ItemPlaceHolder") {
            Object.assign(target, item)
            this.doc.state.doc.items.push(target)
            objReg.addEntity(item.id, target)
            this.getMachine(item.id)?.applySaved()
        } else throw new Error("Some items is marked added but already has content inside object registry")
    }

    performRemove(items : PlanItem[], objReg : EntityRegistry<PlanItem>) {
        let remaining : PlanItem[] = []
        let itemMap = new Map<number, PlanItem>()
        items.map(item => itemMap.set(item.id, item))
        for (let i = 0; i < this.doc.state.doc.items.length; i++) {
            let item = this.doc.state.doc.items[i]
            if (!itemMap.has(item.id)) {
                remaining.push(item)
            } else {
                this.getMachine(item.id)?.applyDeleted()
                this.deleteFromMap(item.id)
            }
        }
        this.doc.state.doc.items = remaining
    }

    addNewItemFromBean(objReg : EntityRegistry<PlanItem>, item : PlanItem) {
        objReg.addEntity(item.id, item)
        this.doc.state.doc.items.push(item)
        if (item.type == "Seat") {
            let seat = item as Seat
            let m = this.initializeSeatMachineForItem(seat)
            this.doc.renderMgr.renderSeat(seat.id)
        } else if (item.type == "SeatLine") {
            let seatLine = item as SeatLine
            let m = this.initializeCurveMachineForItem(seatLine)
            this.doc.renderMgr.renderSeat(seatLine.id)
        }
    }

    initializeCurveMachineForItem(item : SeatLine) : CurveStateMachine {

        if (this.getMachine(item.id)) throw new Error("Control machine for item already exist")

        let m = new CurveStateMachine(this.doc, item.id, false)

        m.initializeFromExistingItem(item)
        this.addToMachineMap(m, item.id)

        return m
    }

    initializeSeatMachineForItem(item : Seat) : SeatStateMachine | SeatStateViewOnlyMachine {

        if (this.getMachine(item.id)) throw new Error("Control machine for item already exist")
        let m = this.ed.conf.disableEdit?
                    new SeatStateViewOnlyMachine(this.doc, item.id, false) :  new SeatStateMachine(this.doc, item.id, false)
        m.initializeFromExistingItem(item)
        this.addToMachineMap(m, item.id)
        return m

    }

    initializeZoneMachineForItem(item : SeatZone) : ZoneStateMachine | ZoneStateViewOnlyMachine  {

        if (this.getMachine(item.id)) throw new Error("Control machine for item already exist")
        let m = this.ed.conf.disableEdit?
                    new ZoneStateViewOnlyMachine(this.doc, item.id, false) :  new ZoneStateMachine(this.doc, item.id, false)
        m.initializeFromExistingItem(item)
        this.addToMachineMap(m, item.id)
        return m

    }

    initializeZoneMachineForNew(c : StartItemPreviewCmd, doc : SPDocCtrl) : ZoneStateMachine {
        if (this.ed.conf.disableEdit) throw new Error("Shouldn't create new seat line in view only mode")

        let id = c.state.existingItem?.id || c.state.newItem?.itemId
        if (!id) throw new Error("Unable to initialize state machine for item because id is empty")
        if (this.getMachine(id)) throw new Error("Control machine for item already exist")

        let m = new ZoneStateMachine(doc, id, true)
        this.addToMachineMap(m, id)

        if (c.state.newItem) {
            let reg = doc.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(doc.viewport.id, doc.state.id))
            reg.addEntity(id, { id : id, type : "ItemPlaceHolder"})   // add an empty item inside the object registry first, when the seatline is created fully, it will be added again
        }

        return m
    }

    initializeCurveMachineForNew(c : StartItemPreviewCmd, doc : SPDocCtrl) : CurveStateMachine {

        if (this.ed.conf.disableEdit) throw new Error("Shouldn't create new seat line in view only mode")

        let id = c.state.existingItem?.id || c.state.newItem?.itemId
        if (!id) throw new Error("Unable to initialize state machine for item because id is empty")
        if (this.getMachine(id)) throw new Error("Control machine for item already exist")

        let m = new CurveStateMachine(doc, id, true)
        this.addToMachineMap(m, id)

        if (c.state.newItem) {
            let reg = doc.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(doc.viewport.id, doc.state.id))
            reg.addEntity(id, { id : id, type : "ItemPlaceHolder"})   // add an empty item inside the object registry first, when the seatline is created fully, it will be added again
        }

        return m
    }

    initializeSeatMachineForNew(c : StartItemPreviewCmd, doc : SPDocCtrl) : SeatStateMachine {

        if (this.ed.conf.disableEdit) throw new Error("Shouldn't create new seat in view only mode")

        let id = c.state.existingItem?.id || c.state.newItem?.itemId
        if (!id) throw new Error("Unable to initialize state machine for item because id is empty")
        if (this.getMachine(id)) throw new Error("Control machine for item already exist")

        let m = new SeatStateMachine(doc, id, true)
        this.addToMachineMap(m, id)

        if (c.state.newItem) {
            let reg = doc.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(doc.viewport.id, doc.state.id))
            reg.addEntity(id, { id : id, type : "ItemPlaceHolder"})   // add an empty item inside the object registry first, when the seatline is created fully, it will be added again
        }

        return m
    }

    selectItem(id : number, type? : PlanItemType) {

        let m = this.machines.get(id)

        if (((m instanceof  SeatStateMachine) || (m instanceof SeatStateViewOnlyMachine) ||
            (m instanceof  ZoneStateMachine) || (m instanceof ZoneStateViewOnlyMachine)) && m.isDisabled())
            return

        if (this.selected.has(id)) {    // reselection result in unselected

            let meta = reliableCmdMeta(this.doc.viewport, this.doc.state.id, this.doc.state.id, SeatPlannerCmdType.CANCEL_DRAWING)
            let cmd = new CancelDrawingCmd(meta)
            cmd.setItemIds([id])

            this.doc.editor.cmdChannel.receive(cmd)

        } else {
            let meta = reliableCmdMeta(this.doc.viewport, this.doc.state.id, this.doc.state.id, SeatPlannerCmdType.START_ITEM_PREVIEW)
            let cmd = new StartItemPreviewCmd(meta)
            let obj = this.doc.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.doc.viewport.id, this.doc.state.id)).getEntity(id)

            if (!obj) throw new Error(`Select some unknown of id ${id}`)

            cmd.setExistingItem(obj)

            this.doc.editor.cmdChannel.receive(cmd)
        }
    }

    clearSelection(seats? : (Seat | SeatZone) []) {

        let ids : number[] = []
        if (!seats) { // clear all
            for (let i of this.selected.keys())
                ids.push(i)
        } else ids = seats.map(v => v.id)

        let meta = reliableCmdMeta(this.doc.viewport, this.doc.state.id, this.doc.state.id, SeatPlannerCmdType.CANCEL_DRAWING)
        let cmd = new CancelDrawingCmd(meta)
        cmd.setItemIds(ids)

        this.doc.editor.cmdChannel.receive(cmd)

    }

    disableSeats(items : (Seat | SeatZone) []) {

        if (!items || items.length == 0) return
        // move to RENDERED_NO_CONTROL first for all the selected seats
        // use cancel draw to do this

        let itemsToCancelSelection = items.map(v => v.id).filter(id => this.selected.has(id))

        if (itemsToCancelSelection.length > 0) {
            let meta = reliableCmdMeta(this.doc.viewport, this.doc.state.id, this.doc.state.id, SeatPlannerCmdType.CANCEL_DRAWING)
            let cmd = new CancelDrawingCmd(meta)

            cmd.setItemIds(itemsToCancelSelection)
            this.doc.editor.cmdChannel.receive(cmd)
        }

        // TODO: convert this to a command
        for (let i of items) {
            let m = this.machines.get(i.id) as SeatStateViewOnlyMachine | ZoneStateViewOnlyMachine | undefined
            if (m && !m.isDisabled()) m.applyDisable()
        }
    }

    enableSeats(items : (Seat | SeatZone) []) {

        if (!items || items.length == 0) return
        // TODO: convert this to a command
        for (let i of items) {
            let m = this.machines.get(i.id) as SeatStateViewOnlyMachine | ZoneStateViewOnlyMachine | undefined
            if (m && m.isDisabled()) m.applyEnable()
        }
    }

    isDisabled(id : number) : boolean {
        let m = this.machines.get(id)

        if (((m instanceof  SeatStateMachine) || (m instanceof SeatStateViewOnlyMachine) || 
            (m instanceof  ZoneStateMachine) || (m instanceof ZoneStateViewOnlyMachine)) && m.isDisabled())
            return true

        return false
    }
}
