package metaticket.order.datastructure.validation

data class ListOption constructor(val value: String, val name: String)

data class ListConstraints constructor(val require: Boolean, val options: List<ListOption>)