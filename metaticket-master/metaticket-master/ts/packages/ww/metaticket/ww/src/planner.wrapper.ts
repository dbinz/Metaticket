/// <reference types="@viclass/ww/typings" />
import {combineLatest, firstValueFrom, map, mergeMap, ReplaySubject} from "rxjs";
import {MFEConfResponse, response} from "./model"
import {
    BoardViewportManager,
    EditorLookup,
    ModuleLookupWithSettings,
    VEventListener,
    ViewportManager,
    ZoomMode,
    ZoomTool
} from "@viclass/editor.core";
import {BoardType, EmbedCoordinator, EmbedCoordinatorConfig} from "@viclass/editor.coordinator/embed"
import {PlanDocCtrl, PlanEditor, PlanEditorType} from "@metaticket/editor.common"

// ZoomTool nội bộ nhận nghịch đảo của %, xem ZoomTool.zoomValues trong @viclass/editor.core (25% -> 100/25 = 4)
const SEAT_PLANNER_DEFAULT_ZOOM_LEVEL = 4

declare global {
    interface Window {
        metaticket: MetaticketWebWrapper
    }
}

export interface MetaticketWebWrapper {
    PlannerWrapper: typeof PlannerWrapper
}

export class PlannerWrapper {

    state: AppStateService
    globalDocId: string | undefined

    constructor(eType: PlanEditorType) {
        this.state = new AppStateService(eType)
    }

    /**
     * Initialize a viewport and load the editor in inside an element
     * @param el
     * @param globalDocId
     */
    async initialize(el: HTMLDivElement, globalDocId: string): Promise<any> {
        this.state.initialize()

        let coord = await firstValueFrom(this.state.coordinator$)
        let viewport = coord.createViewport(el, this.state.edType)
        this.state.setViewport(viewport)

        coord.switchViewportMode(viewport.id, 'EditMode')
        coord.focusViewport(viewport)
        coord.enableAllTools(viewport.id, "common")

        await coord.loadDocOnViewport(viewport, {
            edType: this.state.edType,
            gId: globalDocId
        })

        let ed = coord.editorByType(this.state.edType) as PlanEditor

        let docCtrl = ed.findDocumentByGlobalId(viewport, globalDocId)
        this.globalDocId = globalDocId

        if (!docCtrl) throw new Error("Some error occur! Cannot find doc ctrl")

        ed.selectDoc(docCtrl)

        if (this.state.edType === "SeatPlanner") {
            (viewport as BoardViewportManager).zoom(SEAT_PLANNER_DEFAULT_ZOOM_LEVEL)
        }

        return docCtrl.state

    }

    controllable(): boolean {
        return this.state.boardType == 'board'
    }

    async registerListener(globalDocId: string, listener: VEventListener<any>): Promise<void> {
        let coord = await firstValueFrom(this.state.coordinator$)
        let vm = await firstValueFrom(this.state.viewport$)
        let ed = coord.editorByType(this.state.edType)

        let ctrl = ed.findDocumentByGlobalId(vm, globalDocId) as PlanDocCtrl

        if (!ctrl) throw new Error(`The document ${globalDocId} has not been loaded.`);

        console.log("register doc ctrl", ctrl)

        ctrl.viewEE.registerListener(listener)
    }

    async setDisabled(disabled: boolean, globalDocId: string, itemIds: string[]) {
        let coord = await firstValueFrom(this.state.coordinator$)
        let vm = await firstValueFrom(this.state.viewport$)
        let ed = coord.editorByType(this.state.edType)

        let ctrl = ed.findDocumentByGlobalId(vm, globalDocId) as PlanDocCtrl

        if (!ctrl) throw new Error(`The document ${globalDocId} has not been loaded.`);

        ctrl.setDisabled(disabled, itemIds)
    }

    async clearSelection(globalDocId: string, itemIds?: string[]) {
        let coord = await firstValueFrom(this.state.coordinator$)
        let vm = await firstValueFrom(this.state.viewport$)
        let ed = coord.editorByType(this.state.edType)

        let ctrl = ed.findDocumentByGlobalId(vm, globalDocId) as PlanDocCtrl

        if (!ctrl) throw new Error(`The document ${globalDocId} has not been loaded.`);

        ctrl.clearSelection(itemIds)
    }

