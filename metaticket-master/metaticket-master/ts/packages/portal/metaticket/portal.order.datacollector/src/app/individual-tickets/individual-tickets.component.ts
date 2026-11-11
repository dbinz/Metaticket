import { Component, ElementRef, OnInit } from "@angular/core";
import { AsyncPipe, KeyValuePipe, NgForOf, NgIf } from "@angular/common";
import { AppStateService } from "../app.state.service";
import { FormGroupComponent } from "../formgroup/form-group.component";
import { initFlowbite } from "flowbite";
import { collapseElement } from "../utils/collapse.htmlelement";

@Component( {
    standalone: true,
    selector: "individual-tickets",
    templateUrl: "individual-tickets.component.html",
    imports: [
        AsyncPipe, NgForOf, NgIf, FormGroupComponent, KeyValuePipe
    ]
})
export class IndividualTicketsComponent implements OnInit {

    constructor(
        public appS: AppStateService,
        private element: ElementRef
    ) {}

    ngOnInit(): void {
        this.appS.fgData$.subscribe( _ => initFlowbite())
    }

    collapse(toHidePrefix: string, toShowPrefix: string|number) {
        collapseElement(this.element.nativeElement, toHidePrefix, toShowPrefix)
    }
}
