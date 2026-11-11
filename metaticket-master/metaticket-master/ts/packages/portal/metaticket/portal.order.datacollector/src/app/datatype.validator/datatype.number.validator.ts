import { NumberConstraint } from "../app.model";
import { DatatypeValidator } from "./datatype.validator";

export class DatatypeNumberValidator extends DatatypeValidator<NumberConstraint>{

    override validate(value: number): boolean {
        let s = super.validate(value)
        if (!s) {
            this.tmpl.formGroup.onTmplInvalid(this.tmpl)
            return s
        }

        if (value == null) {
            this.tmpl.formGroup.onTmplValid(this.tmpl)
            return true
        }

        if (Number.isNaN(value)) {
            this.tmpl.error = "Chỉ chấp nhận giá trị là số"
            this.tmpl.formGroup.onTmplInvalid(this.tmpl)
            return false
        }

        if (this.constraint?.min && this.constraint.min > value) {
            this.tmpl.error = `Chỉ chấp nhận giá trị > ${this.constraint.min}`
            this.tmpl.formGroup.onTmplInvalid(this.tmpl)
            return false
        }
        if (this.constraint?.max && this.constraint.max < value) {
            this.tmpl.error = `Chỉ chấp nhận giá trị < ${this.constraint.max}`
            this.tmpl.formGroup.onTmplInvalid(this.tmpl)
            return false
        }

        this.tmpl.formGroup.onTmplValid(this.tmpl)
        return true
    }
}
