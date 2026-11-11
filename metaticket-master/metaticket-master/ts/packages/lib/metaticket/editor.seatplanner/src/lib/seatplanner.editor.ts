import {
    AbstractCommand,
    BoundaryChangeEventData,
    BoundaryRectangle,
    BoundedSVGLayerCtrl,
    Cmd,
    CmdChannel,
    CmdPreSyncHook,
    CreateDocumentResult,
    CreatingContext,
    DefaultVDocCtrl,
    EditorConfig,
    EditorCoordinator,
    EditorType,
    FEATURE_HISTORY,
    FEATURE_INTERNAL_SELECTION,
    FEATURE_SELECTION,
    HistoryFeature,
    isPositionBoundaryChanged,
    isSizeBoundaryChanged,
    LayerOptions,
    LoadingContext,
    LocalDocumentEvent,
    MouseEventData,
    RectangleObjectBoundary,
    RegistryManager,
    reliableCmdMeta,
    reliableSaveCmdMeta,
    SelectContext,
    SelectHitContext,
    SelectionFeature,
    SupportSelectFeature,
    ToolBar,
    UnboundedSVGLayerCtrl,
    VDocCtrl,
    VDocLayerCtrl,
    ViewportManager
} from "@viclass/editor.core"
import {SeatPlannerCmdProcessor} from "./seatplanner.cmd.processor";
import {
    deserializer,
    InsertDocCmd,
    InsertLayerCmd,
    RemoveDocCmd,
    SeatPlannerCmdType,
    UpdateDocBoundaryCmd
} from "./seatplanner.cmd";
import {SeatPlannerToolBar} from "./tools/seatplanner.toolbar";
import {SeatPlannerGateway} from "./seatplanner.gateway";
import {SPDoc, SPLayer} from "./seatplanner.doc";
import {SPDocCtrl, SPSVGLayerCtrl} from "./seatplanner.doc.ctrl";
import {seatPlannerDocReg, seatPlannerLayerReg, seatPlannerObjectReg} from "./util";
import {SeatPlanDocument} from "./seatplanner.model";
import {SeatTool} from "./tools/seat.tool";
import {SegmentTool} from "./tools/segment.tool";
import {SeatZoneTool} from "./tools/seatzone.tool";
import {LoadTool} from "./tools/load.tool";
import {PlanEditor, PlanItem} from "@metaticket/editor.common";

export interface SeatPlannerConfig extends EditorConfig {
    disableEdit : boolean
    enableViewEvent : boolean
    disableJsHighlight : boolean
    disableDeselectOnEscape : boolean
}

export class SeatPlannerEditor extends PlanEditor {

    private supportFeature: Map<string, SupportSelectFeature> = new Map()
    private readonly _id: number;
    private readonly _editorType: EditorType
    private readonly _cmdChannel: CmdChannel
    private readonly _cmdProcessor: SeatPlannerCmdProcessor;
    private spGW : SeatPlannerGateway

    disableJsHighLight : boolean   // for the support feature

    private _activated: boolean = false

    readonly regMan: RegistryManager
    private selectionFeature? : SelectionFeature
    private historyFeature? : HistoryFeature

    // because SEATPLANNER support one document per viewport
    // we keep a map to know which local id is on which viewport
    readonly docByViewport: Map<string, number> = new Map()
    readonly docBoundaries : Map<string, RectangleObjectBoundary> = new Map()

    /**
     * Map of doc controllers, one for each viewport. This only stores the
     * currently focused ctrl of each viewport. This is used, for example, in the case of the input command
     * tool which listen to coordinator event to know which viewport is focused, so that when the user enter
     * some inputs, the tool knows which document to work with based on the current active viewport.
     */
    private focusedDocCtrls: Map<string, SPDocCtrl[]> = new Map()

