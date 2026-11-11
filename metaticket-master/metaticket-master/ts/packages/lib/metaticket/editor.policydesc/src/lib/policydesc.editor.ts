import {
    DOMElementLayerCtrl,
    EditorCoordinator,
    EditorType,
    LayerOptions,
    LoadingContext,
    RegistryManager,
    SelectContext,
    ToolBar,
    VDocCtrl,
    ViewportManager
} from "@viclass/editor.core";
import {PolicydescToolbar} from "./tools/policydesc.toolbar";
import {PolicydescDocCtrl} from "./policydesc.doc.ctrl";
import {policyDescDocReg, policyDescLayerReg} from "./util";
import {PolicyDescConfig, PolicyDescDocument, PolicyDescLayerCtrl} from "./model";
import {PolicydescDoc, PolicyDescLayer} from "./policydesc.doc";
import {PlanEditor} from "@metaticket/editor.common";

export class PolicydescEditor extends PlanEditor {

    private readonly _id: number;
    private readonly _editorType: EditorType
    readonly regMan: RegistryManager

    constructor(public override conf : PolicyDescConfig, public override coord : EditorCoordinator) {
        super(conf, coord)
        this._id = conf.id
        this._editorType = conf.editorType
        this.regMan = conf.registryManager
    }

    get editorType() : EditorType {
        return this._editorType
    }

    get id() : number {
        return this._id
    }

    override findDocumentByGlobalId(vm: ViewportManager, globalId: string): VDocCtrl | undefined {
        let reg = this.regMan.registry<PolicydescDocCtrl>(policyDescDocReg(vm.id))
        for (let ctrl of reg.allEntities()) {
            if (ctrl.state.globalId == globalId) return ctrl
        }

        return undefined
    }

    override isSupportFeature(featureKey: string): boolean {
        return false
    }

    override createToolbar(): ToolBar<any, any> {
        return new PolicydescToolbar(this)
    }

    override async loadDocumentByGlobalId(globalId: string, loadingContext: LoadingContext) {
        let response = await loadingContext.connector.loadDocumentByGlobalId(-1, globalId, "json")
        this.createDocFromBean(response, loadingContext)
    }

    private createDocFromBean(bean: PolicyDescDocument, loadingContext: LoadingContext): PolicydescDocCtrl {

        let docCtrl = this.createDocCtrl(loadingContext.vm, bean)

        this.createLayerCtrl(loadingContext.vm, docCtrl)

        return docCtrl
    }

    createLayerCtrl(vm : ViewportManager, docCtrl : PolicydescDocCtrl, layerId? : number) : PolicyDescLayerCtrl {
        layerId = layerId || 1

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
            boundary: undefined,
            docLocalId: docCtrl.state.id,
            docGlobalId: docCtrl.state.globalId,
            viewport: vm,
            editor: this,
            state: new PolicyDescLayer(layerId, docCtrl.state.id)   // initially, the layer state doesn't have a boundary
        })

        // link the layer with the doc controller
        layerCtrl.doc = docCtrl
        docCtrl.addLayer(layerCtrl)

        let layerRegistry = this.regMan.registry<PolicyDescLayerCtrl>(policyDescLayerReg(vm.id, docCtrl.state.id))
        layerRegistry.addEntity(layerId, layerCtrl)

        return layerCtrl
    }

    requestLayer(viewport : ViewportManager, add : boolean, options : LayerOptions) : PolicyDescLayerCtrl {
        if (this.conf.docViewMode == "full-viewport") {
            return viewport.requestLayer(DOMElementLayerCtrl, add, options) as PolicyDescLayerCtrl
        } else {
            return viewport.requestLayer(DOMElementLayerCtrl, add, options) as PolicyDescLayerCtrl
        }
    }

    /**
     * There are a few cases where doc boundary can be created.
     * 1. It is created programmatically by the controlling coordinator. In this case, latestBoundary and local id is not available
     * 2. It is created when loading document from server, in this case, latestBoundary and local id is not available
     * 3. It is created when receiving insert doc command from other peer, in this case, latestBoundary and local id is available
     * 4. It is created by a creation tool, through accepting user input on a board, in this case, latest boundary is available and local id is not available
     * @param viewport
     * @param bean
     * @returns
     */
    createDocCtrl(viewport: ViewportManager, bean : PolicyDescDocument): PolicydescDocCtrl {

        let docRegistry = this.regMan.register<PolicydescDocCtrl>(policyDescDocReg(viewport.id))

        // in scenarios where documents are not loaded by localId but only
        // global id is provided, we generate a local id for usage, it doesn't matter anyway
        let localId = docRegistry.getAndIncrementId()

        let state = new PolicydescDoc(bean.id, localId, bean)

        let spDocCtrl = new PolicydescDocCtrl(this, state, viewport)

        docRegistry.addEntity(state.id, spDocCtrl)

        this.regMan.register<PolicyDescLayer>(policyDescLayerReg(viewport.id, state.id))

        return spDocCtrl
    }

    override async start(): Promise<void> {
        return Promise.resolve();
    }

    override selectDoc(doc : VDocCtrl) : SelectContext {
        if (doc instanceof PolicydescDocCtrl) {
            return undefined as any
        }

        throw new Error("Geo Editor couldn't select document of different type")
    }
}
