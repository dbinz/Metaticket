package metaticket.authorization

import io.ktor.server.application.*
import io.ktor.server.routing.*
import metaticket.authorization.controller.AuthorizationController

fun Route.documentRoutes(c : AuthorizationController) {

    post("/private/authorization/policy/init") { c.initPolicyFromFile(call) }

    post("/internal/authorization/enforcer") { c.enforcer(call) }

    // === user role management ===
    post("/internal/authorization/user/onnew") { c.setUserDefaultRole(call) }
    post("/private/authorization/user/roles/save") { c.saveRolesOfUser(call) }
    post("/private/authorization/user/roles/fetch") { c.loadRolesOfUser(call) }
    post("/private/authorization/user/roles/add") { c.addRolesToUser(call) }
    post("/private/authorization/user/roles/remove") { c.removeRolesOfUser(call) }
    post("/private/authorization/role/users/fetch") { c.loadUsersOfRole(call) }


    // === resource role management ===
    post("/private/authorization/resource/roles/save") { c.saveRolesOfResource(call) }
    post("/private/authorization/resource/roles/fetch") { c.loadRolesOfResource(call) }
    post("/private/authorization/resource/roles/add") { c.addRolesToResource(call) }
    post("/private/authorization/resource/roles/remove") { c.removeRolesOfResource(call) }
    post("/private/authorization/role/resources/fetch") { c.loadResourcesOfRole(call) }
}
