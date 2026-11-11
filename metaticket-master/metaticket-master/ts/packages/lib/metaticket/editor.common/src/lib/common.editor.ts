import {
    CreateDocumentResult,
    CreatingContext,
    DocumentEditor, EditorConfig, EditorCoordinator,
    EditorType, LoadingContext, MouseEventData, SelectContext, SelectHitContext,
    SupportInternalSelectFeature,
    SupportSelectFeature, ToolBar, VDocCtrl,
    VDocLayerCtrl, ViewportManager
} from "@viclass/editor.core";
import {PlanDocCtrl} from "./common.doc.ctrl";

export abstract class PlanEditor implements DocumentEditor, SupportSelectFeature, SupportInternalSelectFeature {
    abstract readonly editorType: EditorType;
    abstract readonly id: number;

    constructor(public conf : EditorConfig, public coord : EditorCoordinator) {

    }

    checkHit(layer: VDocLayerCtrl, event: MouseEventData<any>, multiple?: boolean): SelectHitContext | void {
        throw Error("Method not implemented yet")
    }

    checkHitInternal(layer: VDocLayerCtrl, event: MouseEventData<any>, multiple?: boolean): SelectHitContext | void {
        throw Error("Method not implemented yet")
    }

    createDocument(creatingContext: CreatingContext): Promise<CreateDocumentResult> {
        throw Error("Method not implemented yet")
    }

    createToolbar(): ToolBar<any, any> {
        throw Error("Method not implemented yet")
    }

    deselect(selectCtx: SelectContext): any {
    }

    featureSupporter<T>(featureKey: string): T {
        throw Error("Method not implemented yet")
    }

    findDocumentByGlobalId(vm: ViewportManager, globalId: string): VDocCtrl | undefined {
        throw Error("Method not implemented yet")
    }

    highlight(ctx: SelectHitContext): any {
        throw Error("Method not implemented yet")
    }

    isSupportFeature(featureKey: string): boolean {
        throw Error("Method not implemented yet")
    }

    loadDocumentByGlobalId(globalId: string, loadingContext: LoadingContext): Promise<void> {
        throw Error("Method not implemented yet")
    }

    loadDocumentByLocalId(localId: number, loadingContext: LoadingContext): Promise<void> {
        throw Error("Method not implemented yet")
    }

    onFeatureInitialization(featureKey: string, feature: any): Promise<void> {
        throw Error("Method not implemented yet")
    }

    reloadDocumentByLocalId(localId: number, loadingContext: LoadingContext): Promise<void> {
        throw Error("Method not implemented yet")
    }

    removeHighlight(ctx: SelectHitContext): any {
        throw Error("Method not implemented yet")
    }

    select(hitCtx: SelectHitContext, multiple?: boolean): SelectContext {
        throw Error("Method not implemented yet")
    }

    selectDoc(doc: VDocCtrl, multiple?: boolean): SelectContext {
        throw Error("Method not implemented yet")
    }

    selectElement(ctx: SelectHitContext, multiple?: boolean): any {
        throw Error("Method not implemented yet")
    }

    start(): Promise<void> {
        throw Error("Method not implemented yet")
    }

    blurDocCtrls(docCtrls: PlanDocCtrl[]) {}
}
