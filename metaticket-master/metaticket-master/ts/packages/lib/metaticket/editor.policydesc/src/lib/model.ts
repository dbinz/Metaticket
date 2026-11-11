import {DOMElementLayerCtrl, EditorConfig} from "@viclass/editor.core";
import {PlanDocument} from "@metaticket/editor.common";

export type PolicyDescLayerCtrl = DOMElementLayerCtrl

export interface PolicyDescDocument extends PlanDocument{
    content: string
}

export interface PolicyDescConfig extends EditorConfig {}
