import {
    BoardViewportManager,
    BoundaryChangeEventData,
    BoundaryRectangle,
    BoundedSVGLayerCtrl,
    MouseEventData,
    Position,
    RectangleObjectBoundary,
    SelectHitContext,
    UnboundedSVGLayerCtrl,
    VDocLayerCtrl,
    VEventListener,
    ViewportManager
} from "@viclass/editor.core";
import {SeatPlannerEditor} from "./seatplanner.editor";
import {SPDoc} from "./seatplanner.doc";
import {RenderManager} from "./objects/render.manager";
import {ObjectCtrlManager} from "./objects/control.manager";
import {markerClass, seatPlannerObjectReg} from "./util";
import {Seat, SeatZone} from "./seatplanner.model";
import {ElementSelectionEvent, PlanDocCtrl, PlanItem, PlanItemType} from "@metaticket/editor.common";

export type SPSVGLayerCtrl = BoundedSVGLayerCtrl | UnboundedSVGLayerCtrl
type CanSit = Seat|SeatZone
export class SPDocCtrl extends PlanDocCtrl {

    /**
     * The boundary of the document
     */
    private _boundary?: RectangleObjectBoundary
    renderMgr : RenderManager
    objCtrlMgr : ObjectCtrlManager

    /**
     * Since all layers are the same size and for geo editor, boundary is actually the boundary of the document, therefore, we store the boundary value
     * here for easy access.
     * @param editor
     * @param state
     * @param vm
     */
    boundaryRect?: BoundaryRectangle = undefined
    private _boundaryChangeListener: VEventListener<BoundaryChangeEventData>[] = []

    layer? : SPSVGLayerCtrl
    selectedItems : Map<number, PlanItem> = new Map()
    lastHighlightId : number = -1000000

    constructor(public override editor: SeatPlannerEditor, public override state: SPDoc, public override viewport: ViewportManager, boundaryRect?: BoundaryRectangle) {
        super(state, editor)
        this.boundaryRect = boundaryRect
        this._boundaryChangeListener.push(this.createBoundaryChangeListener())
        this.renderMgr = new RenderManager(editor, this)
        this.objCtrlMgr = new ObjectCtrlManager(editor, this)
        this.objCtrlMgr.initializeDrawStates(this.state.doc)
    }

    // TODO: this it checking implementation is a slow implementation
    // upgrade to using quad tree or something
    checkHit(event : MouseEventData<any>, l : SPSVGLayerCtrl) : void | SelectHitContext {

        let v = l.viewport as BoardViewportManager

        if (this.layer == l) {

            let hitCtx : void | SelectHitContext = undefined

            let nativeMouse = event.nativeEvent

            let el = nativeMouse.target as Element
            if (el.classList.contains(markerClass) && (el instanceof SVGElement) && el.dataset["iId"])
                hitCtx = { doc : this, hitDetails : {
                    el : el
                }
            }

            return hitCtx
        }
    }

