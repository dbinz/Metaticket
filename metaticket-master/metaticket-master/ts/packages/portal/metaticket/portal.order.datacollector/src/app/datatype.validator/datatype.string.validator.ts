import { StringConstraint } from "../app.model";
import { DatatypeValidator } from "./datatype.validator";

export class DatatypeStringValidator extends DatatypeValidator<StringConstraint>{

    override validate(value: string): boolean {
        let s = super.validate(value)
        if (!s) {
            this.tmpl.formGroup.onTmplInvalid(this.tmpl)
            return s
        }

        if (value == null) {
            this.tmpl.formGroup.onTmplValid(this.tmpl)
            return true
        }

        if (this.constraint?.regex) {
            if (!value.match(this.constraint.regex)) {
                this.tmpl.error = `Không đúng định dạng`
                this.tmpl.formGroup.onTmplInvalid(this.tmpl)
                return false
            }
        }

        if (this.constraint?.minLength && this.constraint.minLength > value.length) {
            this.tmpl.error = `Tối thiểu ${this.constraint.minLength} ký tự`
            this.tmpl.formGroup.onTmplInvalid(this.tmpl)
            return false
        }
        if (this.constraint?.maxLength && this.constraint.maxLength < value.length) {
            this.tmpl.error = `Tối đa ${this.constraint.maxLength} ký tự`
            this.tmpl.formGroup.onTmplInvalid(this.tmpl)
            return false
        }

        this.tmpl.formGroup.onTmplValid(this.tmpl)
        return true
    }

}
