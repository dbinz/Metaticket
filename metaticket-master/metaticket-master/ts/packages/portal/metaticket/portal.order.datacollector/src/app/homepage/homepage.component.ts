import { CommonModule } from "@angular/common";
import { Component, OnInit } from "@angular/core";
import { ActivatedRoute, Router, RouterModule } from "@angular/router";
import { HttpClientModule } from "@angular/common/http";
import { AppStateService } from "../app.state.service";
import { CustomFieldData, SaveOrderDataCollectedRequest } from "../app.model";
import { OdcGateway } from "../odc.gateway";
import { BroadcastService } from "../broadcast.service";
import { Subject } from "rxjs";
import {Title} from '@angular/platform-browser'
import { AngularFaviconService } from "angular-favicon";
@Component( {
    standalone: true,
    selector: "homepage",
    templateUrl: "homepage.component.html",
    imports: [
        CommonModule, RouterModule, HttpClientModule,
    ]
} )
export class HomepageComponent implements OnInit {

    showCat = false

    saveAction$: Subject<{success: boolean, message: string}|undefined> = new Subject()

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private titleS: Title,
        private faviconS: AngularFaviconService,
        private appS: AppStateService,
        private broadcastS: BroadcastService,
        private odcGateway: OdcGateway,
    ) {}

    ngOnInit() {
        let id = this.route.snapshot.paramMap.get("id")!!

        this.appS.faviconUrl$.subscribe(url => this.faviconS.setFavicon(url))
        this.appS.title$.subscribe(t => this.titleS.setTitle(t))

        this.appS.initialize(id).subscribe(t => {
                if (t.priceCategoriesFormGroup?.size > 0) {
                    this.showCat = true
                    this.router.navigate( ["categories"], { relativeTo: this.route } )
                } else {
                    this.router.navigate( ["individual-tickets"], { relativeTo: this.route } )
                }
            }
        )
    }

    onSave() {
        let errors: string[] = []

        let priceCategories: Map<string, CustomFieldData[]> = new Map()
        let individualTickets: Map<string, CustomFieldData[][]> = new Map()

        this.appS.data?.priceCategoriesFormGroup.forEach((v, k) => {
            let datas = v.tmpls.map(d => {
                d.validator.validate(d.value)
                if(d.error) errors.push(d.error)
                return {
                    id: d.id,
                    name: d.displayName,
                    value: d.type=='Date'? d.value?.getTime() : d.value
                } as CustomFieldData
            })
            priceCategories.set(k, datas)
        })

        this.appS.data?.individualTicketsFormGroup.forEach((v, k) => {
            let datas = v.subFormGroup.map(d =>
                d.tmpls.map(d1 => {
                    if (!d1.validator) debugger
                    d1.validator.validate(d1.value)
                    if(d1.error) errors.push(d1.error)
                    return {
                        id: d1.id,
                        name: d1.displayName,
                        value: d1.type=='Date'? d1.value?.getTime() : d1.value
                    } as CustomFieldData
                }
            ))
            individualTickets.set(k, datas)
        })

        let req: SaveOrderDataCollectedRequest = {
            id: this.appS.data!.id,
            priceCategories: priceCategories,
            individualTickets: individualTickets
        }

        console.log('data collected', req)

        if (errors.length > 0) {
            console.log("invalid data", errors)
            this.broadcastS.onSaveFailed()
            this.saveAction$.next({success: false, message: 'Dữ liệu nhập chưa chính xác'})
            return
        }

        this.odcGateway.saveDataCollected(req).subscribe({
            next: (v) => {
                if(v=='VALID') {
                    this.saveAction$.next({success: true, message: 'Lưu dữ liệu thành công'})
                    this.broadcastS.onSaveSuccess()
                } else {
                    this.saveAction$.next({success: false, message: 'Lưu dữ liệu thất bại'})
                    this.broadcastS.onSaveFailed()
                }
            },
            error: (e) => {
                this.saveAction$.next({success: false, message: 'Lưu dữ liệu thất bại'})
                this.broadcastS.onSaveFailed()
                console.error('save data collected failed... ', e)
            },
            complete: () => console.info('complete saving data collected')
        })
    }
}
