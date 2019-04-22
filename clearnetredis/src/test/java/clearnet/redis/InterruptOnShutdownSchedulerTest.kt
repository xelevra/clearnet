package clearnet.redis

import io.reactivex.Observable
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class InterruptOnShutdownSchedulerTest {

    @Test
    fun testTest(){
        val interrupted = AtomicReference<Boolean?>()

        val observable = Observable.fromCallable {
            val testQueue = ArrayBlockingQueue<String>(1)
            try {
                testQueue.poll(500, TimeUnit.SECONDS)
                interrupted.set(false)
                System.out.println("Not interrupted")
            } catch (e: InterruptedException){
                interrupted.set(true)
                System.out.println("Interrupted")
            }
        }

        InterruptOnShutdownScheduler().let {
            observable.subscribeOn(it)
                    .subscribe()
            Thread.sleep(100)
            System.out.println("Shutdown requested")
            it.shutdown()
            Thread.sleep(500)
        }

        assertNotNull(interrupted.get())
        assertTrue(interrupted.get()!!)
    }
}