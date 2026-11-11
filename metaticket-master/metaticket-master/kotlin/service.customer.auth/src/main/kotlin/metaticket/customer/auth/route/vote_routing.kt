package metaticket.customer.auth.route

import io.ktor.server.routing.*
import metaticket.customer.auth.controller.VoteCtrl

fun Route.voteRoutes(
    voteCtrl: VoteCtrl
) {
    // Đi qua /api/customer-auth — nginx auth_request đã verify login và set X-Customer-User-Id.
    post("/customer-auth/vote") {
        voteCtrl.vote(call)
    }

    get("/customer-auth/vote/me") {
        voteCtrl.getMyVote(call)
    }

    // Đi qua /api/public/customer-auth — không yêu cầu login.
    get("/public/customer-auth/vote/options") {
        voteCtrl.getOptions(call)
    }

    // Tự bảo vệ bằng header X-Admin-Secret (không dùng JWT khách hàng).
    get("/public/customer-auth/vote-results") {
        voteCtrl.getResults(call)
    }
}
