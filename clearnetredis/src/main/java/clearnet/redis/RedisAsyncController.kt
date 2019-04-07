package clearnet.redis

import clearnet.interfaces.IAsyncController
import io.reactivex.Observable
import io.reactivex.Single
import redis.clients.jedis.JedisPool

// todo work queue management
class RedisAsyncController(
        private val redisPool: JedisPool,
        private val schedulerFactory: ISchedulerFactory,
        private val inputQueueName: String,
        private val outputQueueName: String
) : IAsyncController {
    private val outputNonBlockingScheduler = schedulerFactory.provideScheduler()

    override fun listenInput(): Observable<String> {
        return Observable.fromCallable { redisPool.resource.brpop(inputQueueName)[1] }
                .subscribeOn(schedulerFactory.provideScheduler())
                .repeat()
    }

    override fun pushOutput(params: String): Single<Long> {
        return Single.fromCallable {
            redisPool.resource.lpush(outputQueueName, params)
        }.subscribeOn(outputNonBlockingScheduler)
    }
}