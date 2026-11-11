import {DefaultEventEmitter, DefaultVDocCtrl, VEventData} from "@viclass/editor.core";
import {PlanViewEventState, PlanViewEventType} from "./common.model";

export abstract class PlanDocCtrl extends DefaultVDocCtrl {
    viewEE = new DefaultEventEmitter<VEventData<PlanViewEventType, PlanDocCtrl, PlanViewEventState>>()

    setDisabled(disabled : boolean, itemIds : string[]) {}
    clearSelection(itemIds? : string[]) {}
}
