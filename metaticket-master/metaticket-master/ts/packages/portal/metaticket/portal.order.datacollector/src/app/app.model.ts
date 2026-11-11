import { DatatypeValidator } from "./datatype.validator/datatype.validator";
import { FormGroup } from "./formgroup/form.group";

export type CustomFieldType = "String"| "Number"| "Boolean"| "Date"| "List"

export interface DatatypeConstraint {
    require ?: boolean
}

export interface StringConstraint extends DatatypeConstraint {
    minLength ?: number
    maxLength ?: number
    regex ?: string
}

export interface DatetimeConstraint extends DatatypeConstraint {
    dateOnly ?: boolean
    minDate ?: number
    maxDate ?: number
}

export interface NumberConstraint extends DatatypeConstraint {
    min ?: number
    max ?: number
}

export interface BooleanConstraint extends DatatypeConstraint {

}

export interface SelectionOption {
    value: string
    name: string
}

export interface SelectionConstraint extends DatatypeConstraint {
    options : SelectionOption[]
}

export interface CustomFieldTmpl extends DatatypeConstraint {
    id: string
    type: CustomFieldType
    displayName?: string
    constraints?: any
    note?: string
    value?: any
    error?: string
    validator: DatatypeValidator<DatatypeConstraint>
    formGroup: FormGroup
}

export interface FormGroupData {
    id: string

    individualTicketsFormGroup: Map<string, FormGroup>

    priceCategoriesFormGroup: Map<string, FormGroup>
}

export interface OrderDataCollectorTemplateResponse {
    id: string
    showId: string
    sessionId: string
    resId: string
    eventName: string
    faviconUrl: string

    // Map<catId, List<List<fieldData>> store template for each ticket in category
    individualTickets: Map<string, CustomFieldTmpl[][]>
    // Map<catId, List<fieldData>> store template for each category, used for all tickets
    priceCategories: Map<string, CustomFieldTmpl[]>

    dataCollectedId?: string
}

export type OrderDataCollectedStatus = 'EMPTY' | 'INVALID' | 'VALID'

export interface OrderDataCollectedResponse {
    id: string
    showId: string
    sessionId: string
    resId: string
    status: OrderDataCollectedStatus

    // Map<catId, List<List<fieldData>> store template for each ticket in category
    individualTickets: Map<string, CustomFieldData[][]>
    // Map<catId, List<fieldData>> store template for each category, used for all tickets
    priceCategories: Map<string, CustomFieldData[]>
}

export interface CustomFieldData {
    id: string
    name: string
    value ?: string
}

export interface SaveOrderDataCollectedRequest {
    id: string

    // Map<catId, List<List<fieldData>> store template for each ticket in category
    individualTickets: Map<string, CustomFieldData[][]>
    // Map<catId, List<fieldData>> store template for each category, used for all tickets
    priceCategories: Map<string, CustomFieldData[]>
}
