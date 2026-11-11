package metaticket.order.datastructure.validation

data class DateTimeConstraints constructor(val dateOnly: Boolean?, val minDate: Long?, val maxDate: Long?, val require: Boolean?)
