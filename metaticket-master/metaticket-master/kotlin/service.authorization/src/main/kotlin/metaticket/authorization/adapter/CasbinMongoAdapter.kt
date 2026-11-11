package metaticket.authorization.adapter

import com.mongodb.client.MongoClient
import metaticket.common.logger.Logging
import org.casbin.jcasbin.persist.BatchAdapter
import org.casbin.jcasbin.persist.UpdatableAdapter
import org.jim.jcasbin.MongoAdapter


/**
 *
 * @author dongcatchay
 */
class CasbinMongoAdapter(
    private val mongoClient: MongoClient,
    dbName: String,
    policyCol: String,
): MongoAdapter(mongoClient, dbName, policyCol),
    UpdatableAdapter,
    BatchAdapter,
    Logging {

    override fun updatePolicy(sec: String, ptype: String, oldRule: MutableList<String>, newPolicy: MutableList<String>) {
        val session = mongoClient.startSession()
        try {
            session.startTransaction()
            removePolicy(sec, ptype, oldRule)
            addPolicy(sec, ptype, newPolicy)
            session.commitTransaction()
        } catch (t: Throwable) {
            logger.error("Adapter cannot update {}, old {} to new {} ... ", ptype, oldRule, newPolicy, t)
            session.abortTransaction()
        } finally {
            session.close()
        }
    }

    override fun addPolicies(sec: String, ptype: String, rules: MutableList<MutableList<String>>) {
        val session = mongoClient.startSession()
        try {
            session.startTransaction()
            rules.forEach { addPolicy(sec, ptype, it) }
            session.commitTransaction()
        } catch (t: Throwable) {
            logger.error("Adapter cannot add {}, rules {} ... ", ptype, rules)
            session.abortTransaction()
        } finally {
            session.close()
        }
    }

    override fun removePolicies(sec: String, ptype: String, rules: MutableList<MutableList<String>>) {
        val session = mongoClient.startSession()
        try {
            session.startTransaction()
            rules.forEach { removePolicy(sec, ptype, it) }
            session.commitTransaction()
        } catch (t: Throwable) {
            logger.error("Adapter cannot remove {}, rules {} ... ", ptype, rules)
            session.abortTransaction()
        } finally {
            session.close()
        }
    }
}
