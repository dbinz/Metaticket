package metaticket.customer.auth.response

data class CaptchaSettingResponse constructor(
    val enable: Boolean,
    val captchaGateway: String, // hcaptcha | recaptcha
    val siteKey: String,
)

data class VoteOptionResponse(
    val id: String,
    val label: String,
    val thumbnailUrl: String?,
    val description: String?
)
data class VoteOptionsResponse(
    val multi: Boolean,
    val maxChoicePerVote: Int,
    val totalVotes: Long,
    val options: List<VoteOptionResponse>,
)
data class VoteMeResponse(val hasVoted: Boolean, val optionIds: List<String>? = null)
data class VoteSubmitResponse(val message: String, val optionIds: List<String>)
data class VoteResultItem(val optionId: String, val label: String, val count: Long, val percent: Double)
data class VoteResultsResponse(val total: Long, val multi: Boolean, val results: List<VoteResultItem>)