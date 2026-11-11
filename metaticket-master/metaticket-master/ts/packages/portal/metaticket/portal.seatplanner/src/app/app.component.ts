import {AfterViewInit, Component, ElementRef, ViewChild} from '@angular/core';
import {RouterModule} from '@angular/router'
import {AppStateService} from './app.state.service';
import {EditorUILoaderComponent, EditorUILoaderEvent, EditorUILoaderModule} from '@viclass/editorui.loader';
import type {EmbedCoordinator} from '@viclass/editor.coordinator/embed';
import {FEATURE_SELECTION, SupportSelectFeature, type ViewportManager} from '@viclass/editor.core';
import {concatMap, firstValueFrom, from, mergeMap, Observable, ReplaySubject, tap} from 'rxjs';
import {CommonModule} from '@angular/common';
import {Seat, SeatPlanDocument, SeatPlannerEditor, SPDocCtrl} from '@metaticket/editor.seatplanner';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {FormBuildingResult, FormCreator, FormFlowSubmitEvent, FormUtilModule} from "@hnct/ngutils-form"
import {TicketGenerationForm} from './app.model';
import {HttpClientModule} from '@angular/common/http';
import {TicketGateway} from './ticket.gw';

@Component({
  standalone: true,
  selector: '[app-root]',
  templateUrl: './app.component.html',
  imports: [
    EditorUILoaderModule,
    CommonModule, FormsModule,
    ReactiveFormsModule,
    FormUtilModule, RouterModule, HttpClientModule
  ],
  providers: [
    TicketGateway
  ]
})
export class AppComponent implements AfterViewInit {
  title = '@metaticket/portal.seatplaner';

  @ViewChild("viewport")
  elRef? : ElementRef<HTMLDivElement>

  loadId? : string = ""

  viewport$ : ReplaySubject<ViewportManager> = new ReplaySubject(1)

  uiLoader : ReplaySubject<EditorUILoaderComponent> = new ReplaySubject(1)
  zoomUiLoader : ReplaySubject<EditorUILoaderComponent> = new ReplaySubject(1)

  doc$ = new ReplaySubject<SeatPlanDocument>(1)

  constructor(private appS : AppStateService, private fb : FormBuilder, private ticketGw : TicketGateway) {
  }

  async ngAfterViewInit() {
    this.appS.initialize()
    await firstValueFrom(
      this.appS.coordinator$.pipe(tap(coord => {
        this.initializeUI(coord)
      }))
    )
  }

  async initializeUI(coord: EmbedCoordinator) {
    let viewport = coord.createViewport(this.elRef!.nativeElement, 'SeatPlanner')
    this.viewport$.next(viewport)

    let commonUIConf = await firstValueFrom(this.appS.commonUIConf$)
    let zoomToolUI = await firstValueFrom(this.appS.zoomUIConf$)
    let editorUIConf = (await firstValueFrom(this.appS.edUIConf$)).find(v => v.editorType == 'SeatPlanner')

    await firstValueFrom(this.zoomUiLoader.pipe(tap(loader => loader.createUI([zoomToolUI]))))
    await firstValueFrom(this.uiLoader.pipe(tap(loader => loader.createUI([commonUIConf, editorUIConf!]))))

    coord.switchViewportMode(viewport.id, 'EditMode')
    coord.focusViewport(viewport)
    coord.enableAllTools(viewport.id, "common")
    coord.enableAllTools(viewport.id, "editor")
  }

  async onEditorUILoaderEvent(event : EditorUILoaderEvent) {
    switch (event.eventType) {
        case 'loader-initialized':
            this.uiLoader.next(event.source)
            break
        case 'ui-loaded':
            let loadedUI = event.source.getUI(event.state!)
            let coord = await firstValueFrom(this.appS.coordinator$)
            let viewport = await firstValueFrom(this.viewport$)

            if (event.state == "CommonToolsEditor") {
              let tb = coord.getCommonToolbar(viewport.id)
              loadedUI.connectToolbar(tb)
              event.source.showUI(event.state)
              event.source.pinUI(event.state)
            } else {
              let tb = coord.getEditorToolbar(viewport.id)
              loadedUI.connectToolbar(tb)
              event.source.showUI(event.state!)
            }
            break
        case 'all-ui-loaded':
            break
    }
  }

