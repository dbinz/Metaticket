/*
 * Public API Surface of seatplanner
 */
import { DocumentEditor, EditorConfig, EditorCoordinator } from "@viclass/editor.core"
import { SeatPlannerConfig, SeatPlannerEditor } from "./lib/seatplanner.editor"

export * from "./lib/seatplanner.editor"

export * from "./lib/tools/seatplanner.toolbar"
export * from "./lib/tools/load.tool"
export * from "./lib/tools/segment.tool"
export * from "./lib/tools/seat.tool"
export * from "./lib/tools/seatzone.tool"

export * from "./lib/tools/tool.model"
export * from "./lib/seatplanner.doc.ctrl"
export * from "./lib/seatplanner.doc"
export * from "./lib/seatplanner.model"

export function factory(conf: EditorConfig, coordinator: EditorCoordinator): DocumentEditor {
    return new SeatPlannerEditor(conf as SeatPlannerConfig, coordinator)
}