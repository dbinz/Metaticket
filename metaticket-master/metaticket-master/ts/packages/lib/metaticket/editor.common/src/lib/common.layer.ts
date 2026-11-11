import {BoundaryRectangle, VDocLayer} from "@viclass/editor.core";

export abstract class PLanLayer implements VDocLayer {
    zindex: number = 1

    constructor(public id : number, public docId: number, public boundary? : BoundaryRectangle) {}
}
