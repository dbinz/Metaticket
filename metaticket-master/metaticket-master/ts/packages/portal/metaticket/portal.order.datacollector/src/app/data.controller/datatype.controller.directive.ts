import { Directive, ElementRef, HostListener, Input } from "@angular/core";
import { CustomFieldTmpl } from "../app.model";

@Directive({
    standalone: true,
    selector: '[datatypeController]',
})
export class DatatypeController {
    @Input() tmpl !: CustomFieldTmpl

    constructor(protected element: ElementRef) {}

    getRawValue(): any {
        return this.element.nativeElement.value
    }

    @HostListener("input")
    @HostListener("dateChange")
    validate(): boolean {
        return this.tmpl.validator.validate(this.getRawValue())
    }

}

@Directive({
    standalone: true,
    selector: '[datatypeBooleanController]',
})
export class DatatypeBooleanController extends DatatypeController {

    override getRawValue(): boolean {
        return this.element.nativeElement.checked
    }
}

@Directive({
    standalone: true,
    selector: '[datatypeDateTimeController]',
})
export class DatatypeDateTimeController extends DatatypeController {

    override getRawValue(): Date {
        return this.tmpl.value
    }
}

