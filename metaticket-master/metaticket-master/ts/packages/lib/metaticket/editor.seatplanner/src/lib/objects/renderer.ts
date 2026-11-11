import {Position, attrsNS} from "@viclass/editor.core"
import {SPDocCtrl} from "../seatplanner.doc.ctrl"
import {Created1State, CurveState, CurveStateMachine, HasAllPoints} from "./curve.state.machine"
import {createControlElementForItem, createControlElementForItemData} from "../util"
import {BezierCurve, Seat, SeatLine} from "../seatplanner.model"
import {HasAllData, SeatStateMachine, SeatStateViewOnlyMachine} from "./seat.state.machine"
import {ControlStateMachine} from "./control.state.machine"
import { ConstructingStateData, ZoneState, ZoneStateMachine, ZoneStateWithItem } from "./zone.state.machine"

export abstract class ItemRenderer {
    controlPoints : SVGElement[] = []   // control points are individual point to control the shape
    controlShapes : SVGElement[] = []   // are the shapes that can be used to control
    els : SVGElement[] = []             // display element of the item

    constructor() {}

    abstract render(docCtrl : SPDocCtrl) : void
}

const seatPropId = "chair"
const seatClass = "seat"
const seatZoneGClass = "sz-g"
const seatCssPrefix = "s-"
const seatZoneCssPrefix = "sz-"
const highlighted = "highlight"
const disabled = "disable"

const seatLineControlPathClass = "c-path"
const seatLineControlPointClass = "c-point"

const seatZoneConstructionPreviewPath = "c-path"
const seatZoneBoundaryControlPointClass = "c-point"
const seatZoneBoundaryControlPath = "c-path"

export const bpIdx = "bpIdx"
export const bpLastIdx = "bpLastIdx"
export const bpBegin = "bpBegin"
export const bpcIdx = "bpcIdx"

export class SeatZoneRenderer extends ItemRenderer {

    state : ZoneStateMachine
    g! : SVGGElement
    circle! : SVGCircleElement
    lastDrawnState : ZoneState | undefined
    curCss? : string
    curActionCat? : string

    constructor(state : ZoneStateMachine) {
        super()
        this.state = state
    }

    override render(docCtrl: SPDocCtrl): void {

        switch (this.state.currentData.state) {
            case "CONSTRUCTING":{
                this.renderConstructing(docCtrl.layer!.svg)
                break
            }

            case "REMOVED": {
                this.removeAllCtrls()
                break
            }

            case "MOVING_CONTROL":
            case "SELECTED" : {
                this.renderSelected(docCtrl.layer!.svg, docCtrl.editor.conf.disableEdit)
                break
            }

            case "RENDERED_NO_CONTROL": {
                if (this.controlPoints.length > 0) this.removeCtrlPoints(docCtrl)
                this.renderControlShapes(docCtrl.layer!.svg)
                break
            }

            default: {
                this.renderControlShapes(docCtrl.layer!.svg)
            }
        }

        this.lastDrawnState = this.state.currentData.state
    }

    renderSelected(container : SVGElement, disableEdit : boolean) {
        let s = this.state.currentData as ZoneStateWithItem
        if (!this.lastDrawnState || this.lastDrawnState == "CONSTRUCTING") {
            this.removeAllCtrls()

            this.renderControlShapes(container)
            this.renderControlPoints(container, true, disableEdit)
        } else if (this.lastDrawnState == "RENDERED_NO_CONTROL") {
            this.renderControlShapes(container)
            this.renderControlPoints(container, true, disableEdit)
        } else if (this.lastDrawnState == "SELECTED" || this.lastDrawnState == "MOVING_CONTROL") {
            this.renderControlShapes(container)
            this.renderControlPoints(container, false, disableEdit)
        }

    }

    renderConstructing(container : SVGElement) {
        let s = this.state.currentData as ConstructingStateData
        let p = s.pos.map(p1 => { return {x : Math.round(p1.x), y: Math.round(p1.y) } })
        let path = `M ${p[0].x} ${p[0].y} L `
        for (let i = 1; i < p.length; i++) {
            path += ` ${p[i].x}, ${ p[i].y}`
        }

        let svg : SVGPathElement

        if (this.lastDrawnState) {
            svg = this.controlShapes[0] as SVGPathElement
            attrsNS(svg, {d : path})
        } else {
            svg = createControlElementForItemData(s.itemId+"", "zone", "path", [seatZoneConstructionPreviewPath]) as SVGPathElement
            this.controlShapes.push(svg)
            attrsNS(svg, {d : path })
            container.appendChild(svg)

            this.renderFirstPos(s.itemId+"", p[0], container)
        }
    }

