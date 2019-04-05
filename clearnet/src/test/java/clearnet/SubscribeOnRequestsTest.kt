package clearnet

import clearnet.help.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class SubscribeOnRequestsTest : CoreBlocksTest() {
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
    fun subscribe() {
        val successes = AtomicInteger()
        val subscriptionCalled = AtomicInteger()


        val subscription = core.observe<String>("test.bindableTask").subscribe { subscriptionCalled.incrementAndGet() }

        testRequests.bindableTask(1).subscribe{successes.incrementAndGet()}
        forwardScheduler()
        assertEquals(1, successes.get())
        assertEquals(1, subscriptionCalled.get())

        testRequests.notBindableTask().subscribe{successes.incrementAndGet()}
        forwardScheduler()
        assertEquals(2, successes.get())
        assertEquals(1, subscriptionCalled.get())

        testRequests.bindableTask(2).subscribe { successes.incrementAndGet() }
        forwardScheduler()
        assertEquals(3, successes.get())
        assertEquals(2, subscriptionCalled.get())

        subscription.dispose()

        testRequests.bindableTask(3).subscribe{successes.incrementAndGet()}
        forwardScheduler()
        assertEquals(4, successes.get())
        assertEquals(2, subscriptionCalled.get())
    }
}