  async onZoomUILoaderEvent(event : EditorUILoaderEvent) {
    switch (event.eventType) {
        case 'loader-initialized':
            this.zoomUiLoader.next(event.source)
            break
        case 'ui-loaded':
            let loadedUI = event.source.getUI(event.state!)
            let coord = await firstValueFrom(this.appS.coordinator$)
            let viewport = await firstValueFrom(this.viewport$)

            if (event.state == "ZoomToolsEditor") {
              let tb = coord.getCommonToolbar(viewport.id)
              loadedUI.connectToolbar(tb)
              event.source.showUI(event.state)
            }
            break
        case 'all-ui-loaded':
            break
    }
  }

  async createDoc() {
    let viewport = await firstValueFrom(this.viewport$)
    let coord = await firstValueFrom(this.appS.coordinator$)
    let docResult = await coord.createDoc(viewport)

    if (!docResult.globalId) return

    // automatically select the doc controller
    let ed = docResult.editor as SeatPlannerEditor
    this.focusDoc(ed, viewport, docResult.globalId!)
  }

  async loadDoc() {

    if (!this.loadId || this.loadId.length == 0) {
      alert("Please enter name or id")
      return
    }

    let viewport = await firstValueFrom(this.viewport$)
    let coord = await firstValueFrom(this.appS.coordinator$)

    await coord.loadDocOnViewport(viewport, {
      edType: this.appS.edType,
      gId: this.loadId
    })

    let ed = coord.editorByType(this.appS.edType) as SeatPlannerEditor
    this.focusDoc(ed, viewport, this.loadId)
  }

  focusDoc(editor : SeatPlannerEditor, viewport : ViewportManager, globalId : string) {
    if (editor.isSupportFeature(FEATURE_SELECTION)) {
      let supporter = editor.featureSupporter(FEATURE_SELECTION) as SupportSelectFeature
      let docCtrl = editor.findDocumentByGlobalId(viewport, globalId)
      if (docCtrl) supporter.selectDoc(docCtrl)
      else alert("Some error occur! Cannot find doc ctrl")

      this.doc$.next((docCtrl as SPDocCtrl).state.doc)
    }
  }

  buildTicketGenerationForm(doc : SeatPlanDocument) : (data : any) => FormBuildingResult {

    let catMapping : any = {}
    doc.catNames.forEach(name => catMapping[name] = {
      catName : "",
      catPrice : 0
    })

    return (data? : TicketGenerationForm) : FormBuildingResult => {
      if (!data) {
        data = {
          showId : "",
          sessionId : "",
          catMapping : catMapping
        }
      }

      return new FormCreator(this.fb, data).build()
    }
  }

  generateTickets = ((formEvent : FormFlowSubmitEvent) => {
    let groupSize = 500
    this.doc$.pipe(
      mergeMap(doc => {
        let seats = doc.items.filter(item => item.type == "Seat") as Seat[]
        let groups : Seat[][] = []
        let curGroup : Seat[] = []
        for (let i = 0; i < seats.length; i++) {
          curGroup.push(seats[i])
          if ((i + 1) % groupSize == 0 || i+1 == seats.length) {
            groups.push(curGroup)
            curGroup = []
          }
        }

        return from(groups)
      }),
      concatMap(items => this.callServerToGenerateTicketForBatch(items as Seat[], formEvent.data))).subscribe()
  }).bind(this)

  callServerToGenerateTicketForBatch(items: Seat[], data: TicketGenerationForm): Observable<any> {
    let dtos = items.map(item => {
      console.log(item)
      return {
        sid : item.sid,
        seatType : "Seat",
        totCap : 1,
        priceCat : data.catMapping[item.cat].catName,
        basePrice : data.catMapping[item.cat].catPrice
      };
    })

    let req = {
      showId : data.showId,
      sessionId : data.sessionId,
      tickets: dtos
    }

    return this.ticketGw.sendTicket(req)
  }



  catMapControls(f : FormGroup) : string[] {
    let controls : string[] = []
    let catMappingGroup = f.get("catMapping") as FormGroup
    for (let c in catMappingGroup.controls) {
      controls.push(c)
    }
    //console.log(controls)
    return controls
  }
}
