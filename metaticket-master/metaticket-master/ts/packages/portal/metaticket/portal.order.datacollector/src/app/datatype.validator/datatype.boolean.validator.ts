import { BooleanConstraint } from "../app.model";
import { DatatypeValidator } from "./datatype.validator";


export class DatatypeBooleanValidator extends DatatypeValidator<BooleanConstraint>{

    override validate(value: boolean): boolean {
        let s = super.validate(value)
        if (!s) {
            this.tmpl.formGroup.onTmplInvalid(this.tmpl)
            return s
        }

        if (value == null) {
            this.tmpl.formGroup.onTmplValid(this.tmpl)
            return true
        }

        this.tmpl.value = value

        this.tmpl.formGroup.onTmplValid(this.tmpl)
        return true
    }
}
