package metaticket.order.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.order.calculate.promotion.meta_promo.MetaPromoConfiguration
import metaticket.order.calculate.promotion.meta_promo.MetaPromoPromotionCodeConfig
import metaticket.order.database.service.PromotionCodeQuantityDBService
import metaticket.order.database.service.PromotionComboQuantityDBService
import metaticket.order.database.service.PromotionDBService
import metaticket.order.pojo.db.Promotion
import metaticket.order.pojo.db.PromotionCodeQuantity
import metaticket.order.pojo.db.PromotionComboQuantity
import metaticket.order.pojo.request.CreatePromotionRequest
import org.bson.types.ObjectId
import java.io.File
import java.util.*

class PromotionController(
    private val promotionDB: PromotionDBService,
    private val promotionCodeQuantityDBService: PromotionCodeQuantityDBService,
    private val jsonFactory: JsonNodeFactory,
    private val jsonMapper: ObjectMapper,
) : Logging {

    suspend fun importFile(call: ApplicationCall) {
        promotionCodeQuantityDBService.updatePromotionCodeQuantity()
//        val result = promotionCodeQuantityDBService.createPromotionCodeQuantity(
//                PromotionCodeQuantity(
//                    showId = "6595773df323909fe23b954e",
//                    sessionId = "65957746f323909fe23b954f",
//                    originalQuantity = 1,
//                    quantity = 1,
//                    code = it,
//                    lastModified = Date()
//                )
//            )
//        val string = "BT332415,BT215112,BT352253,BT424524,BT351352,BT111555,BT524123,BT334534,BT555554,BT123524,BT341325,BT255313,BT515321,BT332121,BT515554,BT125543,BT552434,BT415523,BT135511,BT244345,BT234324,BT135115,BT523355,BT134454,BT324124,BT534423,BT333354,BT543455,BT112533,BT431344,BT121434,BT112441,BT324444,BT243345,BT155311,BT124432,BT534233,BT114425,BT221232,BT254311,BT333245,BT212324,BT144132,BT144412,BT311444,BT555544,BT332224,BT153223,BT414454,BT545424,BT153545,BT414533,BT152143,BT321523,BT352131,BT541131,BT143234,BT313415,BT112411,BT211132,BT313254,BT253435,BT224213,BT555214,BT235414,BT341333,BT232453,BT541221,BT335152,BT415354,BT324433,BT134335,BT132422,BT243153,BT233245,BT544112,BT434355,BT451113,BT242314,BT321333,BT214543,BT141124,BT154544,BT222212,BT252134,BT245535,BT211523,BT123511,BT552331,BT245224,BT455521,BT532553,BT551451,BT322512,BT353243,BT113342,BT113433,BT132244,BT132151,BT255213,BT324452,BT333455,BT422413,BT321243,BT212524,BT411154,BT454334,BT424522,BT213123,BT252512,BT513212,BT142251,BT433541,BT411552,BT412113,BT513241,BT255323,BT434414,BT223155,BT312513,BT452415,BT535221,BT311533,BT351515,BT255115,BT152514,BT144433,BT421341,BT245253,BT541323,BT551422,BT553131,BT415154,BT344112,BT231312,BT245414,BT323134,BT525424,BT532252,BT251532,BT423552,BT425525,BT544352,BT143132,BT311432,BT215125,BT154434,BT411155,BT542451,BT554425,BT222231,BT345451,BT553121,BT451133,BT434123,BT323254,BT514521,BT355542,BT413421,BT424454,BT222355,BT432554,BT544434,BT355244,BT253212,BT141151,BT341553,BT523522,BT433525,BT244111,BT252355,BT533131,BT322331,BT124544,BT344424,BT341523,BT555523,BT541524,BT255443,BT114151,BT235213,BT453512,BT231411,BT352454,BT213542,BT551511,BT351223,BT523254,BT151445,BT511524,BT535143,BT122334,BT412543,BT522535,BT234213,BT141223,BT144221,BT342251,BT423254,BT155532,BT115324,BT432514,BT534214,BT313354,BT335151,BT534453,BT523122,BT151554,BT124142,BT434335,BT525515,BT542211,BT531243,BT433254,BT343443,BT455141,BT141132,BT425232,BT143431,BT323311,BT413222,BT342153,BT413111,BT235215,BT554553,BT533355,BT441311,BT451524,BT143552,BT211225,BT545131,BT453133,BT334222,BT533325,BT551115,BT442111,BT315534,BT512111,BT513342,BT255114,BT223231,BT424444,BT221443,BT542213,BT113134,BT111233,BT254542,BT544211,BT214555,BT422124,BT244123,BT245355,BT244524,BT454224,BT354144,BT334433,BT351121,BT554331,BT423235,BT514312,BT522431,BT234331,BT212413,BT551413,BT314452,BT214143,BT413131,BT122312,BT243113,BT445524,BT255253,BT141313,BT344445,BT351431,BT321411,BT253351,BT343424,BT424141,BT154255,BT313244,BT254145,BT114135,BT332211,BT531534,BT451452,BT153144,BT431213,BT345114,BT415544,BT515332,BT552533,BT435223,BT452122,BT522244,BT322113,BT131131,BT344411,BT211223,BT341215,BT324453,BT143534,BT343352,BT411353,BT313324,BT443253,BT331344,BT434334,BT431333,BT214524,BT513345,BT352214,BT153212,BT341351,BT223124,BT113314,BT255433,BT535411,BT335545,BT422342,BT431541,BT235134,BT444345,BT134145,BT545224,BT332122,BT223314,BT231134,BT425532,BT225353,BT221523,BT154124,BT135444,BT442523,BT521542,BT445423,BT133511,BT241225,BT413351,BT334233,BT535352,BT553455,BT254512,BT311122,BT522541,BT534451,BT435155,BT151541,BT115213,BT411224,BT451345,BT313132,BT334512,BT311351,BT314541,BT221414,BT323411,BT233144,BT243231,BT532541,BT311314,BT313534,BT513153,BT555235,BT422441,BT334542,BT415525,BT141154,BT431312,BT541325,BT531321,BT124152,BT134131,BT215435,BT533423,BT522412,BT353221,BT543114,BT344323,BT441131,BT412352,BT513441,BT552211,BT344311,BT343553,BT135124,BT343322,BT123222,BT331412,BT415131,BT231221,BT334114,BT524112,BT243131,BT512212,BT253523,BT125232,BT325552,BT311341,BT244142,BT442452,BT423454,BT342343,BT245232,BT123213,BT232151,BT454412,BT141432,BT133132,BT245445,BT323552,BT143315,BT221114,BT114211,BT345523,BT441434,BT324115,BT314114,BT531543,BT514452,BT311112,BT314533,BT115251,BT532414,BT144551,BT143154,BT242551,BT342333,BT134544,BT215532,BT433115,BT415351,BT214532,BT533331,BT123115,BT432443,BT134111,BT433131,BT131521,BT413544,BT525332,BT543121,BT443115,BT125341,BT312333,BT521253,BT425214,BT214525,BT125135,BT342124,BT154451,BT532425,BT211322,BT423215,BT543125,BT543312,BT451151,BT454343,BT152251,BT311552,BT122342,BT131224,BT252353,BT255123,BT334115,BT343113,BT342253,BT351253,BT313444,BT411543,BT233534,BT342241,BT435134,BT423111,BT251345,BT421342,BT525455,BT123324,BT355232,BT222313,BT543221,BT412435,BT252131,BT442345,BT335411,BT141421,BT545252,BT144451,BT135443,BT311115,BT422132,BT223434,BT411445,BT545444,BT343242,BT452233,BT142114,BT311541,BT423435,BT444445,BT352412".split(",")
//        val promotionCodes: MutableList<MetaPromoPromotionCodeConfig> = mutableListOf()
//        string.map {
//            val result = promotionCodeQuantityDBService.createPromotionCodeQuantity(
//                PromotionCodeQuantity(
//                    showId = "6595773df323909fe23b954e",
//                    sessionId = "65957746f323909fe23b954f",
//                    originalQuantity = 1,
//                    quantity = 1,
//                    code = it,
//                    lastModified = Date()
//                )
//            )
//            promotionCodes.add(MetaPromoPromotionCodeConfig(
//                discountPercent = 10,
//                discountPrice = null,
//                needCheckQuantity = true,
//                originalQuantity = 1,
//                code = it,
//                name = it,
//            ))
//
//
//            logger.debug(result)
//        }
//        val config = MetaPromoConfiguration(
//            discountByDates = listOf(),
//            discountByTicketAmounts = listOf(),
//            discountBySingleCatAmounts = listOf(),
//            promotionCodes = promotionCodes,
//        )
//
//        promotionDB.createPromotion(
//            Promotion(
//                showId = "6595773df323909fe23b954e",
//                sessionId = "65957746f323909fe23b954f",
//                pluginName = "metaticket.order.calculate.promotion.meta_promo.MetaPromoPlugin",
//                configuration = jsonMapper.writeValueAsString(config) ?: ""
//            )
//        )
        call.respond(HttpStatusCode.OK)
    }
    suspend fun createPromotion(call: ApplicationCall) {
        val reqData = call.receive<CreatePromotionRequest>()

        val promotionDoc = Promotion(
            showId = reqData.showId,
            sessionId = reqData.sessionId,
            pluginName = reqData.pluginName,
            configuration = reqData.configuration
        )

        promotionDB.createPromotion(promotionDoc)

        call.respond(promotionDoc)
    }

    suspend fun fetchPromotionsByShowIdAndSessionId(call: ApplicationCall) {
        val showId = call.parameters["showId"]
            ?: return call.respond(HttpStatusCode.BadRequest,"Missing or malformed showId")
        if (!ObjectId.isValid(showId))
            return call.respond(HttpStatusCode.BadRequest,"Invalid show showId")

        val sessionId = call.parameters["sessionId"]
            ?: return call.respond(HttpStatusCode.BadRequest,"Missing or malformed sessionId")

        if (!ObjectId.isValid(sessionId))
            return call.respond(HttpStatusCode.BadRequest,"Invalid show sessionId")

        val promotions = promotionDB.loadPromotions(showId, sessionId)

        call.respond(promotions)
    }
}