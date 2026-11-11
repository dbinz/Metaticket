import {VDocLayer} from "@viclass/editor.core";
import {PolicyDescDocument} from "./model";
import {PlanDoc} from "@metaticket/editor.common";

export class PolicydescDoc extends PlanDoc {

    constructor(public override globalId: string, public override id: number, public override doc : PolicyDescDocument) {
        super(globalId, id, doc)
    }
}

export class PolicyDescLayer implements VDocLayer {
    zindex: number = 1
    constructor(public id : number, public docId: number) {}
}
