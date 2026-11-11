import {
    DefaultToolBar,
    KeyboardEventListener,
    MouseEventListener,
    NativeEventTarget,
    ViewportManager
} from "@viclass/editor.core";
import {PolicydescEditor} from "../policydesc.editor";

export class PolicydescToolbar extends DefaultToolBar<any, any> {
    override keyboardHandler: KeyboardEventListener<NativeEventTarget<any>> | undefined
    override mouseHandler: MouseEventListener<NativeEventTarget<any>> | undefined

    constructor(public editor: PolicydescEditor) {
        super()
    }

    override attachViewport(viewport: ViewportManager): void {
        super.attachViewport(viewport)

        for (let t of this.tools.values()) {
            t.onViewportAttached(viewport)
        }

    }

    override detachViewport(viewport: ViewportManager): void {
        super.detachViewport(viewport)

        for (let t of this.tools.values()) {
            t.onViewportDetached(viewport)
        }
    }
}
