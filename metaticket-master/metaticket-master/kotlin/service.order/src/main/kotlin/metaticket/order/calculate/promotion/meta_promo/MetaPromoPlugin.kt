package metaticket.order.calculate.promotion.meta_promo

import metaticket.common.logger.Logging
import metaticket.order.calculate.promotion.*
import metaticket.order.pojo.db.Promotion
import metaticket.order.pojo.db.PromotionCodeQuantity
import metaticket.order.pojo.db.PromotionComboQuantity
import org.bson.types.ObjectId
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.*

class MetaPromoPlugin constructor(): CalculatePromotion, Logging {

    override suspend fun calculatePromotionDiscount(input: CalculatePromotionInput): PromotionData? {
        val config = input.jsonMapper?.readValue(input.configuration, MetaPromoConfiguration::class.java) ?: return null
        val promotionDetails = mutableListOf<PromotionDetailData>()
        val promotionData = PromotionData("", 0L, 0L, 0L, promotionDetails)
        val currentDate = Date()

        var priceOriginal = 0L
        var priceDiscountByDateDiscount = 0L
        var totalTicket = 0
        val listTicketPromotionData = mutableListOf<TicketPromotionData>()
        val listTicketPromotionDataClone = mutableListOf<TicketPromotionData>()
        input.tentativeTickets?.let {
            for(ticketRes in input.tentativeTickets) {
                for(ticket in input.tickets) {
                    if(ticket.id == ticketRes.ticketId) {
                        listTicketPromotionData.add(TicketPromotionData(
                            sid = ticket.sid,
                            ticketId = ticketRes.ticketId,
                            priceCat = ticket.priceCat,
                            basePrice = ticket.basePrice,
                            quantity = ticketRes.quantity
                        ))
                        listTicketPromotionDataClone.add(TicketPromotionData(
                            sid = ticket.sid,
                            ticketId = ticketRes.ticketId,
                            priceCat = ticket.priceCat,
                            basePrice = ticket.basePrice,
                            quantity = ticketRes.quantity
                        ))
                    }
                }
            }
        }
        input.ticketsFromReservation?.let {
            for(ticketRes in it) {
                for(ticket in input.tickets) {
                    if(ticket.id == ticketRes.ticketId) {
                        listTicketPromotionData.add(TicketPromotionData(
                            sid = ticket.sid,
                            ticketId = ticketRes.ticketId,
                            priceCat = ticket.priceCat,
                            basePrice = ticket.basePrice,
                            quantity = ticketRes.quantity
                        ))
                        listTicketPromotionDataClone.add(TicketPromotionData(
                            sid = ticket.sid,
                            ticketId = ticketRes.ticketId,
                            priceCat = ticket.priceCat,
                            basePrice = ticket.basePrice,
                            quantity = ticketRes.quantity
                        ))
                    }
                }
            }
        }
        for(ticketData in listTicketPromotionData) {
            totalTicket += ticketData.quantity
            priceOriginal += ticketData.basePrice * ticketData.quantity
        }
        config.discountByDates?.let { discountByDates ->
            for(discountByDate in discountByDates) {
//                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
//                val date = dateFormat.parse(discountByDate.date)
                val date = Date(discountByDate.date)
                if(date.after(currentDate)) {
                    if(discountByDate.discountPercent != null) {
                        priceDiscountByDateDiscount += discountByDate.discountPercent * priceOriginal / 100
                    } else if(discountByDate.discountPrice != null) {
                        priceDiscountByDateDiscount += totalTicket * discountByDate.discountPrice
                    }
                }
            }
        }
        promotionData.priceOriginal = priceOriginal
        promotionData.priceTotal = priceOriginal
        if(priceDiscountByDateDiscount > 0) {
            promotionData.priceTotal = promotionData.priceOriginal - priceDiscountByDateDiscount
            promotionData.priceDiscount += priceDiscountByDateDiscount
            promotionDetails.add(
                PromotionDetailData(
                    "Early Bird",
                    priceDiscountByDateDiscount,
                    PromotionType.NONE
                )
            )
        }
        config.discountByTicketAmounts?.let {
            loop@for(discountByTicketAmount in it) {
                var promotionComboQuantityDoc: PromotionComboQuantity? = null
                if(discountByTicketAmount.needCheckQuantity == true && discountByTicketAmount.id != null) {
                    promotionComboQuantityDoc = input.promotionComboQuantityDBService.loadPromotionComboQuantity(
                        discountByTicketAmount.id
                    )
                }
                logger.debug("needCheckQuantity " + discountByTicketAmount.needCheckQuantity)
                logger.debug("discountByTicketAmountId " + discountByTicketAmount.id)
                var promotionComboQuantity = 1
                if(discountByTicketAmount.quantityType == QuantityType.Ticket) {
                    promotionComboQuantity = totalTicket
                }
                if(
                        discountByTicketAmount.ticketAmountAbove != null &&
                        totalTicket >= discountByTicketAmount.ticketAmountBelow &&
                        totalTicket <= discountByTicketAmount.ticketAmountAbove
                ) {

                    if(promotionComboQuantityDoc != null && promotionComboQuantityDoc.quantity < promotionComboQuantity) {
                    } else if((promotionComboQuantityDoc != null) || (discountByTicketAmount.needCheckQuantity != true)){
                        var discountTotalTicketAmount = 0L
                        if(discountByTicketAmount.discountPercent != null) {
                            discountTotalTicketAmount = discountByTicketAmount.discountPercent * priceOriginal / 100
                        } else if(discountByTicketAmount.discountPrice != null) {
                            discountTotalTicketAmount = discountByTicketAmount.discountPrice
                        }

                        promotionData.priceDiscount += discountTotalTicketAmount
                        promotionData.priceTotal -= discountTotalTicketAmount
                        promotionDetails.add(
                            PromotionDetailData(
                                "Combo ${discountByTicketAmount.ticketAmountBelow} đến ${discountByTicketAmount.ticketAmountAbove} vé",
                                discountTotalTicketAmount,
                                PromotionType.COMBO,
                                promotionComboQuantityId = promotionComboQuantityDoc?.id,
                                promotionComboQuantity = if(discountByTicketAmount.quantityType == QuantityType.Combo) { 1 } else { totalTicket }
                            )
                        )
                        break@loop
                    }


                } else if(
                    discountByTicketAmount.ticketAmountAbove == null &&
                    totalTicket >= discountByTicketAmount.ticketAmountBelow
                ) {

                    if(promotionComboQuantityDoc != null && promotionComboQuantityDoc.quantity < promotionComboQuantity) {
                    } else if((promotionComboQuantityDoc != null) || (discountByTicketAmount.needCheckQuantity != true)){
                        var discountTotalTicketAmount = 0L
                        if(discountByTicketAmount.discountPercent != null) {
                            discountTotalTicketAmount = discountByTicketAmount.discountPercent * priceOriginal / 100
                        } else if(discountByTicketAmount.discountPrice != null) {
                            discountTotalTicketAmount = discountByTicketAmount.discountPrice
                        }
                        promotionData.priceDiscount += discountTotalTicketAmount
                        promotionData.priceTotal -= discountTotalTicketAmount
                        promotionDetails.add(
                            PromotionDetailData(
                                "Combo trên ${discountByTicketAmount.ticketAmountBelow} vé",
                                discountTotalTicketAmount,
                                PromotionType.COMBO,
                                promotionComboQuantityId = promotionComboQuantityDoc?.id,
                                promotionComboQuantity = if(discountByTicketAmount.quantityType == QuantityType.Combo) { 1 } else { totalTicket }
                            )
                        )
                        break@loop
                    }
                }
            }
        }

        config.discountByPriceCategoryAmounts?.let { discountByPriceCategoryAmounts ->

            loop@for(discountByPriceCategoryAmount in discountByPriceCategoryAmounts) {
                if (discountByPriceCategoryAmount.conditions.isNotEmpty()) {
                    var isAllConditionsMatched = true
                    var totalDiscountPrice = 0L
                    var groupTicketName = ""
                    
                    // First pass: Check all conditions
                    for (condition in discountByPriceCategoryAmount.conditions) {
                        var currentTypeQuantity = 0
                        
                        for(ticketPromotionData in listTicketPromotionDataClone) {
                            if(condition.priceCat == ticketPromotionData.priceCat && ticketPromotionData.quantity > 0) {
                                currentTypeQuantity += ticketPromotionData.quantity
                            }
                        }
                        
                        var isConditionMatched = false
                        if (condition.maxQuantity != null) {
                            if (currentTypeQuantity >= condition.minQuantity && currentTypeQuantity <= condition.maxQuantity) {
                                isConditionMatched = true
                            }
                        } else {
                            if (currentTypeQuantity >= condition.minQuantity) {
                                isConditionMatched = true
                            }
                        }
                        
                        if (!isConditionMatched) {
                            isAllConditionsMatched = false
                            break
                        }
                    }
                    
                    if (isAllConditionsMatched) {
                        var basePriceForDiscount = 0L
                        
                        // Second pass: Calculate price and consume tickets
                        for (condition in discountByPriceCategoryAmount.conditions) {
                            var currentTypeQuantity = 0
                            
                            for(ticketPromotionData in listTicketPromotionDataClone) {
                                if(condition.priceCat == ticketPromotionData.priceCat && ticketPromotionData.quantity > 0) {
                                    currentTypeQuantity += ticketPromotionData.quantity
                                    
                                    if(groupTicketName.isEmpty()) {
                                        groupTicketName = ticketPromotionData.sid
                                    } else if (!groupTicketName.contains(ticketPromotionData.sid)) {
                                        groupTicketName += " - ${ticketPromotionData.sid}"
                                    }
                                    
                                    // Track price for MatchedItems discount calculation
                                    if (discountByPriceCategoryAmount.applyDiscountOn == ApplyDiscountOnType.MatchedItems) {
                                        basePriceForDiscount += ticketPromotionData.basePrice * ticketPromotionData.quantity
                                    }

                                    // Mark as consumed
                                    ticketPromotionData.quantity = 0
                                }
                            }
                        }
                        
                        if (discountByPriceCategoryAmount.applyDiscountOn != ApplyDiscountOnType.MatchedItems) {
                             basePriceForDiscount = priceOriginal
                        }
                        
                        if (discountByPriceCategoryAmount.discountPercent != null) {
                            totalDiscountPrice = discountByPriceCategoryAmount.discountPercent * basePriceForDiscount / 100
                        } else if (discountByPriceCategoryAmount.discountPrice != null) {
                            totalDiscountPrice = discountByPriceCategoryAmount.discountPrice
                        }

                        promotionDetails.add(
                            PromotionDetailData(
                                "Combo vé $groupTicketName",
                                totalDiscountPrice,
                                PromotionType.COMBO
                            )
                        )
                        promotionData.priceDiscount += totalDiscountPrice
                        promotionData.priceTotal -= totalDiscountPrice
                    }
                }
            }
        }

        if(config.promotionCodes != null && (input.promotionCode ?: "").isNotEmpty()) {
            loop@for(promotionCode in config.promotionCodes) {
                if(input.promotionCode!!.uppercase(Locale.getDefault()).trim() == promotionCode.code.uppercase(Locale.getDefault()).trim()) {
                    var promotionCodeQuantityId: String? = null
                    if(promotionCode.needCheckQuantity == true) {
                        val promotionCodeQuantityDoc = input.promotionCodeQuantityDBService.loadPromotionCodeQuantity(
                            input.showId, input.sessionId, promotionCode.code.trim()
                        )
                        promotionCodeQuantityId = promotionCodeQuantityDoc?.id
                        if(promotionCodeQuantityDoc == null || promotionCodeQuantityDoc.quantity < 1) {
                            promotionDetails.add(
                                PromotionDetailData(
                                    promotionCode.code.trim(),
                                    0,
                                    PromotionType.COUPON,
                                    error = true,
                                    errorMessage = "Mã đã được sử dụng hết",
                                    promotionCodeQuantityDoc?.id
                                )
                            )
                            break@loop
                        }
                    }
                    var promotionCodeDiscount = 0L
                    if(promotionCode.discountPrice != null) {
                        promotionCodeDiscount = promotionCode.discountPrice
                    } else if(promotionCode.discountPercent != null) {
                        promotionCodeDiscount = promotionCode.discountPercent * promotionData.priceTotal / 100
                    }
                    promotionData.priceDiscount += promotionCodeDiscount
                    promotionData.priceTotal -= promotionCodeDiscount
                    promotionDetails.add(
                        PromotionDetailData(
                            promotionCode.code.trim(),
                            promotionCodeDiscount,
                            PromotionType.COUPON,
                            promotionCodeQuantityId = promotionCodeQuantityId
                        )
                    )
                    break@loop
                }
            }
        }

        // --- Apply Max Discount Amount Cap ---
        config.maxDiscountAmount?.let { maxConfig ->
            // Apply per-type caps
            maxConfig.maxDiscountPerType?.let { perTypeCaps ->
                // Group promotion details by type category
                var discountByDateTotal = 0L
                var discountByTicketAmountTotal = 0L
                var discountByPriceCategoryAmountTotal = 0L
                var discountByPromotionCodeTotal = 0L

                for (detail in promotionDetails) {
                    when (detail.type) {
                        PromotionType.NONE -> discountByDateTotal += detail.priceDiscount
                        PromotionType.COMBO -> discountByTicketAmountTotal += detail.priceDiscount
                        PromotionType.COUPON -> discountByPromotionCodeTotal += detail.priceDiscount
                    }
                }

                // Cap discountByDate
                perTypeCaps["discountByDate"]?.let { cap ->
                    if (discountByDateTotal > cap) {
                        val excess = discountByDateTotal - cap
                        promotionData.priceDiscount -= excess
                        promotionData.priceTotal += excess
                        // Proportionally reduce date discount details
                        for (detail in promotionDetails) {
                            if (detail.type == PromotionType.NONE && discountByDateTotal > 0) {
                                val ratio = detail.priceDiscount.toDouble() / discountByDateTotal
                                detail.priceDiscount = (cap * ratio).toLong()
                            }
                        }
                    }
                }

                // Cap discountByTicketAmount + discountByPriceCategoryAmount (both are COMBO type)
                perTypeCaps["discountByTicketAmount"]?.let { cap ->
                    if (discountByTicketAmountTotal > cap) {
                        val excess = discountByTicketAmountTotal - cap
                        promotionData.priceDiscount -= excess
                        promotionData.priceTotal += excess
                        for (detail in promotionDetails) {
                            if (detail.type == PromotionType.COMBO && discountByTicketAmountTotal > 0) {
                                val ratio = detail.priceDiscount.toDouble() / discountByTicketAmountTotal
                                detail.priceDiscount = (cap * ratio).toLong()
                            }
                        }
                    }
                }

                // Cap promotionCode
                perTypeCaps["promotionCode"]?.let { cap ->
                    if (discountByPromotionCodeTotal > cap) {
                        val excess = discountByPromotionCodeTotal - cap
                        promotionData.priceDiscount -= excess
                        promotionData.priceTotal += excess
                        for (detail in promotionDetails) {
                            if (detail.type == PromotionType.COUPON && discountByPromotionCodeTotal > 0) {
                                val ratio = detail.priceDiscount.toDouble() / discountByPromotionCodeTotal
                                detail.priceDiscount = (cap * ratio).toLong()
                            }
                        }
                    }
                }
            }

            // Apply total order discount cap
            maxConfig.maxDiscountTotal?.let { maxTotal ->
                if (promotionData.priceDiscount > maxTotal) {
                    val excess = promotionData.priceDiscount - maxTotal
                    promotionData.priceDiscount = maxTotal
                    promotionData.priceTotal += excess
                    // Proportionally reduce all detail discounts
                    val totalDetailDiscount = promotionDetails.sumOf { it.priceDiscount }
                    if (totalDetailDiscount > 0) {
                        for (detail in promotionDetails) {
                            val ratio = detail.priceDiscount.toDouble() / totalDetailDiscount
                            detail.priceDiscount = (maxTotal * ratio).toLong()
                        }
                    }
                }
            }
        }

        if(promotionData.priceTotal < 0) {
            promotionData.priceTotal = 0
        }

        return promotionData
    }