    constructor(public override conf : SeatPlannerConfig, public override coord : EditorCoordinator) {
        super(conf, coord)
        this._id = conf.id
        this._editorType = conf.editorType
        this.regMan = conf.registryManager
        this.disableJsHighLight = conf.disableJsHighlight
        this.spGW = new SeatPlannerGateway(conf.apiUri)

        this._cmdProcessor = new SeatPlannerCmdProcessor(this, this.spGW)
        this._cmdChannel = coord.registerCmdChannel(this._id)
        this.cmdChannel.registerCmdListener(this._cmdProcessor)

        this.cmdChannel.registerDeserializer(deserializer)
        this.cmdChannel.registerPreSyncHook(this.preSyncHook)

        this.supportFeature.set(FEATURE_HISTORY, this)
        this.supportFeature.set(FEATURE_SELECTION, this)
        this.supportFeature.set(FEATURE_INTERNAL_SELECTION, this)
    }

    get editorType() : EditorType {
        return this._editorType
    }

    get cmdChannel() : CmdChannel {
        return this._cmdChannel
    }

    get id() : number {
        return this._id
    }

    override findDocumentByGlobalId(vm: ViewportManager, globalId: string): VDocCtrl | undefined {
        let reg = this.regMan.registry<SPDocCtrl>(seatPlannerDocReg(vm.id))
        for (let ctrl of reg.allEntities()) {
            if (ctrl.state.globalId == globalId) return ctrl
        }

        return undefined
    }

    override checkHit(l: VDocLayerCtrl, event: MouseEventData<any>): void | SelectHitContext {
        if (!l.doc || !(l.doc instanceof SPDocCtrl)) return

        return (l.doc as SPDocCtrl).checkHit(event, l as SPSVGLayerCtrl)
    }

    override select(hitCtx: SelectHitContext): SelectContext {
        let doc = hitCtx.doc as SPDocCtrl
        let ctx = this.selectDoc(doc)
        ctx.selectDetails = hitCtx.hitDetails
        return ctx
    }

    override selectDoc(doc : VDocCtrl) : SelectContext {
        if (doc instanceof SPDocCtrl) {
            this.doFocusDocCtrl(doc)
            let context: SelectContext = {
                boundary: doc.boundary!,    // when this method is called, that means the doc ctrl is bounded, so must have boundary
                doc: doc,
                supporter: this,
                selectDetails: {}
            }

            this.selectionFeature!.onSelect(context)
            return context
        }

        throw new Error("Geo Editor couldn't select document of different type")
    }

    override deselect(selectCtx: SelectContext) {
        let docCtrl = selectCtx.doc as SPDocCtrl
        this.doBlurDocCtrl(docCtrl)
    }

    override checkHitInternal(layer: VDocLayerCtrl, event: MouseEventData<any>): void | SelectHitContext {
        if (!layer.doc || !(layer.doc instanceof SPDocCtrl)) return
        return (layer.doc as SPDocCtrl).checkHitInternal(event, layer as SPSVGLayerCtrl)
    }

    override highlight(ctx: SelectHitContext) {
        let doc = ctx.doc as SPDocCtrl

        doc.highlight(ctx.hitDetails.el)
    }

    override removeHighlight(ctx: SelectHitContext) {
        let doc = ctx.doc as SPDocCtrl

        doc.removeHighlight(ctx.hitDetails.el)
    }

    override selectElement(ctx: SelectHitContext, multiple : boolean) {
        let doc = ctx.doc as SPDocCtrl

        doc.selectItem(ctx.hitDetails.el, multiple)
    }

    override featureSupporter<T>(featureKey: string): T {

        let f = this.supportFeature.get(featureKey)

        if (f) return this as unknown as T

        throw new Error(`Geo Editor doesn't support feature ${featureKey}`)
    }

    override isSupportFeature(featureKey: string): boolean {
        return this.supportFeature.has(featureKey)
    }

    override onFeatureInitialization(featureKey: string, feature: any): Promise<void> {
        switch (featureKey) {
            case FEATURE_SELECTION:
                this.selectionFeature = feature as SelectionFeature
                break
            case FEATURE_HISTORY:
                this.historyFeature = feature as HistoryFeature
                break

            default: break
        }

        return Promise.resolve()
    }