    async startZoom(zoomMode: ZoomMode) {
        let coord = await firstValueFrom(this.state.coordinator$) as EmbedCoordinator
        let vm = await firstValueFrom(this.state.viewport$)
        let tb = coord.getCommonToolbar(vm.id)

        let zoom = tb.getTool("zoom") as ZoomTool
        let state = zoom.toolState
        state.zoomMode = zoomMode
        zoom.updateZoom(state)
        tb.focus("zoom")

        let ed = coord.editorByType(this.state.edType) as PlanEditor

        let docCtrl = ed.findDocumentByGlobalId(vm, this.globalDocId!) as PlanDocCtrl

        if (!docCtrl) throw new Error("Some error occur! Cannot find doc ctrl")

        ed.blurDocCtrls([docCtrl])
    }

    async startPan() {
        let coord = await firstValueFrom(this.state.coordinator$) as EmbedCoordinator
        let vm = await firstValueFrom(this.state.viewport$)
        let tb = coord.getCommonToolbar(vm.id)

        tb.focus("pan")
        let ed = coord.editorByType(this.state.edType) as PlanEditor

        let docCtrl = ed.findDocumentByGlobalId(vm, this.globalDocId!) as PlanDocCtrl

        if (!docCtrl) throw new Error("Some error occur! Cannot find doc ctrl")

        ed.blurDocCtrls([docCtrl])
    }

    async clearTool() {
        let coord = await firstValueFrom(this.state.coordinator$) as EmbedCoordinator
        let vm = await firstValueFrom(this.state.viewport$)
        let tb = coord.getCommonToolbar(vm.id)

        let ed = coord.editorByType(this.state.edType) as PlanEditor

        let docCtrl = ed.findDocumentByGlobalId(vm, this.globalDocId!)

        if (!docCtrl) throw new Error("Some error occur! Cannot find doc ctrl")

        ed.selectDoc(docCtrl)

        tb.clearAllFocus()
    }

}

export class AppStateService {

    mfeConf$: ReplaySubject<MFEConfResponse> = new ReplaySubject(1)

    editorList = ['SeatPlanner', 'PolicyDesc']

    // editor lookups
    edConf$ = this.mfeConf$.pipe(map(resp => {
        return resp.descs.filter(v => this.editorList.includes(v.item)).map(desc => desc.impl as EditorLookup)
    }))

    // coordinator look up with settings
    coordModuleLookup$ = this.mfeConf$.pipe(map(resp => {
        let descs = resp.descs.filter(v => v.item == "EmbedCoordinator")

        if (!descs || descs.length == 0) throw new Error("Unable to find the embed coordinator module lookup")

        return descs[0].impl as ModuleLookupWithSettings

    }))

    coordinator$ = new ReplaySubject<EmbedCoordinator>(1)
    viewport$ = new ReplaySubject<ViewportManager>(1)

    boardType ?: BoardType

    constructor(public edType: PlanEditorType = "SeatPlanner") {

        // create the coordinator
        combineLatest([this.coordModuleLookup$, this.edConf$])
            .pipe(mergeMap(async ([lookup, edLookups]) => {
                    let partialConfig = lookup.settings as Partial<EmbedCoordinatorConfig>

                    this.boardType = partialConfig.viewports![this.edType]?.type

                    let module = await viclass.mfeRT.loadRemoteModule({
                        type: "module",
                        remoteEntry: lookup.lookup.remoteEntry,
                        exposedModule: lookup.lookup.exposedModule
                    }).then(moduleInstance => {
                        if (moduleInstance.default) return moduleInstance.default
                        else return moduleInstance;
                    })

                    let ctor = module.EmbedCoordinator as typeof EmbedCoordinator

                    let embedConfig: Partial<EmbedCoordinatorConfig> = {
                        id: "metaticket-embed-coord",
                        edLookups: edLookups,
                    }

                    Object.assign(embedConfig, partialConfig)

                    let coord = new ctor(embedConfig as EmbedCoordinatorConfig)

                    await coord.initialize()
                    await coord.start()

                    console.log("[AppState] Cooridnator initialized and started!")

                    this.coordinator$.next(coord)

                })
            ).subscribe()
    }

    initialize() {
        // retrieve configuration for editors
        this.mfeConf$.next(response)
    }

    setViewport(v: ViewportManager) {
        this.viewport$.next(v)
    }
}

window.metaticket = window.metaticket || {}

Object.assign(window.metaticket, {
    PlannerWrapper: typeof PlannerWrapper
})
