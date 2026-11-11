package metaticket.order.datastructure.validation

data class StringConstraints constructor(val regex: String?, val minLength: Int?, val maxLength: Int?, val require: Boolean?)