    override async loadDocumentByGlobalId(globalId: string, loadingContext: LoadingContext) {
        let response = await loadingContext.connector.loadDocumentByGlobalId(this.cmdChannel.channelCode, globalId, "json")
        this.createDocFromBean(response, loadingContext)

    }

    override async loadDocumentByLocalId(localId: number, loadingContext: LoadingContext) {
        let response = await loadingContext.connector.loadDocumentByLocalId(this.cmdChannel.channelCode, localId, "json")
        this.createDocFromBean(response, loadingContext, localId)
    }

    override async createDocument(ctx: CreatingContext): Promise<CreateDocumentResult> {

        let viewport = ctx.vm!
        let boundary : BoundaryRectangle | undefined = ctx.boundary

        if (!boundary) boundary = {
            start: {x : 0, y : 200}, end: {x : 200, y : 0},
        }

        let start = boundary.start!
        let end = boundary.end!

        let topLeft = {
            x : Math.min(start.x, end.x),
            y : Math.max(start.y, end.y)
        }

        let bottomRight = {
            x : Math.max(start.x, end.x),
            y : Math.min(start.y, end.y)
        }

        boundary = {
            start: topLeft,
            end: bottomRight
        }

        let response = (await this.spGW.createDoc()) as SeatPlanDocument

        let docRegisty = this.regMan.register<DefaultVDocCtrl>(seatPlannerDocReg(ctx.vm.id))
        let docId = docRegisty.getAndIncrementId()

        let meta = reliableSaveCmdMeta(ctx.vm, 0, docId, docId, SeatPlannerCmdType.INSERT_DOC)

        let cmd = new InsertDocCmd(meta, response)
        cmd.setState(boundary, response.id)

        this.cmdChannel.receive(cmd)  // wait for the processing of the insert command to complete

        // send insert layer cmd
        let docCtrl = this.regMan.registry<SPDocCtrl>(seatPlannerDocReg(viewport.id)).getEntity(docId)
        let layerRegistry = this.regMan.registry<SPSVGLayerCtrl>(seatPlannerLayerReg(viewport.id, docCtrl.state.id))

        let layerId = layerRegistry.getAndIncrementId()
        let insertLayerMeta = reliableSaveCmdMeta(viewport, docCtrl.state, docCtrl.state.id, layerId, SeatPlannerCmdType.INSERT_LAYER)

        let insertLayerCmd = new InsertLayerCmd(insertLayerMeta)
        insertLayerCmd.setState(boundary, 0)
        this.cmdChannel.receive(insertLayerCmd)

        return {
            editor: this,
            globalId: response.id,
            localId: docId
        }
    }

    private createDocFromBean(bean: SeatPlanDocument, loadingContext: LoadingContext, localId?: number): SPDocCtrl {

        let spDocCtrl = this.createDocCtrl(loadingContext.vm, bean, undefined, localId)

        this.createLayerCtrl(loadingContext.vm, spDocCtrl)

        // load all items into the registry
        this.loadAllItems(bean, spDocCtrl)

        return spDocCtrl
    }

    private loadAllItems(bean : SeatPlanDocument, doc : SPDocCtrl) {
        let objReg = this.regMan.registry<PlanItem>(seatPlannerObjectReg(doc.viewport.id, doc.state.id))

        for (let item of bean.items) {
            objReg.addEntity(item.id, item)
        }
    }

