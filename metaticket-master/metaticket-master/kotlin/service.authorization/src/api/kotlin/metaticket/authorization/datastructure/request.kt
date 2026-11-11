package metaticket.authorization.datastructure


/**
 *
 * @author dongcatchay
 */

data class EnforcerRequest(
    val sub: String,
    val act: String,
    val obj: String,
)

data class LoadRolesOfUserRequest(
    val user: String
)

data class LoadUsersOfRoleRequest(
    val role: String
)

data class OnNewUserRequest(
    val user: String
)

data class SaveRolesOfUserRequest(
    val user: String,
    val oldRoles: List<String>,
    val newRoles: List<String>
)

data class RemoveRolesOfUserRequest(
    val user: String,
    val roles: List<String>
)

data class AddRolesToUserRequest(
    val user: String,
    val roles: List<String>
)

data class LoadRolesOfResourceRequest(
    val resource: String
)

data class LoadResourcesOfRoleRequest(
    val role: String
)

data class SaveRolesOfResourceRequest(
    val resource: String,
    val oldRoles: List<String>,
    val newRoles: List<String>
)

data class AddRolesToResourceRequest(
    val resource: String,
    val roles: List<String>
)

data class RemoveRolesOffResourceRequest(
    val resource: String,
    val roles: List<String>
)