    highlight(el : SVGElement) {

        let id = el.dataset["iId"]
        let type = el.dataset["iType"] as PlanItemType

        if (!id || !type) return // not something to be selected
        let numId = parseInt(id)

        if (this.objCtrlMgr.isDisabled(numId)) return

        if (numId == this.lastHighlightId) return

        this.lastHighlightId = numId
        let obj = this.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.viewport.id, this.state.id)).getEntity(numId)

        this.viewEE.emit({
            source : this,
            eventType: "item-highlight",
            state: {
                items: [obj],
                controlEls: [this.renderMgr.getItemFirstControlEl(obj)!]
            }
        })
    }

    removeHighlight(el : SVGElement) {
        let id = el.dataset["iId"]
        let type = el.dataset["iType"] as PlanItemType

        if (!id || !type) return // not something to be selected
        let numId = parseInt(id)

        if (this.objCtrlMgr.isDisabled(numId)) return

        let obj = this.editor.regMan.registry<PlanItem>(seatPlannerObjectReg(this.viewport.id, this.state.id)).getEntity(numId)
        this.viewEE.emit({
            source : this,
            eventType: "item-lowlight",
            state: {
                items: [obj]
            }
        })
        this.lastHighlightId = -1000000
    }

    selectItem(el : SVGElement, multiple : boolean) {
        let id = el.dataset["iId"]
        let type = el.dataset["iType"] as PlanItemType

        if (!id || !type) return // not something to be selected

        this.objCtrlMgr.selectItem(parseInt(id!), type)
    }

    override clearSelection(seatIds? : string[]) {
        if (!seatIds) this.objCtrlMgr.clearSelection()
        else {
            let items : CanSit[] = this.state.doc.items.filter(v => (v.type =="Seat" || v.type == "SeatZone") && (v as CanSit).sid && (v as CanSit).sid.length > 0 && seatIds.indexOf((v as CanSit).sid) >= 0) as CanSit[]
            this.objCtrlMgr.clearSelection(items)
        }
    }

    override setDisabled(disabled : boolean, seatIds : string[]) {

        let items : CanSit[] = this.state.doc.items.filter(v => (v.type =="Seat" || v.type == "SeatZone") && (v as CanSit).sid && (v as CanSit).sid.length > 0 && seatIds.indexOf((v as CanSit).sid) >= 0) as CanSit[]

        if (disabled) this.objCtrlMgr.disableSeats(items)
        else this.objCtrlMgr.enableSeats(items)
    }

    triggerSelectionChange(triggerItem? : PlanItem) {
        let controlling = this.objCtrlMgr.getControlling().values()
        let removed = this.selectedItems
        this.selectedItems = new Map()
        let items = []
        let hasNewSelection = false

        for (let m of controlling) {
            if (m.item) {
                items.push(m.item)

                if (!removed.has(m.item.id)) hasNewSelection = true
                else removed.delete(m.item.id)   // anything that remains in removed will be triggerred as removed
                this.selectedItems.set(m.item.id, m.item)
            }
        }
        let selectionEvent : ElementSelectionEvent = {
            eventType: "element-selection-change",
            source: this,
            state: {
                items: items
            }
        }

        this.viewport.triggerContentEvent({
            originalEvent: selectionEvent,
            source: SPDocCtrl.name,
            vm: this.viewport
        })

        if (hasNewSelection && this.selectedItems.size > 0) {
            this.viewEE.emit({
                eventType: "item-selected",
                source: this,
                state: {
                    items: items,
                    controlEls : items.map(i => this.renderMgr.getItemFirstControlEl(i)!),
                    triggerItem: triggerItem,
                    triggerEl: this.renderMgr.getItemFirstControlEl(triggerItem)
                }
            })
        }

        if (removed.size > 0) {
            let removedItems : PlanItem[] = []
            for (let i of removed.values()) removedItems.push(i)

            this.viewEE.emit({
                eventType: "item-deselected",
                source: this,
                state: {
                    items: removedItems,
                    triggerItem: triggerItem,
                    triggerEl: this.renderMgr.getItemFirstControlEl(triggerItem)
                }
            })
        }
    }

    override addLayer(layer: VDocLayerCtrl): void {
        super.addLayer(layer)
        this.layer = layer as SPSVGLayerCtrl

        layer.attachRenderer(() => this.render())
    }

    async render() {
        // only render one, because after rendering elements are on the layer and the browser automatically render when anything change
        this.layer?.removeRenderer()

        this.renderMgr.started = true

        let bean = this.state.doc

        if (bean.baseImgUrl) {
            await this.renderMgr.renderImageUrl(bean.baseImgUrl)
        }
        this.renderMgr.applyStyleSheet(bean.styleSheet)

        this.renderMgr.applyDefs(bean.defs)

        this.renderMgr.renderItems(this.state.doc.items)

    }

    checkHitInternal(event : MouseEventData<any>, l : SPSVGLayerCtrl) : void | SelectHitContext {
        return this.checkHit(event, l)
    }

    get boundary(): RectangleObjectBoundary | undefined {
        return this._boundary
    }

    set boundary(v: RectangleObjectBoundary) {
        if (!this.isBoundedView()) throw new Error("Only in bounded view, ")
        this._boundary = v
        this.boundaryRect = v.boundary  // just to make sure the boundary rect is updated properly
        this.registerBoundaryChangeListener(this.createBoundaryChangeListener())
    }

    private createBoundaryChangeListener(): VEventListener<BoundaryChangeEventData> {
        let self = this
        return new (class implements VEventListener<BoundaryChangeEventData> {

            onEvent(eventData: BoundaryChangeEventData): BoundaryChangeEventData {
                self.editor.onDocBoundaryChange(self, eventData)
                return eventData
            }

        })()
    }

    registerBoundaryChangeListener(listener: VEventListener<BoundaryChangeEventData>) {
        this._boundaryChangeListener.push(listener)
        this.boundary?.boundaryChange?.registerListener(listener)
    }

    unregisterBoundaryChangeListener(listener: VEventListener<BoundaryChangeEventData>) {
        this.boundary?.boundaryChange?.unregisterListener(listener)
        this._boundaryChangeListener = this._boundaryChangeListener.filter(l => l != listener)
    }

    select() {
        if (this.isBoundedView()) {
            if (!this.boundary) {
                if (!this.boundaryRect) throw new Error("The document needs a boundary rectangle to create a boundary controller")
                this.boundary = new RectangleObjectBoundary(this.viewport as BoardViewportManager, this.boundaryRect)
            }

            if (this.boundary) {
                this.boundary.enableMouseEvent()
                this.boundary.show()
            }
        }

        this.viewport.float(this.layer!)
    }

    deselect() {
        if (this.isBoundedView()) {
            if (this.boundary) {
                this.boundary.destroy()
                this._boundary = undefined
            }
            this._boundaryChangeListener = []
        }

        this.viewport.sink(this.layer!)
    }

    updateBoundary(boundary: BoundaryRectangle) {
        this.boundaryRect = boundary
        this.boundary?.updateBoundary(boundary) // update the display of the boundary if any

        if (this.isBoundedView() && this.layer) // in full mode, the layer is an unbounded graphic layer
            (this.layer as BoundedSVGLayerCtrl).updateBoundary(boundary)

    }

    isBoundedView() {
        return this.editor.conf.docViewMode == "bounded"
    }
}

export function validateMousePos(pos: Position, doc: SPDocCtrl): boolean {
    if ((doc.editor as SeatPlannerEditor).conf.docViewMode == "bounded") {  // when bounded, boundary must be available
        let tx = Math.min(doc.boundaryRect!.start!.x, doc.boundaryRect!.end!.x)
        let ty = Math.max(doc.boundaryRect!.start!.y, doc.boundaryRect!.end!.y)

        let w = doc.boundaryRect!.width!
        let h = doc.boundaryRect!.height!

        let bx = tx + w
        let by = ty - h

        // check pos inside document boundary or not
        return (pos.x > tx && pos.x < bx) && (pos.y > by && pos.y < ty)
    } else return true
}