    createLayerCtrl(vm : ViewportManager, docCtrl : SPDocCtrl,  layerId? : number) : SPSVGLayerCtrl {
        layerId = layerId || 1

        let initialBoundary = docCtrl.boundary?.boundary || {
            start: { x: 0, y: 200 },
            end: { x: 300, y: 0 },
            width: 300,
            height: 200
        }

        /**
         * Request the layer used by the document controller from the viewport
         * Provide what we know about the layer. The actual positioning of the layer
         * on the board is determined by the viewport.
         *
         * In the case of the classroom, the viewport manager will get the position from the
         * doc mapping, in other case, the layer will be created in a default position with
         * the provided width / height
         */
        let layerCtrl = this.requestLayer(vm, true, {
            boundary: initialBoundary,
            docLocalId: docCtrl.state.id,
            docGlobalId: docCtrl.state.globalId,
            viewport: vm,
            editor: this,
            state: new SPLayer(layerId, docCtrl.state.id, initialBoundary)   // initially, the layer state doesn't have a boundary
        });

        // link the layer with the doc controller
        layerCtrl.doc = docCtrl
        docCtrl.addLayer(layerCtrl)

        let layerRegistry = this.regMan.registry<SPSVGLayerCtrl>(seatPlannerLayerReg(vm.id, docCtrl.state.id))
        layerRegistry.addEntity(layerId, layerCtrl)

        return layerCtrl
    }

    /**
     * There are a few cases where doc boundary can be created.
     * 1. It is created programmatically by the controlling coordinator. In this case, latestBoundary and local id is not available
     * 2. It is created when loading document from server, in this case, latestBoundary and local id is not available
     * 3. It is created when receiving insert doc command from other peer, in this case, latestBoundary and local id is available
     * 4. It is created by a creation tool, through accepting user input on a board, in this case, latest boundary is available and local id is not available
     * @param viewport
     * @param bean
     * @param latestBoundary
     * @param localId
     * @returns
     */
    createDocCtrl(viewport: ViewportManager, bean : SeatPlanDocument, latestBoundary?: BoundaryRectangle, localId? : number): SPDocCtrl {

        let docRegistry = this.regMan.register<SPDocCtrl>(seatPlannerDocReg(viewport.id))

        // in scenarios where documents are not loaded by localId but only
        // global id is provided, we generate a local id for usage, it doesn't matter anyway
        if (!localId) localId = docRegistry.getAndIncrementId()

        let state = new SPDoc(bean.id, localId, bean)

        let spDocCtrl = new SPDocCtrl(this, state, viewport, latestBoundary)

        // if there is an existing boundary controller focused on the viewport, then we use it
        let docBoundaryCtrl = this.docBoundary(viewport.id)

        // sometimes, doc ctrl is not created from doc boundary, e.g. in case of creating doc programatically
        if (docBoundaryCtrl) {
            if (latestBoundary) docBoundaryCtrl.updateBoundary(latestBoundary)    // update the boundary to the latest value
            spDocCtrl.boundary = docBoundaryCtrl

            docBoundaryCtrl.enableMouseEvent()  // when the doc boundary ctrl has been created, the user might interact with the boundary, so we have to enable mouse event

            // remove the doc boundary from the map, because it has been used inside the geo doc controller
            this.docBoundaries.delete(viewport.id)
        }

        docRegistry.addEntity(state.id, spDocCtrl)

        this.regMan.register<SPSVGLayerCtrl>(seatPlannerLayerReg(viewport.id, state.id))

        this.regMan.register<PlanItem>(seatPlannerObjectReg(viewport.id, state.id))

        return spDocCtrl
    }


    override async start(): Promise<void> {
        await this._cmdProcessor.start()
        await this.cmdChannel.start()
    }

    override createToolbar(): ToolBar<any, any> {

        let tb = new SeatPlannerToolBar(this)

        if (!this.conf.disableEdit) {   // empty toolbar if editing is disable
            tb.addTool('seat', new SeatTool(tb))
            tb.addTool('segment', new SegmentTool(tb))
            tb.addTool('seatzone', new SeatZoneTool(tb))
            tb.addTool('loadtool', new LoadTool(tb, this.spGW))
        }

        return tb
    }

