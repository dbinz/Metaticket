package metaticket.editor.policydesc

import io.ktor.server.routing.*
import metaticket.editor.policydesc.controller.DocumentController

fun Route.documentRoutes(c : DocumentController) {
    get("/private/editor/policydesc/document") {
        c.loadDoc(call)
    }
    get("/editor/policydesc/document") {
        c.loadDoc(call)
    }

    get("/private/editor/policydesc/document/category") {
        c.loadDocCategory(call)
    }

    get("/private/editor/policydesc/documents/category") {
        c.loadAllDocCategory(call)
    }
}
