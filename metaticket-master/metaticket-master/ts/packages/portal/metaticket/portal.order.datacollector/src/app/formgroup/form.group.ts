import { CustomFieldTmpl } from "../app.model";

export class FormGroup {
    parentFormGroup ?: FormGroup
    readonly tmplsError: CustomFieldTmpl[] = []
    readonly subFormGroupError: FormGroup[] = []

    readonly tmpls: CustomFieldTmpl[] = []
    readonly subFormGroup: FormGroup[] = []

    constructor(
        public name: string,
    ) {}

    get isValid(): boolean {
        return this.tmplsError.length + this.subFormGroupError.length == 0
    }

    validate(): boolean {
        this.subFormGroup.forEach(fg => fg.validate())
        this.tmpls.forEach(t => t.validator.validate(t.value))

        return this.tmplsError.length + this.subFormGroupError.length == 0
    }

    onTmplValid(tmpl: CustomFieldTmpl) {
        let idx = this.tmplsError.indexOf(tmpl)
        if (idx > -1) {
            this.tmplsError.splice(idx, 1)
            if (this.tmplsError.length + this.subFormGroupError.length == 0) this.parentFormGroup?.onFormGroupValid(this)
        }
    }

    onFormGroupValid(fg: FormGroup) {
        let idx = this.subFormGroupError.indexOf(fg)
        if (idx > -1) {
            this.subFormGroupError.splice( idx, 1 )
            if (this.tmplsError.length + this.subFormGroupError.length == 0) this.parentFormGroup?.onFormGroupValid(this)
        }
    }

    onTmplInvalid(tmpl: CustomFieldTmpl) {
        let idx = this.tmplsError.indexOf(tmpl)
        if (idx < 0) {
            this.tmplsError.push(tmpl)
            if (this.tmplsError.length + this.subFormGroupError.length == 1) this.parentFormGroup?.onFormGroupInvalid(this)
        }
    }

    onFormGroupInvalid(fg: FormGroup) {
        let idx = this.subFormGroupError.indexOf(fg)
        if (idx < 0) {
            this.subFormGroupError.push(fg)
            if (this.tmplsError.length + this.subFormGroupError.length == 1) this.parentFormGroup?.onFormGroupInvalid(this)
        }
    }
}
