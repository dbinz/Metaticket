/*
 * Public API Surface of policy description
 */
import {DocumentEditor, EditorConfig, EditorCoordinator} from "@viclass/editor.core"
import {PolicydescEditor} from "./lib/policydesc.editor";
import {PolicyDescConfig} from "./lib/model";

export * from './lib/policydesc.editor'
export * from "./lib/policydesc.doc.ctrl"
export * from "./lib/policydesc.doc"
export * from "./lib/model"

export function factory(conf: EditorConfig, coordinator: EditorCoordinator): DocumentEditor {
    return new PolicydescEditor(conf as PolicyDescConfig, coordinator)
}
