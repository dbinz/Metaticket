package metaticket.payment.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import metaticket.payment.pojo.db.PGAccount
import metaticket.payment.pojo.db.PaymentGatewayConfig


/**
 *
 * @author dongcatchay
 */
class PGAccountConfigDBService(
    private val col: MongoCollection<PaymentGatewayConfig>,
) {
    suspend fun insert(pgAccount: PGAccount): PGAccount? {
        val filters = Filters.not(Filters.exists("${PaymentGatewayConfig::pgAccount.name}.${pgAccount.code}"))
        val updates = Updates.set("${PaymentGatewayConfig::pgAccount.name}.${pgAccount.code}", pgAccount)
        val updated = withContext(Dispatchers.IO) { col.updateOne(filters, updates) }
        return if (updated.modifiedCount > 0) pgAccount else null
    }

    suspend fun remove(code: String): Boolean {
        val filters = Filters.not(Filters.eq(PaymentGatewayConfig::pgAccountDefaultCode.name, code))
        val updates = Updates.unset("${PaymentGatewayConfig::pgAccount.name}.$code")
        return withContext(Dispatchers.IO) { col.updateOne(filters, updates) }.matchedCount > 0
    }

    suspend fun load(code: String): PaymentGatewayConfig? {
        val filter = Filters.exists("${PaymentGatewayConfig::pgAccount.name}.$code")
        return withContext(Dispatchers.IO) { col.find(filter) }.firstOrNull()
    }

    suspend fun loadDefaultAccountCode(): String? {
        return withContext(Dispatchers.IO) { col.find(Filters.empty()) }.firstOrNull()?.pgAccountDefaultCode
    }

    suspend fun setEnable(code: String, value: Boolean): PaymentGatewayConfig? {
        val filter = Filters.exists("${PaymentGatewayConfig::pgAccount.name}.$code")
        val updates = Updates.set("${PaymentGatewayConfig::pgAccount.name}.$code.${PGAccount::enabled.name}", value)
        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, updates, options) }
    }

    suspend fun update(code: String, fields: List<Pair<String, Any?>>): PaymentGatewayConfig? {
        val filter = Filters.exists("${PaymentGatewayConfig::pgAccount.name}.$code")
        val updates = Updates.combine(fields.map { (f, v) ->
            Updates.set("${PaymentGatewayConfig::pgAccount.name}.$code.$f", v)
        })
        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, updates, options) }
    }

    suspend fun updateDefaultAccount(code: String): PaymentGatewayConfig? {
        val filter = Filters.exists("${PaymentGatewayConfig::pgAccount.name}.$code")
        val updates = Updates.set(PaymentGatewayConfig::pgAccountDefaultCode.name, code)
        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, updates, options) }
    }

    suspend fun loadConfig(): PaymentGatewayConfig? {
        return withContext(Dispatchers.IO) { col.find() }.firstOrNull()
    }
}
