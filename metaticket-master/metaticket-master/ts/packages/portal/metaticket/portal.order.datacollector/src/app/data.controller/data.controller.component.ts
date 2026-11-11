import { AfterViewInit, ChangeDetectorRef, Component, Input, OnInit } from "@angular/core";
import { AsyncPipe, NgClass, NgForOf, NgIf, NgSwitch, NgSwitchCase, NgTemplateOutlet } from "@angular/common";
import { CustomFieldTmpl } from "../app.model";
import { FormsModule } from "@angular/forms";
import { DatatypeBooleanController, DatatypeController, DatatypeDateTimeController } from "./datatype.controller.directive";
import {MatDatepickerModule} from '@angular/material/datepicker';
import {MatNativeDateModule} from '@angular/material/core';

@Component( {
    standalone: true,
    selector: "data-controller",
    templateUrl: "data.controller.component.html",
    imports: [
        AsyncPipe, NgForOf, NgIf, FormsModule, NgClass, NgTemplateOutlet,
        NgSwitch, NgSwitchCase, DatatypeController, DatatypeBooleanController, DatatypeDateTimeController,
        MatNativeDateModule,
        MatDatepickerModule,
    ]
})
export class DataControllerComponent implements OnInit, AfterViewInit {

    @Input( "tmpl" )
    tmpl!: CustomFieldTmpl

    private _constraint: any

    constructor(private cdr: ChangeDetectorRef) {}

    ngAfterViewInit(): void {
        this.cdr.detectChanges()
    }

    ngOnInit(): void {

    }

}
