import {attrsNS, Position, xmlns} from "@viclass/editor.core";
import {UIBehaviorCtrl} from "../seatplanner.api";
import {SPDocCtrl} from "../seatplanner.doc.ctrl";
import {ItemRenderer, SeatLineRenderer, SeatRenderer, SeatZoneRenderer} from "./renderer";
import {SeatPlannerEditor} from "../seatplanner.editor";
import {PlanItem} from "@metaticket/editor.common";
import { ZoneStateMachine } from "./zone.state.machine";

type ItemId = number

export class RenderManager implements UIBehaviorCtrl {

    started : boolean = false

    // keep track of all elements has been rendered so that we can get it out quickly
    renderers : Map<ItemId, ItemRenderer> = new Map()

    private baseImgEl? : SVGImageElement
    private style? : SVGStyleElement
    private defs?: SVGDefsElement;

    constructor(private ed : SeatPlannerEditor, private doc : SPDocCtrl) {}

    getItemFirstControlEl(item? : PlanItem) : SVGElement | undefined {
        if (!this.started) return
        if (!item) return
        let renderer = this.renderers.get(item.id)
        return renderer?.controlShapes[0]
    }

    renderItems(items : PlanItem[]) {

        if (!this.started) return

        for (let item of items) {
            this.renderSingleItem(item)
        }
    }

    removeItem(id : number) {
        this.renderers.delete(id)
    }

    renderSingleItem(item : PlanItem) {
        if (!this.started) return
        if (item.type == 'Seat') {
            return this.renderSeat(item.id)
        } else if (item.type == "SeatLine") {
            return this.renderSeatLine(item.id)
        } else if (item.type == "SeatZone") {
            return this.renderSeatZone(item.id)
        } else {
            throw new Error("Item type not known")
        }
    }

    renderSeat(id : number) {
        if (!this.started) return
        let renderer = this.renderers.get(id)
        if (!renderer) {
            let state = this.doc.objCtrlMgr.getMachine(id)
            if (!state) throw new Error(`Item ${id} control state has not been initialized`)
            renderer = new SeatRenderer(state)
            this.renderers.set(id, renderer)
        }

        renderer.render(this.doc)
    }

    renderImageUrl(url : string, layerPos? : Position) : Promise<SVGImageElement> {
        if (!this.started) throw "Cannot render. Renderer has not been started"
        
        if (this.baseImgEl) {
            this.baseImgEl.remove()
        }

        return new Promise((rs, rj) => {
            let image = document.createElementNS(xmlns, "image") as SVGImageElement

            let imgObj = new Image()
            imgObj.src = url

            imgObj.onload = () => {
                attrsNS(image, { x : (- imgObj.naturalWidth / 2), y: (- imgObj.naturalHeight / 2) })
                image.setAttributeNS("http://www.w3.org/1999/xlink", "href", url)
                this.doc.layer?.svg.prepend(image)

                imgObj.onload = null
                rs(image)
            }

            this.baseImgEl = image
        })

    }

    renderSeatLine(id : number) {
        if (!this.started) return

        if (this.ed.conf.disableEdit) return

        let renderer = this.renderers.get(id)
        if (!renderer) {
            let state = this.doc.objCtrlMgr.getMachine(id)
            if (!state) throw new Error(`Item ${id} control state has not been initialized`)
            renderer = new SeatLineRenderer(state)
            this.renderers.set(id, renderer)
        }
        renderer.render(this.doc)
    }

    renderSeatZone(id : number) {
        if (!this.started) return

        let renderer = this.renderers.get(id)
        if (!renderer) {
            let state = this.doc.objCtrlMgr.getMachine(id)
            if (!state) throw new Error(`Item ${id} control state has not been initialized`)
            renderer = new SeatZoneRenderer(state as ZoneStateMachine)
            this.renderers.set(id, renderer)
        }
        renderer.render(this.doc)
    }

    applyStyleSheet(styleSheet : string) : SVGStyleElement {
        if (!this.started) throw "Cannot render. Renderer has not been started"
        let style = document.createElementNS(xmlns, "style") as SVGStyleElement

        this.style = style
        style.innerHTML = styleSheet

        this.doc.layer?.svg.prepend(style)

        return style
    }

    applyDefs(defs : string) : SVGDefsElement {
        if (!this.started) throw "Cannot render. Renderer has not been started"
        let defEl = document.createElementNS(xmlns, "defs") as SVGDefsElement
        this.defs = defEl

        defEl.innerHTML = defs

        this.doc.layer?.svg.prepend(defEl)

        return defEl
    }

    rendererOf(id : number) : ItemRenderer | undefined {
        return this.renderers.get(id)
    }

    disable(item: PlanItem[]): void {
        throw new Error("Method not implemented.");
    }
    enable(item: PlanItem[]): void {
        throw new Error("Method not implemented.");
    }
    highlight(item: PlanItem[]): void {
        throw new Error("Method not implemented.");
    }
    removeHightlight(item: PlanItem[]): void {
        throw new Error("Method not implemented.");
    }

}