    removeDoc(vpId: string, docId: number) {
        let docRegistry = this.regMan.register<SPDocCtrl>(seatPlannerDocReg(vpId))
        let layerRegistry = this.regMan.registry<SPSVGLayerCtrl>(seatPlannerLayerReg(vpId, docId))
        let docCtrl = docRegistry.getEntity(docId)
        if (docCtrl) {
            docRegistry.removeEntity(docId)
            layerRegistry.removeEntity(docCtrl.layer!.state!.id)
            this.blurDocCtrls([docCtrl])
        }
    }

    override blurDocCtrls(docCtrls: SPDocCtrl[]) {
        for (const docCtrl of docCtrls) {
            if (!this.doBlurDocCtrl(docCtrl)) continue

            let context : SelectContext = {
                boundary: docCtrl.boundary!,
                doc: docCtrl,
                supporter: this,
                selectDetails: undefined
            }
            this.selectionFeature!.onDeselect(context)
        }
    }

    private doBlurDocCtrl(docCtrl: SPDocCtrl) : boolean {
        let focusedDocs = this.getFocusedDocs(docCtrl.viewport.id)
        if (!focusedDocs || focusedDocs.length <= 0) return false

        if (focusedDocs.includes(docCtrl)) {
            docCtrl.deselect()
        } else return false

        // TODO: clear all the selected element of the blurred doc ctrl

        let docs = focusedDocs.filter(d => d != docCtrl)
        this.focusedDocCtrls.set(docCtrl.viewport.id, docs)

        return true
    }

    focusDoc(vpId: string, docId: number) {
        let docRegistry = this.regMan.registry<SPDocCtrl>(seatPlannerDocReg(vpId))
        let docCtrl = docRegistry.getEntity(docId)
        this.focusDocCtrl(docCtrl)
    }

    focusDocCtrl(docCtrl: SPDocCtrl) {
        this.doFocusDocCtrl(docCtrl)

        let context: SelectContext = {
            boundary: docCtrl.boundary!,
            doc: docCtrl,
            supporter: this,
            selectDetails: undefined
        }

        this.selectionFeature!.onSelect(context)
    }

    private doFocusDocCtrl(docCtrl: SPDocCtrl) {
        let viewportId = docCtrl.viewport.id

        docCtrl.select()

        if (this.focusedDocCtrls.has(viewportId)) {
            let docs = this.focusedDocCtrls.get(viewportId)!
            docs.push(docCtrl)
            // TODO: highlight selected elements, if any
        } else {
            this.focusedDocCtrls.set(viewportId, [docCtrl])
        }
    }

    getFocusedDocs(viewportId: string): SPDocCtrl[] | undefined {
        return this.focusedDocCtrls.get(viewportId)
    }

    onDocBoundaryChange(geoDocCtrl : SPDocCtrl, event : BoundaryChangeEventData) {

        // send update doc boundary obj
        let meta = reliableCmdMeta(geoDocCtrl.viewport, geoDocCtrl.state.id, geoDocCtrl.state.id, SeatPlannerCmdType.UPDATE_DOC_BOUNDARY)

        let oldBoundary = event.state!.oldBoundary
        let newBoundary = event.state!.newBoundary

        let positionChanged = isPositionBoundaryChanged(oldBoundary, newBoundary)
        let sizeChanged = isSizeBoundaryChanged(oldBoundary, newBoundary)

        let newRect = {
            start: newBoundary.start!,
            end: newBoundary.end!
        }

        let cmd = new UpdateDocBoundaryCmd(meta)
        cmd.setState(newRect, event.eventType == "boundary-update-end", sizeChanged, positionChanged)

        this.cmdChannel.receive(cmd)
    }

    hasDocBoundary(vpId: string): boolean {
        return this.docBoundaries.has(vpId)
    }

    docBoundary(viewportId: string): RectangleObjectBoundary | undefined {
        return this.docBoundaries.get(viewportId)
    }

