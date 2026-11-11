import { Component, ElementRef, OnInit } from "@angular/core";
import { AppStateService } from "../app.state.service";
import { AsyncPipe, KeyValuePipe, NgForOf, NgIf } from "@angular/common";
import { DataControllerComponent } from "../data.controller/data.controller.component";
import { FormGroupComponent } from "../formgroup/form-group.component";
import { collapseElement } from "../utils/collapse.htmlelement";

@Component( {
    standalone: true,
    selector: "categories",
    templateUrl: "categories.component.html",
    imports: [
        AsyncPipe, NgForOf, NgIf, DataControllerComponent, FormGroupComponent, KeyValuePipe
    ]
})
export class CategoriesComponent implements OnInit {

    constructor(
        public appS: AppStateService,
        private element: ElementRef
    ) {}

    ngOnInit(): void {
    }

    collapse(toHidePrefix: string, toShowPrefix: string|number) {
        collapseElement(this.element.nativeElement, toHidePrefix, toShowPrefix)
    }
}
