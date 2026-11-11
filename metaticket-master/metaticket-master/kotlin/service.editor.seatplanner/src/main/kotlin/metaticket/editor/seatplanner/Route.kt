package metaticket.editor.seatplanner

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import metaticket.editor.seatplanner.controller.DocumentController
import metaticket.editor.seatplanner.controller.ShapeController

fun Route.documentRoutes(c : DocumentController) {
    get("/private/editor/seatplanner/createDoc") {
        c.createDoc(call)
    }

    get("/private/editor/seatplanner/deleteDoc") {
        c.deleteDoc(call)
    }

    post("/private/editor/seatplanner/cmd") {
        c.processCmd(call)
    }

    post("/private/editor/seatplanner/saveDocStyle") {
        c.saveDocStyle(call)
    }

    post("/private/editor/seatplanner/saveDocDef") {
        c.saveDocDef(call)
    }

    post("/private/editor/seatplanner/saveDocName") {
        c.saveDocName(call)
    }

     post("/private/editor/seatplanner/saveCategories") {
        c.saveCategories(call)
    }

    get("/private/editor/seatplanner/document") {
        c.loadDoc(call)
    }
    get("/editor/seatplanner/document") {
        c.loadDoc(call)
    }

    get("/private/editor/seatplanner/document/category") {
        c.loadDocCategory(call)
    }

    get("/private/editor/seatplanner/documents/category") {
        c.loadAllDocCategory(call)
    }

    post("/private/editor/seatplanner/uploadImage") {
        c.uploadImage(call)
    }

    post("/private/editor/seatplanner/updateBaseImgUrl") {
        c.updateBaseImgUrl(call)
    }
}

fun Route.shapeRoutes(c : ShapeController) {

}
