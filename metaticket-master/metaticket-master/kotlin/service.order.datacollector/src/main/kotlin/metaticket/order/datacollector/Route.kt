package metaticket.order.datacollector

import io.ktor.server.routing.*
import metaticket.order.datacollector.controller.OrderDataCollectorController

fun Route.documentRoutes(
    ctl: OrderDataCollectorController,
) {
    post("/odc/link/generate") { ctl.generateLink(call) }
    get("/odc/template/load/{id}") { ctl.loadOrderDataCollectorTemplate(call) }
    post("/odc/data/validate") {  }
    get("/odc/data/load/{id}") { ctl.loadOrderDataCollected(call) }
    post("/odc/data/save") { ctl.saveOrderDataCollected(call) }
    post("/odc/data/is-valid/link") { ctl.checkOrderDataCollectedIsValid(call) }
}
