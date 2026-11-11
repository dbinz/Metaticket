import {SeatPlanDocument} from "./seatplanner.model"
import {PlanDoc, PLanLayer} from "@metaticket/editor.common"

export class SPDoc extends PlanDoc {

    constructor(public override globalId: string, public override id: number, public override doc: SeatPlanDocument) {
        super(globalId, id, doc);
    }

}

export class SPLayer extends PLanLayer {}