    private renderFirstPos(itemId : string, pos : Position, container : SVGElement) {

        let el = createControlElementForItemData(itemId, "zone" , 'circle', [seatZoneBoundaryControlPointClass]) as SVGCircleElement
        attrsNS(el, { cx : pos.x, cy : pos.y, r : 5 })

        container.appendChild(el)
        this.controlPoints.push(el)
        
    }

    renderControlShapes(container: SVGElement) {
        let s = this.state.currentData as ZoneStateWithItem

        if (this.controlShapes.length == 0) {
            
            this.g = createControlElementForItem(s.zone, 'g', [seatZoneGClass]) as SVGCircleElement
            let cpath = createControlElementForItem(s.zone, 'path', []) as SVGCircleElement

            this.controlShapes.unshift(cpath)
            container.appendChild(this.g)
            this.g.appendChild(cpath)
        }

        let path = "M"
        let center : Position = {x : 0, y : 0}
        for (let i = 0; i < s.zone.boundary.e.length; i++) {    // generate control points
            let curve = s.zone.boundary.e[i] as BezierCurve
            for (let j = 0; j < curve.p.length; j++) {
                if (j == 0 || j == curve.p.length - 1) {
                    center.x += curve.p[j].x
                    center.y += curve.p[j].y
                }

                if (j == 0 && i > 0) continue

                if (j == curve.p.length - 1 && i == s.zone.boundary.e.length - 1) {
                    path += " z"
                    continue
                }

                if (j == 0 && i == 0) {
                    path += ` ${curve.p[j].x}, ${curve.p[j].y}`
                }

                if (j == 1) {
                    let p = curve.p
                    if (p.length == 2) {
                        path += ` L ${p[1].x} ${p[1].y}`
                    } else if (p.length == 3) {
                        path += ` Q ${p[1].x} ${p[1].y} , ${p[2].x} ${p[2].y} `
                    } else if (p.length == 4) {
                        path += ` C ${p[1].x} ${p[1].y} , ${p[2].x} ${p[2].y} , ${p[3].x} ${p[3].y} `
                    }
                } 

            }
        }

        center.x = center.x / (s.zone.boundary.e.length * 2)
        center.y = center.y / (s.zone.boundary.e.length * 2)

        let pathE = this.controlShapes[0]

        let cpathZClass = seatZoneCssPrefix
        if (s.zone.cat && s.zone.cat.length > 0) cpathZClass += s.zone.cat
        else cpathZClass += "default"

        if (this.curCss && this.curCss != cpathZClass) {
            this.g.classList.replace(this.curCss, cpathZClass)
        } else if (!this.curCss) {
            this.g.classList.add(cpathZClass)
        }

        this.curCss = cpathZClass

        if (s.state == "DISABLED") {
            if (this.curActionCat && this.curActionCat != disabled)
                this.g.classList.replace(this.curActionCat, disabled)
            else if (!this.curActionCat)
                this.g.classList.add(disabled)

            this.curActionCat = disabled
        } else if (s.isHighlighted) {
            if (this.curActionCat && this.curActionCat != highlighted)
                this.g.classList.replace(this.curActionCat, highlighted)
            else if (!this.curActionCat)
                this.g.classList.add(highlighted)

            this.curActionCat = highlighted
        } else {
            if (this.curActionCat) {
                this.g.classList.remove(this.curActionCat)
                delete this.curActionCat
            }
        }

        attrsNS(pathE, { d : path })

        let text : SVGGraphicsElement
        if (this.controlShapes.length == 1) {
            let labelStyle = []
            if (s.zone.lstyle) labelStyle.push(s.zone.lstyle)
            text = createControlElementForItem(s.zone, "text", labelStyle) as SVGTextElement
            this.g.appendChild(text)
            text.style.textRendering = "optimizedSpeed"
            this.controlShapes.push(text)
        } else text = this.controlShapes[1] as SVGTextElement

        let ltransform = ""

        let lPos = s.zone.lpos? { x: s.zone.lpos.x, y : s.zone.lpos.y } : {x : 0, y : 0}
        lPos.x += center.x
        lPos.y += center.y

        ltransform = `translate(${Math.round(lPos.x)} ${Math.round(lPos.y)})`

        if (s.zone.lrot) {
            ltransform += ` rotate(${s.zone.lrot})`
        }

        attrsNS(text, {transform : ltransform})

        if (s.zone.label && s.zone.label.length > 0) {
            text.innerHTML = s.zone.label
        } else if (s.zone.sid) text.innerHTML = s.zone.sid
        else text.innerHTML = ""
    }

