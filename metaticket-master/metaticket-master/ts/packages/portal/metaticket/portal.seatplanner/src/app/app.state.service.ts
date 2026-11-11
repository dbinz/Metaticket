import { Injectable } from "@angular/core"
import { ReplaySubject, combineLatest, map, mergeMap } from "rxjs";
import type { EditorLookup, ModuleLookup, ModuleLookupWithSettings } from "@viclass/editor.core";
import { MFEConfResponse, response } from "./app.model"
import type { EmbedCoordinator, EmbedCoordinatorConfig } from "@viclass/editor.coordinator/embed"
import type { EditorUILookup } from "@viclass/editorui.loader"

/**
 * Containing states of the application
 */
@Injectable({
    providedIn: 'root'
})
export class AppStateService {

    edType = 'SeatPlanner'

    mfeConf$ : ReplaySubject<MFEConfResponse> = new ReplaySubject(1)

    editorList = [this.edType]

    // editor lookups
    edConf$ = this.mfeConf$.pipe(map(resp => {
        return resp.descs.filter(v => this.editorList.includes(v.item)).map(desc => desc.impl as EditorLookup)
    }))

    // editor ui lookups
    edUIConf$ = this.mfeConf$.pipe(map(resp => {
        return resp.descs.filter(v => this.editorList.includes(v.item)).map(desc => desc.ui as EditorUILookup)
    }))

    zoomUIConf$ = this.mfeConf$.pipe(map(resp => {
        return resp.descs.find(v => v.item == "ZoomToolsEditor")!.ui as EditorUILookup
    }))

    commonUIConf$ = this.mfeConf$.pipe(map(resp => {
        return resp.descs.find(v => v.item == "CommonToolsEditor")!.ui as EditorUILookup
    }))

    baseStyleModuleConf$ = this.mfeConf$.pipe(map(resp => {
        return resp.descs.find(v => v.item == "EditorUIBaseStyle")!.ui as ModuleLookup
    }))

    // coordinator look up with settings
    coordModuleLookup$ = this.mfeConf$.pipe(map(resp => {
        let descs = resp.descs.filter(v => v.item == "EmbedCoordinator")

        if (!descs || descs.length == 0) throw new Error("Unable to find the embed coordinator module lookup")

        return descs[0].impl as ModuleLookupWithSettings

    }))

    coordinator$  = new ReplaySubject<EmbedCoordinator>(1)


    constructor() {

        // create the coordinator
        combineLatest([this.coordModuleLookup$, this.edConf$])
        .pipe(mergeMap(async ([lookup, edLookups]) => {
                // console.log(edLookups)
                let module = await viclass.mfeRT.loadRemoteModule({
                    type: "module",
                    remoteEntry: lookup.lookup.remoteEntry,
                    exposedModule: lookup.lookup.exposedModule
                }).then(moduleInstance => {
                    if (moduleInstance.default) return moduleInstance.default
                    else return moduleInstance;
                })

                let ctor = module.EmbedCoordinator as typeof EmbedCoordinator

                let partialConfig = lookup.settings as Partial<EmbedCoordinatorConfig>

                let embedConfig : Partial<EmbedCoordinatorConfig> = {
                    id : "metaticket-embed-coord",
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

}
