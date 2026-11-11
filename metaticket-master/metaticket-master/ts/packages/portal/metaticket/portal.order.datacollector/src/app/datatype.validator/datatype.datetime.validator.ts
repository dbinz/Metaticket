import { DatetimeConstraint } from "../app.model";
import { DatatypeValidator } from "./datatype.validator";

export class DatatypeDatetimeValidator extends DatatypeValidator<DatetimeConstraint>{

    override validate(value?: Date): boolean {
        let s = super.validate(value)
        if (!s) {
            this.tmpl.formGroup.onTmplInvalid(this.tmpl)
            return s
        }

        let date = value?.getTime()

        if (date == null) {
            this.tmpl.formGroup.onTmplValid(this.tmpl)
            return true
        }

        if (this.constraint?.minDate) {
            if (this.constraint.minDate > date) {
                this.tmpl.error = "Thời gian phải sau ngày" + new Date(this.constraint.minDate).toLocaleString()
                this.tmpl.formGroup.onTmplInvalid(this.tmpl)
                return false
            }
        }

        if (this.constraint?.maxDate) {
            if (this.constraint.maxDate < date) {
                this.tmpl.error = "Thời gian phải trước ngày" + new Date(this.constraint.maxDate).toLocaleString()
                this.tmpl.formGroup.onTmplInvalid(this.tmpl)
                return false
            }
        }

        this.tmpl.formGroup.onTmplValid(this.tmpl)
        return true
    }
}
