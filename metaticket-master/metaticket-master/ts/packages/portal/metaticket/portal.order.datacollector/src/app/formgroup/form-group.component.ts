import { Component, Input } from "@angular/core";
import { AsyncPipe, NgForOf, NgIf } from "@angular/common";
import { CustomFieldTmpl } from "../app.model";
import { DataControllerComponent } from "../data.controller/data.controller.component";
import { FormGroup } from "./form.group";

@Component( {
    standalone: true,
    selector: "form-group",
    templateUrl: "form-group.component.html",
    imports: [
        AsyncPipe, NgForOf, NgIf, DataControllerComponent
    ]
})
export class FormGroupComponent {

    @Input("fg")
    fg !: FormGroup

    constructor() {
    }
}
