import {VDoc} from "@viclass/editor.core";
import {PlanDocument} from "./common.model";
import {PLanLayer} from "./common.layer";

export abstract class PlanDoc implements VDoc {

    constructor(public globalId: string, public id: number, public doc: PlanDocument) {
    }

    get version(): number {
        return this.doc.version
    }

    set version(value: number) {
        this.doc.version = value
    }

    addLayer(layer: PLanLayer): any {}
}
