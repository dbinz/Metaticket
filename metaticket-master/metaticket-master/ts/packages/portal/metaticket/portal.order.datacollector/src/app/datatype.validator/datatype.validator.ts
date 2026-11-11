import { CustomFieldTmpl, DatatypeConstraint } from "../app.model";

export class DatatypeValidator<T extends DatatypeConstraint> {
    protected constraint ?: T

    constructor(protected tmpl: CustomFieldTmpl) {
        this.constraint = tmpl.constraints
    }

    validate(value: any): boolean {
        this.tmpl.error = undefined
        if (this.constraint?.require) {
            if (value == null || (typeof value == "string" && value.trim().length == 0)) {
                this.tmpl.error = `Không được để trống`
                this.tmpl.formGroup.onTmplInvalid(this.tmpl)
                return false
            }
        }
        this.tmpl.formGroup.onTmplValid(this.tmpl)
        return true
    }
}
