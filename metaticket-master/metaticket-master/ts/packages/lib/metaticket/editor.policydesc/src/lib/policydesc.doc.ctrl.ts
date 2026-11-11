import {ViewportManager} from "@viclass/editor.core";
import {PolicydescEditor} from "./policydesc.editor";
import {PolicydescDoc} from "./policydesc.doc";
import {PolicyDescLayerCtrl} from "./model";
import {markerClass, PlanDocCtrl, PlanItem} from "@metaticket/editor.common";


export class PolicydescDocCtrl extends PlanDocCtrl {

    layer ?: PolicyDescLayerCtrl

    private els: Map<string, HTMLElement> = new Map()

    constructor(public override editor: PolicydescEditor, public override state: PolicydescDoc, public override viewport: ViewportManager) {
        super(state, editor)
    }

    override setDisabled(disabled: boolean, itemIds: string[]) {
        itemIds.forEach(id => {
            let el = this.els.get(id)
            if (!el) return
            if (disabled) el.setAttribute('disabled', '')
            else el.removeAttribute('disabled')
        })
    }

    override addLayer(layer: PolicyDescLayerCtrl): void {
        super.addLayer(layer)
        this.layer = layer

        layer.attachRenderer(() => this.render())
    }

    private async render() {
        // only render one, because after rendering elements are on the layer and the browser automatically render when anything change
        this.layer?.removeRenderer()

        let shadowRoot = this.layer!.domEl.attachShadow({mode: "open"})
        let rootE = document.createElement("div") as HTMLDivElement
        let style = document.createElement("style")

        rootE.innerHTML = this.state.doc.content
        style.textContent = this.state.doc.styleSheet.trim()

        shadowRoot.appendChild(style)
        shadowRoot.appendChild(rootE)

        let els = shadowRoot.querySelectorAll(`.${markerClass}.item`)
        for (let i = 0; i < els.length; i++) {
            let el = els.item(i) as HTMLElement
            let id = el.dataset["iId"]!
            this.els.set(id, el)
            this.attactListener(el)
        }
    }

    private attactListener(el: HTMLElement) {
        el.addEventListener( "click", e => {
            let target = e.currentTarget as HTMLElement
            let id = target.dataset["iId"]!
            let obj = this.state.doc.items.filter(i => i.id == parseInt(id))[0]
            if (!obj) return

            obj = structuredClone(obj)

            this.viewEE.emit({
                eventType: "item-selected",
                source: this,
                state: {
                    items: [obj],
                    triggerItem: obj,
                    triggerEl: target
                }
            })
        })
    }
}