    renderControlPoints(container : SVGElement, create : boolean, disableEdit : boolean) {
        if (disableEdit) return
        let s = this.state.currentData as ZoneStateWithItem
        let cpIdx = 0
        for (let i = 0; i < s.zone.boundary.e.length; i++) {    // generate control points
            let curve = s.zone.boundary.e[i] as BezierCurve
            for (let j = 0; j < curve.p.length; j++) {
                if (j == 0 && i > 0) continue

                if (j == curve.p.length - 1 && i == s.zone.boundary.e.length - 1) {
                    continue
                }

                let cpE = create ? 
                            createControlElementForItem(s.zone, 'circle', [seatZoneBoundaryControlPointClass]) as SVGCircleElement : 
                            this.controlPoints[cpIdx]

                if (!cpE) throw new Error("Unable to find existing control point or create new control point")

                attrsNS(cpE, { cx : curve.p[j].x, cy : curve.p[j].y, r : 5 })

                if (create) {
                    cpE.dataset["bpIdx"] = i+"" // store the index of the bezier curve this cp belongs
                    cpE.dataset["bpcIdx"] = j+""  // store the index of the control point of the curve
                    if (j == curve.p.length-1)
                        cpE.dataset["bpLastIdx"] = "y"  // this point is the last index of a curve, meaning this point is also the begining of another curve

                    if (j == 0 && i == 0) {
                        cpE.dataset["bpBegin"] = "y" // this point is the begining of the first curve, meaning it is also the end of the last curve
                    }
                    
                    this.controlPoints.push(cpE)
                    
                    container.appendChild(cpE)
                }
                cpIdx ++
            }
        }
    }

    renderAllCtrls(container : SVGElement) {
        throw new Error("Method not implemented.")
    }
    
    removeCtrlPoints(docCtrl : SPDocCtrl) {
        for (let el of this.controlPoints) el.remove()
        this.controlPoints.splice(0)
    }

    private removeAllCtrls() {
        for (let el of this.controlPoints) el.remove()
        for (let el of this.controlShapes) el.remove()

        this.controlPoints.splice(0)
        this.controlShapes.splice(0)

        if (this.g) this.g.remove()
    }
    
}

export class SeatLineRenderer extends ItemRenderer {

    state : CurveStateMachine

    constructor(state : ControlStateMachine<string>) {
        super()
        if (state instanceof CurveStateMachine)
            this.state = state
        else throw new Error("SeatLineRenderer can only render CurveStateMachine")
    }

    override render(docCtrl: SPDocCtrl): void {
        let s : CurveState = this.state.currentData.state

        switch (s) {
            case "CREATED_1":{
                this.renderFirstPos(docCtrl)
                break
            }
            case "REMOVED": {
                this.removeAllCtrls(docCtrl)
                break
            }
            case "RENDERED_NO_CONTROL": {
                this.removeCtrlPoints(docCtrl)
                this.renderControlShapes((this.state.currentData as HasAllPoints).seatLine, docCtrl.layer!.svg)
                break
            }
            default:{
                this.renderAllCtrls(docCtrl)
            }
        }

    }

    renderFirstPos(d : SPDocCtrl) {
        let s : CurveState = this.state.currentData.state
        if (this.controlPoints.length == 0) {
            let sd : Created1State = this.state.currentData as Created1State

            let el = createControlElementForItem({ id : sd.itemId, type: 'SeatLine' } , 'circle', [seatLineControlPointClass]) as SVGCircleElement
            attrsNS(el, { cx : sd.pos1.x, cy : sd.pos1.y, r : 5 })

            d.layer?.svg.appendChild(el)
            this.controlPoints.push(el)
        }
    }

    removeAllCtrls(docCtrl : SPDocCtrl) {
        for (let el of this.controlPoints) el.remove()
        for (let el of this.controlShapes) el.remove()

        this.controlPoints.splice(0)
        this.controlShapes.splice(0)
    }

    removeCtrlPoints(docCtrl : SPDocCtrl) {
        for (let el of this.controlPoints) el.remove()
        this.controlPoints.splice(0)
    }

    renderControlShapes(seatLine : SeatLine, container : SVGElement) {

        let p = seatLine.line.p.map(p1 => { return {x : Math.round(p1.x), y: Math.round(p1.y) } })
        let path = `M ${p[0].x} ${p[0].y}`
        if (p.length == 2) {
            path += ` L ${p[1].x} ${p[1].y}`
        } else if (p.length == 3) {
            path += ` Q ${p[1].x} ${p[1].y} , ${p[2].x} ${p[2].y} `
        } else if (p.length == 4) {
            path += ` C ${p[1].x} ${p[1].y} , ${p[2].x} ${p[2].y} , ${p[3].x} ${p[3].y} `
        } else {
            throw new Error("Max 2 control points allowed!")
        }

        let svg : SVGPathElement

        if (this.controlShapes.length == 1) {
            svg = this.controlShapes[0] as SVGPathElement
            attrsNS(svg, {d : path})
        } else {
            svg = createControlElementForItem(seatLine, "path", [seatLineControlPathClass]) as SVGPathElement
            this.controlShapes.push(svg)
            attrsNS(svg, {d : path })
            container.appendChild(svg)
        }
    }

