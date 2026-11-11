import { PlanItem } from "@metaticket/editor.common"

export interface UIBehaviorCtrl {
    disable(item : PlanItem[]) : void
    enable(item : PlanItem[]) : void
    highlight(item : PlanItem[]) : void
    removeHightlight(item : PlanItem[]) : void
}
