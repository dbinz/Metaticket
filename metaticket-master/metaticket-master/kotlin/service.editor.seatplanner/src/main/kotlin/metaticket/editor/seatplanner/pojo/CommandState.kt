package metaticket.editor.seatplanner.pojo

data class SaveItemsCmdState(
    val added : List<PlanItem>,
    val updated : List<PlanItem>,
    val removed : List<PlanItem>
)
