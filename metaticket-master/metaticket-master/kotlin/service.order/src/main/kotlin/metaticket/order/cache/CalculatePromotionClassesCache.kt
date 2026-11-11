package metaticket.order.cache

import metaticket.order.calculate.promotion.CalculatePromotion

class CalculatePromotionClassesCache constructor() {
    private val calculatePromotionClasses = mutableMapOf<String, CalculatePromotion>()

    fun add(classInstancePromotion: CalculatePromotion) {
        calculatePromotionClasses[classInstancePromotion.javaClass.name] = classInstancePromotion
    }

    fun get(plugin: String): CalculatePromotion? {
        return calculatePromotionClasses[plugin]
    }
}