    override suspend fun applyPromotionDiscount(input: ApplyPromotionInput) {
        val promotionData = input.promotionData

        var shouldApply = true
        loop@for(promotionDetail in promotionData.details) {
            if(promotionDetail.error) {
                shouldApply = false
                break@loop
            }
        }
        if(shouldApply) {
            var failedIndex = -1
            loop@for((index, promotionDetail) in promotionData.details.withIndex()) {
                if(promotionDetail.promotionCodeQuantityId != null && ObjectId.isValid(promotionDetail.promotionCodeQuantityId) && promotionDetail.type == PromotionType.COUPON) {
                    val result = input.promotionCodeQuantityDBService.decrementPromotionCodeQuantity(promotionDetail.promotionCodeQuantityId!!)
                    if(result == null) {
                        failedIndex = index
                        break@loop
                    }
                } else if(promotionDetail.promotionComboQuantityId != null && promotionDetail.type == PromotionType.COMBO) {
                    val result = input.promotionComboQuantityDBService.decrementPromotionComboQuantity(promotionDetail.promotionComboQuantityId!!, promotionDetail.promotionComboQuantity ?: 1)
                    if(result == null) {
                        failedIndex = index
                        break@loop
                    }
                }
            }
            if(failedIndex > -1) {
                loop@for((index, promotionDetail) in promotionData.details.withIndex()) {
                    if(index <= failedIndex) {
                        when(promotionDetail.type) {
                            PromotionType.COUPON -> {
                                input.promotionCodeQuantityDBService.incrementPromotionCodeQuantity(promotionDetail.promotionCodeQuantityId!!)
                            }
                            PromotionType.COMBO -> {
                                if(!promotionDetail.error) {
                                    input.promotionComboQuantityDBService.incrementPromotionComboQuantity(promotionDetail.promotionComboQuantityId!!, promotionDetail.promotionComboQuantity ?: 1)
                                }
                            }
                            else -> {

                            }
                        }

                    } else {
                        break@loop
                    }
                }
                promotionData.applyPromotionFailed = true
            }
        }
    }

