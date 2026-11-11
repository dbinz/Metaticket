import { AfterViewInit, Component, ElementRef, ViewChild, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { EditorUILoaderComponent, EditorUILoaderEvent, EditorUILoaderModule } from '@viclass/editorui.loader';
import type { EmbedCoordinator } from '@viclass/editor.coordinator/embed';
import { FEATURE_SELECTION, SupportSelectFeature, type ViewportManager } from '@viclass/editor.core';
import { firstValueFrom, ReplaySubject, tap } from 'rxjs';
import { CommonModule } from '@angular/common';
import { SeatPlanDocument, SeatPlannerEditor, SPDocCtrl } from '@metaticket/editor.seatplanner';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FormUtilModule } from '@hnct/ngutils-form';
import { HttpClientModule } from '@angular/common/http';
import { SeatplannerGateway } from '../../gateway/seatplanner.gateway';
import { SeatMapService } from '../seat-map.service';
import { NbButtonModule, NbCardModule, NbIconModule, NbInputModule, NbTooltipModule } from '@nebular/theme';
import { TicketGateway } from '../../gateway/ticket.gateway';

@Component({
    standalone: true,
    selector: '[draw-seatmap]',
    templateUrl: './seat_map_draw.component.html',
    styleUrls: ['./seat_map_draw.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        EditorUILoaderModule,
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        FormUtilModule,
        RouterModule,
        HttpClientModule,
        NbButtonModule,
        NbCardModule,
        NbIconModule,
        NbInputModule,
        NbTooltipModule,
    ],
    providers: [TicketGateway, SeatplannerGateway],
})
export class SeatMapDrawComponent implements AfterViewInit {
    @ViewChild('viewport')
    elRef?: ElementRef<HTMLDivElement>;

    loadId?: string = '';
    selectedFile?: File;

    viewport$: ReplaySubject<ViewportManager> = new ReplaySubject(1);

    uiLoader: ReplaySubject<EditorUILoaderComponent> = new ReplaySubject(1);
    zoomUiLoader: ReplaySubject<EditorUILoaderComponent> = new ReplaySubject(1);

    doc$ = new ReplaySubject<SeatPlanDocument>(1);

    constructor(
        private seatmapService: SeatMapService,
        private route: ActivatedRoute,
    ) {}

    async ngAfterViewInit() {
        this.seatmapService.initialize();
        await firstValueFrom(
            this.seatmapService.coordinator$.pipe(
                tap(coord => {
                    this.initializeUI(coord);
                }),
            ),
        );

        this.route.params.subscribe(params => {
            if (params['id']) {
                this.loadId = params['id'];
                this.loadDoc();
            }
        });
    }

    async initializeUI(coord: EmbedCoordinator) {
        let viewport = coord.createViewport(this.elRef!.nativeElement, 'SeatPlanner');
        this.viewport$.next(viewport);

        let commonUIConf = await firstValueFrom(this.seatmapService.commonUIConf$);
        let zoomToolUI = await firstValueFrom(this.seatmapService.zoomUIConf$);
        let editorUIConf = (await firstValueFrom(this.seatmapService.edUIConf$)).find( v => v.editorType == 'SeatPlanner');

        await firstValueFrom(this.zoomUiLoader.pipe(tap(loader => loader.createUI([zoomToolUI]))));
        await firstValueFrom(this.uiLoader.pipe(tap(loader => loader.createUI([commonUIConf, editorUIConf!]))));

        coord.switchViewportMode(viewport.id, 'EditMode');
        coord.focusViewport(viewport);
        coord.enableAllTools(viewport.id, 'common');
        coord.enableAllTools(viewport.id, 'editor');
    }

    async onEditorUILoaderEvent(event: EditorUILoaderEvent) {
        switch (event.eventType) {
            case 'loader-initialized':
                this.uiLoader.next(event.source);
                break;
            case 'ui-loaded':
                let loadedUI = event.source.getUI(event.state!);
                let coord = await firstValueFrom(this.seatmapService.coordinator$);
                let viewport = await firstValueFrom(this.viewport$);

                if (event.state == 'CommonToolsEditor') {
                    let tb = coord.getCommonToolbar(viewport.id);
                    loadedUI.connectToolbar(tb);
                    event.source.showUI(event.state);
                    event.source.pinUI(event.state);
                } else {
                    let tb = coord.getEditorToolbar(viewport.id);
                    loadedUI.connectToolbar(tb);
                    event.source.showUI(event.state!);
                }
                break;
            case 'all-ui-loaded':
                break;
        }
    }

    async onZoomUILoaderEvent(event: EditorUILoaderEvent) {
        switch (event.eventType) {
            case 'loader-initialized':
                this.zoomUiLoader.next(event.source);
                break;
            case 'ui-loaded':
                let loadedUI = event.source.getUI(event.state!);
                let coord = await firstValueFrom(this.seatmapService.coordinator$);
                let viewport = await firstValueFrom(this.viewport$);

                if (event.state == 'ZoomToolsEditor') {
                    let tb = coord.getCommonToolbar(viewport.id);
                    loadedUI.connectToolbar(tb);
                    event.source.showUI(event.state);
                }
                break;
            case 'all-ui-loaded':
                break;
        }
    }

    async createDoc() {
        let viewport = await firstValueFrom(this.viewport$);
        let coord = await firstValueFrom(this.seatmapService.coordinator$);
        let docResult = await coord.createDoc(viewport);

        if (!docResult.globalId) return;

        // automatically select the doc controller
        let ed = docResult.editor as SeatPlannerEditor;
        this.focusDoc(ed, viewport, docResult.globalId!);
    }

    async loadDoc() {
        if (!this.loadId || this.loadId.length == 0) {
            alert('Please enter name or id');
            return;
        }

        let viewport = await firstValueFrom(this.viewport$);
        let coord = await firstValueFrom(this.seatmapService.coordinator$);

        await coord.loadDocOnViewport(viewport, {
            edType: this.seatmapService.edType,
            gId: this.loadId,
        });

        let ed = coord.editorByType(this.seatmapService.edType) as SeatPlannerEditor;
        this.focusDoc(ed, viewport, this.loadId);
    }

    focusDoc(editor: SeatPlannerEditor, viewport: ViewportManager, globalId: string) {
        if (editor.isSupportFeature(FEATURE_SELECTION)) {
            let supporter = editor.featureSupporter(FEATURE_SELECTION) as SupportSelectFeature;
            let docCtrl = editor.findDocumentByGlobalId(viewport, globalId);
            if (docCtrl) supporter.selectDoc(docCtrl);
            else alert('Some error occur! Cannot find doc ctrl');

            this.doc$.next((docCtrl as SPDocCtrl).state.doc);
        }
    }
}
