import { Injectable } from "@angular/core";
import { Observable, ReplaySubject } from "rxjs";
import { OdcGateway } from "./odc.gateway";
import {
    CustomFieldTmpl,
    FormGroupData,
    OrderDataCollectedResponse,
    OrderDataCollectorTemplateResponse
} from "./app.model";
import { DatatypeValidator } from "./datatype.validator/datatype.validator";
import { DatatypeStringValidator } from "./datatype.validator/datatype.string.validator";
import { DatatypeNumberValidator } from "./datatype.validator/datatype.number.validator";
import { DatatypeBooleanValidator } from "./datatype.validator/datatype.boolean.validator";
import { DatatypeSelectionValidator } from "./datatype.validator/datatype.selection.validator";
import { DatatypeDatetimeValidator } from "./datatype.validator/datatype.datetime.validator";
import { FormGroup } from "./formgroup/form.group";

/**
 * Containing states of the application
 */
@Injectable({
    providedIn: 'root'
})
export class AppStateService {

    private _data : FormGroupData | undefined

    title$: ReplaySubject<string> = new ReplaySubject<string>()
    faviconUrl$: ReplaySubject<string> = new ReplaySubject<string>()
    fgData$: ReplaySubject<FormGroupData> = new ReplaySubject()

    constructor(private odcGateway: OdcGateway) {}

    private requestValidator(tmpl: CustomFieldTmpl): DatatypeValidator<any> {
        switch (tmpl.type) {
            case "String": return new DatatypeStringValidator(tmpl)
            case "Number": return new DatatypeNumberValidator(tmpl)
            case "Boolean": return new DatatypeBooleanValidator(tmpl)
            case "List": return new DatatypeSelectionValidator(tmpl)
            case "Date": return new DatatypeDatetimeValidator(tmpl)
            default: throw "Not support validator for datatype" + tmpl.type
        }
    }

    private initOdcTmpl(odc: OrderDataCollectorTemplateResponse, fgData: FormGroupData) {
        let individualTickets: Map<string, CustomFieldTmpl[][]> = new Map(Object.entries(odc.individualTickets))
        let priceCategories: Map<string, CustomFieldTmpl[]> = new Map(Object.entries(odc.priceCategories))

        priceCategories.forEach((v, k) => {
            let fg = new FormGroup(k)
            v.forEach(d => {
                d.constraints = d.constraints? JSON.parse(d.constraints): undefined
                d.validator = this.requestValidator(d)
                d.formGroup = fg
                fg.tmpls.push(d)
            })
            fgData.priceCategoriesFormGroup.set(k, fg)
        })

        individualTickets.forEach((v, k) => {
            let fg = new FormGroup(k)
            for (let i = 0; i < v.length; i++) {
                let d = v[i]
                let fg1 = new FormGroup(`${k}-tk${i}`)
                d.forEach( d1 => {
                    d1.constraints = d1.constraints ? JSON.parse( d1.constraints ) : undefined
                    d1.validator = this.requestValidator( d1 )
                    d1.formGroup = fg1
                    fg1.tmpls.push(d1)
                } )
                fg1.parentFormGroup = fg
                fg.subFormGroup.push(fg1)
            }
            fgData.individualTicketsFormGroup.set(k, fg)
        })
    }

    private initOdcValue(fgData: FormGroupData, d: OrderDataCollectedResponse) {
        fgData.priceCategoriesFormGroup.forEach((v, k) => {
            for (let i = 0; i < v.tmpls.length; i++) {
                let value = d.priceCategories.get(k)![i].value
                this.convertOdcValueToTmpl(value, v.tmpls[i])
            }
        })
        fgData.individualTicketsFormGroup.forEach((v, k) => {
            for (let i = 0; i < v.subFormGroup.length; i++) {
                let c = v.subFormGroup[i]
                for (let j = 0; j < c.tmpls.length; j++) {
                    let value = d.individualTickets.get(k)![i][j].value
                    this.convertOdcValueToTmpl(value, c.tmpls[j])
                }
            }
        })
        this._data = fgData
        this.fgData$.next(fgData)
    }

    private convertOdcValueToTmpl(value: any, tmpl: CustomFieldTmpl) {
        if (tmpl.type=='Date' && value!=null) {
            value = new Date( parseInt( value ) )
        }
        tmpl.value = value
        if (tmpl.type=='Boolean') tmpl.value = (value == 'true')
    }

    initialize(id: string): Observable<FormGroupData> {
        this.odcGateway.loadCollectorTemplate(id).subscribe(odc => {

            if (odc.eventName) this.title$.next(odc.eventName)
            if (odc.faviconUrl) this.faviconUrl$.next(odc.faviconUrl)

            let fgData = {
                id: odc.id,
                individualTicketsFormGroup : new Map(),
                priceCategoriesFormGroup : new Map()
            }

            this.initOdcTmpl(odc, fgData)

            if (odc.dataCollectedId) {
                this.odcGateway.loadDataCollected(odc.id).subscribe(d => {
                    d.individualTickets = new Map(Object.entries(d.individualTickets))
                    d.priceCategories = new Map(Object.entries(d.priceCategories))

                    this.initOdcValue(fgData, d)
                })
            } else {
                this._data = fgData
                this.fgData$.next(fgData)
            }
        })
        return this.fgData$
    }

    get data(): FormGroupData | undefined {
        return this._data
    }
}