    requestLayer(viewport : ViewportManager, add : boolean, options : LayerOptions) : SPSVGLayerCtrl {
        if (this.conf.docViewMode == "full-viewport") {
            return viewport.requestLayer(UnboundedSVGLayerCtrl, add, options)
        } else {
            let ctrl = viewport.requestLayer(BoundedSVGLayerCtrl, add, options) as BoundedSVGLayerCtrl

            // this is because the actual position of the layer is
            // not known when loaded but might be set when the layer ctrl is put on the board
            if (ctrl.state) (ctrl.state as SPLayer).boundary = ctrl.boundary

            return ctrl
        }
    }

    /**
     * Return a hook that will be executed after commands are combined before synced to the command gateway.
     * This hook intercept the commands to be synced and check if it is insert doc or insert layer and inform
     * the coordinator correspondingly.
     * @param cmd
     */
    private preSyncHook: CmdPreSyncHook = async (c: Cmd<any>) => {
        let cmd = c as AbstractCommand<SeatPlannerCmdType>
        switch (cmd.cmdType) {
            case SeatPlannerCmdType.INSERT_DOC: {
                let insertDocCmd = cmd as InsertDocCmd

                let globalId = insertDocCmd.state.globalId!

                let event: LocalDocumentEvent = {
                    eventType: "insert-doc",
                    source: this,
                    state: {
                        localId: cmd.meta.versionable!,
                        globalId: globalId,
                        viewportId: cmd.meta.viewport.id
                    }
                }

                await this.coord.onDocumentLocalEvent(event)
                break;
            }
            case SeatPlannerCmdType.REMOVE_DOC: {
                let removeDocCmd = cmd as RemoveDocCmd

                let vm: ViewportManager = cmd.meta.viewport
                let docId = removeDocCmd.state.localId!

                let docRegistry = this.regMan.registry<SPDocCtrl>(seatPlannerDocReg(vm.id))
                let docCtrl = docRegistry.getEntity(docId)

                let event: LocalDocumentEvent = {
                    eventType: "remove-doc",
                    source: this,
                    state: {
                        docLocalId: docId,
                        viewportId: vm.id,
                        layers: [docCtrl.layer!.state!.id]
                    }
                }

                await this.coord.onDocumentLocalEvent(event)
                break
            }
            case SeatPlannerCmdType.INSERT_LAYER: {
                let insertLayerCmd = cmd as InsertLayerCmd
                let layerId = cmd.meta.targetId!
                let docId = cmd.meta.versionable!
                let vm: ViewportManager = cmd.meta.viewport

                let boundary = insertLayerCmd.state.boundary!

                let event: LocalDocumentEvent = {
                    eventType: "insert-layer",
                    source: this,
                    state: {
                        positionStart: { x : boundary.start!.x, y: boundary.start!.y },
                        positionEnd: { x : boundary.end!.x, y : boundary.end!.y },
                        docLocalId: docId,
                        layerId: layerId,
                        viewportId: vm.id,
                        layerIndex: insertLayerCmd.state.zIndex
                    }
                }

                await this.coord.onDocumentLocalEvent(event)
                break;
            }
            case SeatPlannerCmdType.UPDATE_DOC_BOUNDARY: {
                let updateCmd = cmd as UpdateDocBoundaryCmd

                let layerId = 1 // only one layer per document, so layer id is always 1
                let docId = updateCmd.meta.versionable!
                let vm: ViewportManager = updateCmd.meta.viewport
                let boundary = updateCmd.state.boundary!

                let positionChanged = updateCmd.state.positionChanged

                if (positionChanged) {
                    let event: LocalDocumentEvent = {
                        eventType: "update-layer",
                        source: this,
                        state: {
                            positionStart: { x : boundary.start.x, y: boundary.start.y },
                            positionEnd: { x : boundary.end.x, y : boundary.end.y },
                            docLocalId: docId,
                            endPoint: updateCmd.state.endPoint,
                            layerId: layerId,
                            viewportId: vm.id,
                            layerIndex: 0, // TODO it should be retrieved from cmd state
                        }
                    }

                    await this.coord.onDocumentLocalEvent(event)
                }
                break
            }

            default:
                break;
        }
    }

}
