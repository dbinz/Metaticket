import { ChangeDetectionStrategy, Component } from '@angular/core';
import { EditorUIComponent, EditorUILoaderComponent, IEditorUILoaderComponent } from '@viclass/editorui.loader';
import { DefaultToolBar, ToolEventData, ToolEventListener } from '@viclass/editor.core';
import { SeatPlannerToolBar, SeatPlannerToolType, LoadTool, SegmentTool, SeatLineCalParam,
    SegmentToolState, SeatToolState, SeatTool, SeatLineNaming, Seat,
    SeatZoneToolState,
    SeatZoneTool,
    LoadToolState
 } from '@metaticket/editor.seatplanner';
import { BehaviorSubject, ReplaySubject } from 'rxjs';

export interface ErrorMsg {
    msg : string
}

@Component({
    selector: 'lib-seatplanner.ui',
    templateUrl: "./seatplanner.ui.component.html",
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class SeatplannerUiComponent implements EditorUIComponent {

    spToolBar$ = new BehaviorSubject<SeatPlannerToolBar | null>(null)
    activeTool$ = new BehaviorSubject<SeatPlannerToolType | null>(null)

    showing: boolean = false
    uiLoader?: EditorUILoaderComponent

    segment$ : ReplaySubject<SegmentToolState> = new ReplaySubject(1)

    seat$ : ReplaySubject<SeatToolState> = new ReplaySubject(1)

    seatzone$ : ReplaySubject<SeatZoneToolState> = new ReplaySubject(1)

    load$ : ReplaySubject<LoadToolState> = new ReplaySubject(1)

    errors$ = new BehaviorSubject<ErrorMsg[] | null>(null)
    curValueTimeout : any = 0

    connectToolbar<T extends DefaultToolBar<any, any>>(toolbar: T) {

        if (toolbar instanceof SeatPlannerToolBar) {
            this.spToolBar$.next(toolbar)
            toolbar.registerToolListener(SeatplannerUiComponent.ToolListener(this))

            if (toolbar.getTool("segment")) {
                this.segment$.next(toolbar.toolState("segment"))    // initialize the observable
            }

            if (toolbar.getTool("seat")) {
                this.seat$.next(toolbar.toolState("seat"))    // initialize the observable
            }

            if (toolbar.getTool("seatzone")) {
                this.seatzone$.next(toolbar.toolState("seatzone"))    // initialize the observable
            }

            if (toolbar.getTool("loadtool")) {
                this.load$.next(toolbar.toolState("loadtool"))    // initialize the observable
            }
        }
    }

    disableUI() {
    }

    hideUI() {
        this.showing = false
    }

    showUI() {
        this.showing = true
    }

    isShowing(): boolean {
        return this.showing
    }

    loadedBy(uiLoader: IEditorUILoaderComponent) {
        this.uiLoader = uiLoader as EditorUILoaderComponent
    }

    hasTool(toolType: SeatPlannerToolType, tb: SeatPlannerToolBar) {
        return tb.getTool(toolType)
    }

    hasSomeTool(tb: SeatPlannerToolBar) {
        return !tb.empty
    }

    isToolActive(toolType: SeatPlannerToolType, tb: SeatPlannerToolBar) {
        return tb?.curTool == toolType
    }

    switchTool(toolType: SeatPlannerToolType, tb: SeatPlannerToolBar) {
        if (!tb.isToolActive(toolType))
            tb.focus(toolType)
        else tb.blur(toolType)
    }

    readFileContent(target : HTMLInputElement) : Promise<string> {
        return new Promise((rs, rj) => {
            var files = target.files
            if (files && files.item(0)) {
                let reader = new FileReader();
                let file = files.item(0)!
                reader.readAsText(file, "UTF-8");
                reader.onload = function (evt : ProgressEvent<FileReader>) {
                    rs(evt.target!.result as string)
                }
                reader.onerror = function (evt) {
                    rj(evt)
                }
            }
        })
    }

    async changeStyle(tb : SeatPlannerToolBar, event : Event) {
        let style = await this.readFileContent(event.target! as HTMLInputElement)

        let loadTool = tb.getTool("loadtool") as LoadTool

        loadTool.submitStyle(style)
    }

    async changeDefinition(tb : SeatPlannerToolBar, event : Event) {
        let def = await this.readFileContent(event.target! as HTMLInputElement)

        let loadTool = tb.getTool("loadtool") as LoadTool

        loadTool.submitDef(def)
    }

    async generateSeatInLine(tb : SeatPlannerToolBar, event : SeatLineCalParam) {
        let segmentTool = tb.getTool("segment") as SegmentTool

        if (event.capacity == 0) {
            alert("Capacity 0")
        }

        segmentTool.generateSeatsForSelectedLine(event)
    }

    addError(err : ErrorMsg) {
        let value = this.errors$.getValue()

        if (!value) value = []

        value.push(err)

        if (this.curValueTimeout) clearTimeout(this.curValueTimeout)

        //this.curValueTimeout = setTimeout(() => this.clearErrorDisplay(), 5000)
    }

    clearErrorDisplay() {
        this.errors$.next(null)
        this.curValueTimeout = undefined
    }

    updateSeatNaming(tb : SeatPlannerToolBar, naming : SeatLineNaming, lineName : string, seatCat : string) {
        let segmentTool = tb.getTool("segment") as SegmentTool

        try {
            segmentTool.generateSeatNamesForSelectedLine(naming, lineName, seatCat)
        } catch (e : any) {
            if (typeof e === 'object') {
                if (e["duplicates"]) {
                    let dup = e["duplicates"] as Seat[]
                    let dupStr = dup.map(d => d.sid).join(", ")
                    this.errors$.next([{ msg : `Some seat are duplicates ${dupStr}. The update naming action is NOT SUCCESSFUL !!!!`}])
                    segmentTool.refreshStateDataFromSelectedLine()
                }
            }
        }
    }

    async updateSeat(tb : SeatPlannerToolBar, cs : SeatToolState) {
        let seatTool = tb.getTool("seat") as SeatTool

        seatTool.updateFocusedFromState(cs)
    }

    async updateSeatZone(tb : SeatPlannerToolBar, cs : SeatZoneToolState) {
        let seatZoneTool = tb.getTool("seatzone") as SeatZoneTool

        seatZoneTool.updateFocusedFromState(cs)
    }

    async updateZoneConfig(tb : SeatPlannerToolBar, cs : SeatZoneToolState) {
        let seatZoneTool = tb.getTool("seatzone") as SeatZoneTool
        seatZoneTool.updateZoneConfig(cs)
    }

    async saveDocName(tb: SeatPlannerToolBar, name: string) {
        let loadTool = tb.getTool("loadtool") as LoadTool
        loadTool.saveDocName(name)
    }

    async addCategory(tb: SeatPlannerToolBar, categories: string[], newCat: string) {
        if (!newCat || categories.includes(newCat)) return
        let loadTool = tb.getTool("loadtool") as LoadTool
        let newCategories = [...categories, newCat]
        loadTool.saveCategories(newCategories)
    }

    async removeCategory(tb: SeatPlannerToolBar, categories: string[], catToRemove: string) {
        let loadTool = tb.getTool("loadtool") as LoadTool
        let newCategories = categories.filter(c => c !== catToRemove)
        loadTool.saveCategories(newCategories)
    }

    async updateBaseImgUrl(tb: SeatPlannerToolBar, url: string) {
        let loadTool = tb.getTool("loadtool") as LoadTool
        loadTool.updateBaseImgUrl(url)
    }

    async uploadImage(tb: SeatPlannerToolBar, event: Event) {
        let target = event.target as HTMLInputElement
        if (target.files && target.files.length > 0) {
            let file = target.files[0]
            let loadTool = tb.getTool("loadtool") as LoadTool
            loadTool.uploadImage(file)
        }
    }

    private static ToolListener(_p: SeatplannerUiComponent): ToolEventListener<DefaultToolBar<any, any>, any> {
        return new (class implements ToolEventListener<DefaultToolBar<any, any>, any> {
            onEvent(eventData: ToolEventData<any, any>): ToolEventData<any, any> {

                switch (eventData.eventType) {
                    case 'focus': {
                        _p.activeTool$.next(eventData.toolType)
                        break;
                    }
                    case 'blur': {
                        _p.activeTool$.next(null)
                        break
                    }
                    case 'change': {
                        if (eventData.toolType == "segment") _p.segment$.next(eventData.state as SegmentToolState)
                        else if (eventData.toolType == "seat") _p.seat$.next(eventData.state as SeatToolState)
                        else if (eventData.toolType == "seatzone") _p.seatzone$.next(eventData.state as SeatZoneToolState)
                        else if (eventData.toolType == "loadtool") _p.load$.next(eventData.state as LoadToolState)
                        break
                    }
                    default:
                        break;
                }

                return eventData
            }

        })()
    }

}
