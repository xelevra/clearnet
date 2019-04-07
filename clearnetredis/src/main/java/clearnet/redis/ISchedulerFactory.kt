package clearnet.redis

import io.reactivex.Scheduler

interface ISchedulerFactory {
    fun provideScheduler(): Scheduler
}