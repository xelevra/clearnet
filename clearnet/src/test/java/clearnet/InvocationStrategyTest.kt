package clearnet

import clearnet.InvocationStrategy.*
import clearnet.error.ClearNetworkException
import clearnet.error.NetworkException
import clearnet.help.*
import io.reactivex.schedulers.TestScheduler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class InvocationStrategyTest(
        var throwError: Boolean,
        var requestExecutorStateExpectation: Int,
        var returnObject: Boolean,
        var cacheProviderStateExpectation: Int,
        var callbackStateExpectation: Int,
        var invocationStrategy: clearnet.InvocationStrategy
) {

    val testScheduler = TestScheduler()
    val testCacheProvider = TestCacheProvider()
    val testRequestExecutor = TestRequestExecutor()
    val invocationBlocks = TestCoreBlocks(cacheProvider = testCacheProvider)
    val converterExecutor = Core(ImmediateExecutor, testScheduler, blocks = *invocationBlocks.getAll())
    val testRequests: TestRequests = ExecutorWrapper(converterExecutor, HeadersProviderStub, GsonTestSerializer())
            .create(TestRequests::class.java, testRequestExecutor, Int.MAX_VALUE)
    val testObserver = TestObserver()
    val timeT = invocationBlocks.getFromNetTimeThreshold

    companion object {
        @JvmStatic
        @Parameters
        fun data(): Collection<Array<Any>> {
            return listOf(
                    arrayOf(false, 1, false, 0, 10, NO_CACHE),
                    arrayOf(false, 1, true, 0, 10, NO_CACHE),
                    arrayOf(true, 1, false, 0, 1, NO_CACHE),
                    arrayOf(true, 1, true, 0, 1, NO_CACHE),

                    arrayOf(false, 1, false, 10, 10, PRIORITY_REQUEST),
                    arrayOf(false, 1, true, 10, 10, PRIORITY_REQUEST),
                    arrayOf(true, 1, false, 1, 1, PRIORITY_REQUEST),
                    arrayOf(true, 1, true, 1, 10, PRIORITY_REQUEST),

                    arrayOf(false, 1, false, 11, 10, PRIORITY_CACHE),
                    arrayOf(false, 0, true, 1, 10, PRIORITY_CACHE),
                    arrayOf(true, 1, false, 1, 1, PRIORITY_CACHE),
                    arrayOf(true, 0, true, 1, 10, PRIORITY_CACHE)
            )
        }
    }

    @Before
    fun setup() {
        testRequestExecutor.state = 0
        testRequestExecutor.throwError = false
        testCacheProvider.state = 0
        testObserver.state = 0
    }

    @Test
    fun test() {
        testRequestExecutor.throwError = throwError
        testCacheProvider.returnObject = returnObject

        when (invocationStrategy) {
            NO_CACHE -> testRequests.noCache().subscribe(testObserver)
            PRIORITY_REQUEST -> testRequests.priorityRequest().subscribe(testObserver)
            PRIORITY_CACHE -> testRequests.priorityCache().subscribe(testObserver)
        }

        testScheduler.advanceTimeBy(timeT, TimeUnit.MILLISECONDS)
        testScheduler.advanceTimeBy(timeT, TimeUnit.MILLISECONDS)

        assertEquals(requestExecutorStateExpectation, testRequestExecutor.state, "Strategy: $invocationStrategy")
        assertEquals(cacheProviderStateExpectation, testCacheProvider.state, "Strategy: + $invocationStrategy")
        assertEquals(callbackStateExpectation, testObserver.state, "Strategy: + $invocationStrategy Exception: ${testObserver.lastException}")
    }

    class TestRequestExecutor : RequestExecutorStub() {
        var throwError = false
        var state = 0

        override fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String, String>): Pair<String, Map<String, String>> {
            state++
            if (throwError) throw IOException()
            return Pair("{\"result\":\"test\"}", Collections.emptyMap())
        }
    }

    class TestObserver : ObserverStub<String>() {
        var state = 0;
        var lastException: ClearNetworkException? = null

        override fun onNext(t: String?) {
            state += 10
        }

        override fun onError(e: Throwable) {
            if (e !is NetworkException) throw e
            state++
            lastException = e
        }
    }
}