import { SelectionConstraint } from "../app.model";
import { DatatypeValidator } from "./datatype.validator";

export class DatatypeSelectionValidator extends DatatypeValidator<SelectionConstraint>{

    override validate(value: any): boolean {
        let s = super.validate(value)
        if (!s) {
            this.tmpl.formGroup.onTmplValid(this.tmpl)
            return s
        }

        if (value == null) {
            this.tmpl.formGroup.onTmplValid(this.tmpl)
            return true
        }

        if (this.constraint?.options) {
            if (!this.constraint.options.map(o => o.value).includes(value)) {
                this.tmpl.error = `Lựa chọn phải trong danh sách`
                this.tmpl.formGroup.onTmplInvalid(this.tmpl)
                return false
            }
        }

        this.tmpl.formGroup.onTmplValid(this.tmpl)
        return true
    }
}
