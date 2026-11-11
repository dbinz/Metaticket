package metaticket.authorization.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.authorization.datastructure.*
import metaticket.common.logger.Logging
import org.casbin.jcasbin.main.Enforcer
import org.casbin.jcasbin.persist.file_adapter.FileAdapter
import org.casbin.jcasbin.util.BuiltInFunctions


/**
 *
 * @author dongcatchay
 */
class AuthorizationController(
    private val enforce: Enforcer,
    private val env : ApplicationEnvironment,
): Logging {

    suspend fun initPolicyFromFile(call: ApplicationCall) {
        val adapter = FileAdapter("conf/policy.csv")
        val e = Enforcer("conf/rbac_model.conf", adapter)
        e.addNamedMatchingFunc("g3", "KeyMatch2") { key1, key2 ->
            return@addNamedMatchingFunc BuiltInFunctions.keyMatch2(key1, key2)
        }
        logger.info("policies {}", e.model)
        enforce.model = e.model

        logger.info("load policies {}", enforce.policy)

        //enforce.savePolicy()

        call.respond(HttpStatusCode.OK)
    }

    suspend fun enforcer(call: ApplicationCall) {
        val req = call.receive<EnforcerRequest>()
        val allow = enforce.enforce(req.sub, req.act, req.obj)
        if (allow) call.respond(HttpStatusCode.OK, "allow")
        else call.respond(HttpStatusCode.Unauthorized, "not allow")
    }

    suspend fun setUserDefaultRole(call: ApplicationCall) {
        val req = call.receive<OnNewUserRequest>()
        val user = req.user

        val defaultUserRole = env.config.property("casbin.defaultUserRole").getString()

        enforce.addNamedGroupingPolicy("g", listOf(user, defaultUserRole))

        //enforce.savePolicy()

        logger.info("user {} has default role {}", user, defaultUserRole)
        call.respond(HttpStatusCode.OK)
    }

    suspend fun loadRolesOfUser(call: ApplicationCall) {
        val req = call.receive<LoadRolesOfUserRequest>()
        val user = req.user
        val roles = enforce.getNamedRoleManager("g").getRoles(user)
        call.respond(HttpStatusCode.OK, roles)
    }

    suspend fun loadUsersOfRole(call: ApplicationCall) {
        val req = call.receive<LoadUsersOfRoleRequest>()
        val role = req.role
        val users = enforce.getNamedRoleManager("g").getUsers(role)
        call.respond(HttpStatusCode.OK, users)
    }

    suspend fun saveRolesOfUser(call: ApplicationCall) {
        val req = call.receive<SaveRolesOfUserRequest>()
        val user = req.user
        val oldRoles = req.oldRoles
        val newRoles = req.newRoles

        val oldUserRoles = enforce.getNamedRoleManager("g").getRoles(user)
        logger.info("user {} has old roles {}", user, oldUserRoles)

        if (oldUserRoles.isNotEmpty() && oldRoles != oldUserRoles) {
            logger.info("user {}, request old roles {}", user, oldRoles)
            logger.error("user {}, old roles from request and server are not the same", user)
            return call.respond(HttpStatusCode.BadRequest, "Need to refresh list roles")
        }

        enforce.updateNamedGroupingPolicy("g", oldRoles, newRoles)

        //enforce.savePolicy()

        logger.info("user {} saved new roles {}", user, newRoles)
        call.respond(HttpStatusCode.OK)
    }

    suspend fun addRolesToUser(call: ApplicationCall) {
        val req = call.receive<AddRolesToUserRequest>()
        val user = req.user
        val roles = req.roles

        enforce.addNamedGroupingPolicies("g", roles.map { listOf(user, it) })

        //enforce.savePolicy()

        logger.info("user {}, added role {}", user, roles)
        call.respond(HttpStatusCode.OK)
    }

    suspend fun removeRolesOfUser(call: ApplicationCall) {
        val req = call.receive<RemoveRolesOfUserRequest>()
        val user = req.user
        val roles = req.roles
        enforce.removeNamedGroupingPolicies("g", roles.map { listOf(user, it) })

        //enforce.savePolicy()

        logger.info("user {}, removed role {}", user, roles)
        call.respond(HttpStatusCode.OK)
    }

    suspend fun loadRolesOfResource(call: ApplicationCall) {
        val req = call.receive<LoadRolesOfResourceRequest>()
        val resource = req.resource
        val roles = enforce.getNamedRoleManager("g3").getRoles(resource)
        call.respond(HttpStatusCode.OK, roles)
    }

    suspend fun loadResourcesOfRole(call: ApplicationCall) {
        val req = call.receive<LoadResourcesOfRoleRequest>()
        val role = req.role
        val resource = enforce.getNamedRoleManager("g3").getUsers(role)
        call.respond(HttpStatusCode.OK, resource)
    }

    suspend fun saveRolesOfResource(call: ApplicationCall) {
        val req = call.receive<SaveRolesOfResourceRequest>()
        val resource = req.resource
        val oldRoles = req.oldRoles
        val newRoles = req.newRoles

        val oldResourceRoles = enforce.getNamedRoleManager("g").getRoles(resource)
        logger.info("resource {} has old roles {}", resource, oldResourceRoles)

        if (oldResourceRoles.isNotEmpty() && oldRoles != oldResourceRoles) {
            logger.info("resource {}, request old roles {}", resource, oldRoles)
            logger.error("resource {}, old roles from request and server are not the same", resource)
            return call.respond(HttpStatusCode.BadRequest, "Need to refresh list roles")
        }

        enforce.updateNamedGroupingPolicy("g3", oldRoles, newRoles)

        //enforce.savePolicy()

        logger.info("resource {} saved new roles {}", resource, newRoles)
        call.respond(HttpStatusCode.OK)
    }

    suspend fun addRolesToResource(call: ApplicationCall) {
        val req = call.receive<AddRolesToResourceRequest>()
        val resource = req.resource
        val roles = req.roles

        enforce.addNamedGroupingPolicies("g3", roles.map { listOf(resource, it) })

        //enforce.savePolicy()

        logger.info("resource {}, added role {}", resource, roles)
        call.respond(HttpStatusCode.OK)
    }

    suspend fun removeRolesOfResource(call: ApplicationCall) {
        val req = call.receive<RemoveRolesOffResourceRequest>()
        val resource = req.resource
        val roles = req.roles
        enforce.removeNamedGroupingPolicies("g3", roles.map { listOf(resource, it) })

        //enforce.savePolicy()

        logger.info("resource {}, removed role {}", resource, roles)
        call.respond(HttpStatusCode.OK)
    }
}
