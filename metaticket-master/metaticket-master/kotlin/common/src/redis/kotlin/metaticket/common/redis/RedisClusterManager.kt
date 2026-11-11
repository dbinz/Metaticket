package metaticket.common.redis

import io.lettuce.core.RedisURI
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.codec.RedisCodec
import metaticket.common.logger.Logging
import java.time.Duration

/**
 * Manages the connection to a Redis Cluster using the Lettuce library.
 *
 * @param nodes List of Redis Cluster node addresses in the format "host:port".
 *
 * @author dongcatchay
 */
class RedisClusterManager<V> constructor(nodes: List<String>): Logging {
    private lateinit var _connection: StatefulRedisClusterConnection<String, V>

    // Create a list of RedisURI from the nodes list, handling errors if any
    private val _redisURIs = nodes.mapNotNull {
        try {
            val (host, port) = it.split(":")
            RedisURI.create(host, port.toInt())
        } catch (e: Throwable) {
            logger.error("Error creating RedisURI for node $it: ", e)
            null
        }
    }

    // Create RedisClusterClient, check if the RedisURI list is empty
    private val _clusterClient = if (_redisURIs.isNotEmpty()) {
        RedisClusterClient.create(_redisURIs)
    } else {
        logger.error("No valid Redis nodes provided.")
        throw IllegalArgumentException("No valid Redis nodes provided.")
    }

    val asyncCommands: RedisClusterAsyncCommands<String, V>
        get() = _connection.async()

    /**
     * Connects to the Redis Cluster.
     *
     * @return StatefulRedisClusterConnection if the connection is successful, null if it fails.
     */
    fun connect(codec: RedisCodec<String, V>? = null) {
        return try {
            val topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(true)
                .refreshPeriod(Duration.ofSeconds(30))
                .enableAllAdaptiveRefreshTriggers()
                .build()

            val clientOptions = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyRefreshOptions)
                .autoReconnect(true)
                .validateClusterNodeMembership(false)
                .maxRedirects(16)
                .build()

            _clusterClient.setOptions(clientOptions)

            if (codec != null)
                _connection = _clusterClient.connect(codec)
            else
                _connection = _clusterClient.connect() as StatefulRedisClusterConnection<String, V>
        } catch (e: Throwable) {
            logger.error("Error connecting to Redis Cluster: ", e)
            throw e
        }
    }

    /**
     * Closes the Redis Cluster connection.
     */
    fun shutdown() {
        try {
            _connection.close()
            _clusterClient.shutdown()
        } catch (e: Throwable) {
            logger.error("Error shutting down Redis Cluster client: ", e)
        }
    }
}
