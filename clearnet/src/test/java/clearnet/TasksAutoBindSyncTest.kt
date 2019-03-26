package clearnet

import clearnet.help.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class TasksAutoBindSyncTest : CoreBlocksTest() {
    private lateinit var core: Core
    private lateinit var testRequests: TestRequests

    @Before
    fun setup() {
        core = Core(
                ioExecutor = ImmediateExecutor,
                worker = testScheduler,
                blocks = *coreBlocks.getAll()
        )
        testRequests = ExecutorWrapper(core, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequests::class.java, BatchTestRequestExecutor(), 1, CallbackHolderStub)
    }

    @Test
    fun bindAfterSuccess() {
        val successes = AtomicInteger()

        val callback = { _: String -> successes.incrementAndGet(); Unit }

        testRequests.bindableTask(1).subscribe(callback)
        forwardScheduler()
        assertEquals(1, successes.get())

        testRequests.bindableTask(1).subscribe(callback)
        forwardScheduler()
        assertEquals(2, successes.get())
    }

    @Test
    fun bindAfterDeliverResult() {   // what happens if new callback is subscribed after result is delivered
        val successes = AtomicInteger()

        val observer = object : ObserverStub<String>(){
            override fun onNext(t: String?) {
                if (successes.getAndIncrement() == 0) {
                    testRequests.withCacheBindableTask().subscribe(this)
                }
            }
        }

        testRequests.withCacheBindableTask().subscribe(observer)
        forwardScheduler()
        forwardScheduler()

        assertEquals(2, successes.get())
    }

    @Test
    fun reactive() {
        val successes = AtomicInteger()

        testRequests.reactiveRequest(1).subscribe {
            successes.incrementAndGet()
        }

        forwardScheduler()

        assertEquals(1, successes.get())
    }
}