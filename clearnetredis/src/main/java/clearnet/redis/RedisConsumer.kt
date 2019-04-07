package clearnet.redis

import redis.clients.jedis.JedisPool

class RedisConsumer<WorkType, ResultType>(
        private val redisPool: JedisPool,
        private val ioSchedulerFactory: ISchedulerFactory,
        private val inputQueueName: String,
        private val resultsQueueName: String
) {

}