    renderControlPoints(seatLine : SeatLine, container : SVGElement) {
        let p = seatLine.line.p.map(p1 => { return {x : Math.round(p1.x), y: Math.round(p1.y) } })

        for (let i = 0; i < p.length; i++) {
            let svg : SVGCircleElement
            if (i < this.controlPoints.length) {
                svg = this.controlPoints[i] as SVGCircleElement
                attrsNS(svg, {cx : p[i].x, cy: p[i].y, r: 5})
            } else {
                svg = createControlElementForItem(seatLine, "circle", [seatLineControlPointClass]) as SVGCircleElement
                this.controlPoints.push(svg)
                container.appendChild(svg)
                attrsNS(svg, {cx : p[i].x, cy: p[i].y, r: 5 })
            }

        }
    }

    renderAllCtrls(d : SPDocCtrl) {
        let state = this.state.currentData as HasAllPoints

        this.renderControlShapes(state.seatLine, d.layer!.svg)

        this.renderControlPoints(state.seatLine, d.layer!.svg)
    }

}

export class SeatRenderer extends ItemRenderer {

    private curCat? : string
    private curActionCat? : string
    state : SeatStateMachine | SeatStateViewOnlyMachine

    private rendered : boolean = false

    constructor(state : ControlStateMachine<string>) {
        super()

        if (state instanceof SeatStateMachine || state instanceof SeatStateViewOnlyMachine)
            this.state = state
        else throw new Error("SeatRenderer can only render SeatStateMachine")
    }

    override render(docCtrl: SPDocCtrl): void {
        let s : HasAllData = this.state.currentData as HasAllData

        switch (s.state) {
            case "REMOVED": {
                this.removeControlShapes(docCtrl.layer!.svg)
                break
            }
            default:{
                this.renderControlShapes(s, s.seat, docCtrl.layer!.svg)

                if (!this.rendered) {   // first time render
                    //if (docCtrl.editor.conf)
                }

                this.rendered = true
            }
        }
    }

    removeControlShapes(container : SVGElement) {
        for (let el of this.controlShapes) el.remove()
        this.controlShapes.splice(0)

        this.rendered = false
    }

    renderChair(seat : Seat, container : SVGGElement) {
        let use : SVGUseElement
        let p = seat.pos

        if (container.childNodes.length == 0) {
            use = createControlElementForItem(seat, "use", []) as SVGUseElement
            use.setAttribute("href", `#${seatPropId}`)
            container.appendChild(use)
        } else use = container.childNodes.item(0) as SVGUseElement

        attrsNS(use, {x : p.x, y : p.y})
    }

    renderDisplayName(seat : Seat, container : SVGGElement) {
        let text : SVGGraphicsElement
        let p = seat.pos

        if (container.childNodes.length == 1) {
            text = createControlElementForItem(seat, "text", []) as SVGTextElement
            container.appendChild(text)
            text.style.textRendering = "optimizedSpeed"
        } else text = container.childNodes.item(1) as SVGTextElement

        attrsNS(text, {x : p.x, y : p.y})  // hacky because the layer is flipped and text doesn't seem to work correctly

        if (seat.displayName) text.innerHTML = seat.displayName
        else if (seat.sid) text.innerHTML = seat.sid
        else text.innerHTML = ""
    }

    renderControlShapes(s : HasAllData, seat : Seat, container : SVGElement) {

        let gEl : SVGGElement

        if (this.controlShapes.length == 1) {
            gEl = this.controlShapes[0] as SVGGElement
        } else {
            gEl = createControlElementForItem(seat, "g", [seatClass]) as SVGGElement
            this.controlShapes.push(gEl)
            container.appendChild(gEl)
        }

        this.renderChair(seat, gEl)
        this.renderDisplayName(seat, gEl)

        let cat : string     // all seat will belong to some category
        if (!seat.cat || seat.cat == "") cat = seatCssPrefix+"default"
        else cat = seatCssPrefix+seat.cat

        if (this.curCat && this.curCat != cat) {
            gEl.classList.replace(this.curCat, cat)
        } else if (!this.curCat) {
            gEl.classList.add(cat)
        }

        this.curCat = cat

        if (s.state == "DISABLED") {
            if (this.curActionCat && this.curActionCat != disabled)
                gEl.classList.replace(this.curActionCat, disabled)
            else if (!this.curActionCat)
                gEl.classList.add(disabled)

            this.curActionCat = disabled
        } else if (s.isHighlighted) {

            if (this.curActionCat && this.curActionCat != highlighted)
                gEl.classList.replace(this.curActionCat, highlighted)
            else if (!this.curActionCat)
                gEl.classList.add(highlighted)

            this.curActionCat = highlighted
        } else {
            if (this.curActionCat) {
                gEl.classList.remove(this.curActionCat)
                delete this.curActionCat
            }
        }

    }

}
