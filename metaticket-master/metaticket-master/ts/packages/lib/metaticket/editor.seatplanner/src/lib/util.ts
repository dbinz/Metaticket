import {PlanItem} from "@metaticket/editor.common"
import {Position, xmlns} from "@viclass/editor.core"

export function seatPlannerDocReg(vpId: string) {
    return `spe/spDoc/${vpId}`
}

export function seatPlannerLayerReg(vpId: string, docId: number) {
    return `spe/spLayer/${vpId}/${docId}`
}

export function seatPlannerObjectReg(vpId: string, docId: number) {
    return `spe/spObj/${vpId}/${docId}`
}

export const markerClass = "_s_c_"

export function createControlElementForItem(item : PlanItem, tag : string, additionalClasses : string[]) : SVGElement {
    return createControlElementForItemData(item.id+"", item.type, tag, additionalClasses)
}

export function createControlElementForItemData(itemId : string, itemType : string , tag : string, additionalClasses : string[]) : SVGElement {
    let el = document.createElementNS(xmlns, tag)
    el.classList.add(markerClass, ...additionalClasses)
    el.dataset["iId"] = itemId
    el.dataset["iType"] = itemType
    return el
}

export function equidistancePointArrayFromTwoEnds(start : Position, end : Position, numPoint : number) : Position[] {
    let fraction = 1/(numPoint+1)
    let p : Position[] = [start]

    for (let i = 1; i <= numPoint; i++) {
        let inbetween = { x : start.x + (end.x-start.x) * i * fraction, y : start.y + (end.y-start.y) * i * fraction }
        p.push(inbetween)
    }

    p.push(end)

    return p
}