    override suspend fun revertPromotionDiscount(input: RevertPromotionInput) {
        for (detail in input.details) {
            runCatching {
                if (detail.promotionCodeQuantityId != null) {
                    input.promotionCodeQuantityDBService.incrementPromotionCodeQuantity(detail.promotionCodeQuantityId)
                    logger.info("reverted promotion {}", detail)
                } else if (detail.promotionComboQuantityId != null) {
                    input.promotionComboQuantityDBService.incrementPromotionComboQuantity(detail.promotionComboQuantityId, detail.promotionComboQuantity ?: 1)
                    logger.info("reverted promotion {}", detail)
                }
            }.onFailure {
                logger.error("revertPromotionDiscount failed... ", it)
            }
        }
    }

    override suspend fun createPromotion(input: CreatePromotionInput): Boolean {
        val data = input.jsonMapper?.readValue(input.data, Array<CreatePromotionData>::class.java)?.toList() ?: return false

        val discountByDates: MutableList<MetaPromoDiscountByDateConfig> = mutableListOf()
        val discountByTicketAmounts: MutableList<MetaPromoDiscountByTicketAmountConfig> = mutableListOf()
        val discountByPriceCategoryAmounts: MutableList<MetaPromoDiscountByPriceCategoryAmountConfig> = mutableListOf()
        val promotionCodes: MutableList<MetaPromoPromotionCodeConfig> = mutableListOf()
        var maxDiscountAmountConfig: MetaPromoMaxDiscountAmountConfig? = null
        for(item in data) {
            when(item.type) {
                "discountByTicketAmount" -> {
                    var discountPercent: Long? = null
                    var discountPrice: Long? = null
                    if(item.discountOption == "DiscountPercent") {
                        discountPercent = item.discountValue
                    } else {
                        discountPrice = item.discountValue
                    }
                    var id: String? = ""
                    if(item.needCheckQuantity == true) {
                        id = input.promotionComboQuantityDBService.createPromotionComboQuantity(PromotionComboQuantity(
                            showId = input.showId,
                            sessionId = input.sessionId,
                            originalQuantity = item.originalQuantity ?: 0,
                            quantity = item.originalQuantity ?: 0,
                            lastModified = Date()
                        ))
                    }

                    discountByTicketAmounts.add(MetaPromoDiscountByTicketAmountConfig(
                        ticketAmountBelow = item.ticketAmountMin ?: 0,
                        ticketAmountAbove = item.ticketAmountMax,
                        discountPercent = discountPercent,
                        discountPrice = discountPrice,
                        id = id,
                        needCheckQuantity = item.needCheckQuantity,
                        originalQuantity = item.originalQuantity,
                        quantityType = item.quantityOption
                    ))
                }
                "discountByDate" -> {
                    try {
                        val date = ZonedDateTime.parse(item.date)
                        var discountPercent: Long? = null
                        var discountPrice: Long? = null
                        if(item.discountOption == "DiscountPercent") {
                            discountPercent = item.discountValue
                        } else {
                            discountPrice = item.discountValue
                        }

                        discountByDates.add(MetaPromoDiscountByDateConfig(
                            name = item.name ?: "",
                            date = date.toInstant().toEpochMilli(),
                            discountPrice = discountPrice,
                            discountPercent = discountPercent,
                            promotionCodes = null,
                        ))
                    } catch(ex: Exception) {
                        logger.debug(ex.toString())
                    }
                }
                "discountByPromotionCode" -> {
                    var discountPercent: Long? = null
                    var discountPrice: Long? = null
                    if(item.discountOption == "DiscountPercent") {
                        discountPercent = item.discountValue
                    } else {
                        discountPrice = item.discountValue
                    }
                    if(item.needCheckQuantity == true) {
                        val isCodeExists = input.promotionCodeQuantityDBService.loadPromotionCodeQuantity(
                            showId = input.showId,
                            sessionId = input.sessionId,
                            code = item.code?.trim() ?: "",
                        )
                        if(isCodeExists == null) {
                            input.promotionCodeQuantityDBService.createPromotionCodeQuantity(
                                PromotionCodeQuantity(
                                    showId = input.showId,
                                    sessionId = input.sessionId,
                                    code = item.code?.trim() ?: "",
                                    originalQuantity = item.originalQuantity ?: 0,
                                    quantity = item.originalQuantity ?: 0,
                                    lastModified = Date()
                                )
                            )
                        }
                    }

                    promotionCodes.add(MetaPromoPromotionCodeConfig(
                        discountPercent = discountPercent,
                        discountPrice = discountPrice,
                        needCheckQuantity = item.needCheckQuantity,
                        originalQuantity = item.originalQuantity,
                        code = item.code?.trim() ?: "",
                        name = item.name ?: "",
                    ))
                }
                "discountByMultiPromotionCode" -> {
                    var discountPercent: Long? = null
                    var discountPrice: Long? = null
                    if(item.discountOption == "DiscountPercent") {
                        discountPercent = item.discountValue
                    } else {
                        discountPrice = item.discountValue
                    }
                    if(item.needCheckQuantity == true) {
                        val codes = item.codes!!.split(",")
                        codes.map { code ->
                            val isCodeExists = input.promotionCodeQuantityDBService.loadPromotionCodeQuantity(
                                showId = input.showId,
                                sessionId = input.sessionId,
                                code = code.trim(),
                            )
                            if(isCodeExists == null) {
                                input.promotionCodeQuantityDBService.createPromotionCodeQuantity(
                                    PromotionCodeQuantity(
                                        showId = input.showId,
                                        sessionId = input.sessionId,
                                        code = code.trim(),
                                        originalQuantity = item.originalQuantity ?: 0,
                                        quantity = item.originalQuantity ?: 0,
                                        lastModified = Date()
                                    )
                                )
                            }
                            promotionCodes.add(MetaPromoPromotionCodeConfig(
                                discountPercent = discountPercent,
                                discountPrice = discountPrice,
                                needCheckQuantity = item.needCheckQuantity,
                                originalQuantity = item.originalQuantity,
                                code = code.trim(),
                                name = item.name ?: "",
                            ))
                        }
                    } else {
                        val codes = item.codes!!.split(",")
                        codes.map { code ->
                            promotionCodes.add(MetaPromoPromotionCodeConfig(
                                discountPercent = discountPercent,
                                discountPrice = discountPrice,
                                needCheckQuantity = false,
                                originalQuantity = null,
                                code = code.trim(),
                                name = item.name ?: "",
                            ))
                        }
                    }
                }
                "discountByPriceCategoryAmount" -> {
                    var discountPercent: Long? = null
                    var discountPrice: Long? = null
                    if(item.discountOption == "DiscountPercent") {
                        discountPercent = item.discountValue
                    } else {
                        discountPrice = item.discountValue
                    }

                    val conditions = item.priceCatConditions?.map {
                        MetaPromoPriceCatConditionConfig(
                            priceCat = it.priceCat,
                            minQuantity = it.minQuantity,
                            maxQuantity = it.maxQuantity
                        )
                    } ?: listOf()

                    var applyDiscountOn = ApplyDiscountOnType.TotalOrder
                    if (item.applyDiscountOn == "MatchedItems") {
                        applyDiscountOn = ApplyDiscountOnType.MatchedItems
                    }

                    discountByPriceCategoryAmounts.add(MetaPromoDiscountByPriceCategoryAmountConfig(
                        conditions = conditions,
                        discountPercent = discountPercent,
                        discountPrice = discountPrice,
                        applyDiscountOn = applyDiscountOn
                    ))
                }
                "maxDiscountAmount" -> {
                    // Build per-type map from flat fields or use provided map
                    val perTypeMap: MutableMap<String, Long> = mutableMapOf()
                    if (item.enablePerType == true) {
                        item.maxDiscountByDate?.let { perTypeMap["discountByDate"] = it }
                        item.maxDiscountByTicketAmount?.let { perTypeMap["discountByTicketAmount"] = it }
                        item.maxDiscountByPromotionCode?.let { perTypeMap["promotionCode"] = it }
                    }
                    // Also merge explicit map if provided
                    item.maxDiscountPerType?.let { perTypeMap.putAll(it) }

                    maxDiscountAmountConfig = MetaPromoMaxDiscountAmountConfig(
                        maxDiscountPerType = if (perTypeMap.isNotEmpty()) perTypeMap else null,
                        maxDiscountTotal = item.maxDiscountTotal
                    )
                }
            }
        }

        val config = MetaPromoConfiguration(
            discountByDates = discountByDates,
            discountByTicketAmounts = discountByTicketAmounts,
            discountByPriceCategoryAmounts = discountByPriceCategoryAmounts,
            promotionCodes = promotionCodes,
            maxDiscountAmount = maxDiscountAmountConfig,
        )

        input.promotionDBService.createPromotion(
            Promotion(
                showId = input.showId,
                sessionId = input.sessionId,
                pluginName = "metaticket.order.calculate.promotion.meta_promo.MetaPromoPlugin",
                configuration = input.jsonMapper?.writeValueAsString(config) ?: ""
            )
        )
        return true
    }

    override suspend fun deletePromotion(input: DeletePromotionInput): Boolean {
        input.promotionDBService.deletePromotion(input.promotionId)
        return true
    }
}
