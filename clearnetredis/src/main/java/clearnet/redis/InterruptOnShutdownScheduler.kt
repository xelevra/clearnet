package clearnet.redis

import io.reactivex.Scheduler
import io.reactivex.internal.schedulers.NewThreadWorker
import java.lang.IllegalStateException
import java.util.concurrent.ThreadFactory

/**
 * The scheduler allows to correctly finish blocking tasks on shutdown
 */
class InterruptOnShutdownScheduler : Scheduler() {
    private val threads: MutableList<Thread> = ArrayList()
    private var finished = false

    @Synchronized
    override fun createWorker(): Worker {
        finished = false
        return NewThreadWorker(threadFactory)
    }

    @Synchronized
    override fun start() {
        if(finished) throw IllegalStateException("Start requested after shutting down. Use new instance instead")
        super.start()
    }

    @Synchronized
    override fun shutdown() {
        super.shutdown()
        threads.forEach(Thread::interrupt)
        threads.clear()
    }

    private val threadFactory: ThreadFactory = ThreadFactory { runnable ->
        synchronized(this@InterruptOnShutdownScheduler){
            return@synchronized Thread(runnable, "InterruptOnShutdownThread ${threads.size + 1}").also {
                threads += it
            }
        }
    }
}