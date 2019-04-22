package clearnet

import clearnet.error.ClearNetworkException
import clearnet.help.*
import clearnet.interfaces.ISerializer
import clearnet.model.RPCRequestBody
import clearnet.support.AsyncRequestExecutor
import io.reactivex.internal.schedulers.ImmediateThinScheduler
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionBlockTest : CoreBlocksTest() {
    lateinit var controller: TestAsyncController
    private lateinit var converterExecutor: Core
    private lateinit var testRequests: TestRequests
    private lateinit var requestExecutor: AsyncRequestExecutor
    private lateinit var serializer: ISerializer

    @Before
    fun initialize(){
        controller = TestAsyncController()
        requestExecutor = AsyncRequestExecutor(controller)
        converterExecutor = Core(ImmediateExecutor, Schedulers.from(ImmediateExecutor), blocks = *coreBlocks.getAll())
        testRequests = ExecutorWrapper(converterExecutor, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequests::class.java, requestExecutor, Int.MAX_VALUE)
        serializer = GsonTestSerializer()
    }

    @Test
    fun subscribe() {
        var result: RPCRequestBody<String>? = null
        var error: Throwable? = null
        testRequests.subscribeTest().observeOn(ImmediateThinScheduler.INSTANCE).subscribe({
            result = it
        }, {
            error = it
        })


        assertNull(result)
        assertNull(error)
        assertEquals(1, controller.subscribers)

        var obj = RPCRequestBody(1, "anyMethod", "test")
        controller.subject.onNext(serializer.serialize(obj))
        assertEquals(obj, result)
        assertNull(error)
        assertEquals(1, controller.subscribers)

        obj = RPCRequestBody(1, "anyMethod", "test2")
        controller.subject.onNext(serializer.serialize(obj))
        assertEquals(obj, result)
        assertNull(error)
        assertEquals(1, controller.subscribers)

        result = null
        controller.subject.onNext("not valid")
        assertNull(result)
        assertNotNull(error)
        assertTrue(error is ClearNetworkException)
        assertEquals(0, controller.subscribers)
    }

    @Test
    fun unsubscribe() {
        var error: Throwable? = null
        val disposable = testRequests.subscribeTest().observeOn(ImmediateThinScheduler.INSTANCE).subscribe({

        }, {
            error = it
        })

        var observedCoreFeatureObject: RPCRequestBody<String>? = null
        // if subscription still emit values then task was not finished
        converterExecutor.observe<RPCRequestBody<String>>("test.subscribeTest").subscribe {
            observedCoreFeatureObject = it
        }

        assertNull(error)
        assertEquals(1, controller.subscribers)

        val obj = RPCRequestBody(1, "test.subscribeTest", "test")
        controller.subject.onNext(serializer.serialize(obj))
        assertEquals(obj, observedCoreFeatureObject)
        assertNull(error)
        assertEquals(1, controller.subscribers)

        observedCoreFeatureObject = null
        error = null

        disposable.dispose()
        assertEquals(0, controller.subscribers)

        controller.subject.onNext(serializer.serialize(obj))
        assertNull(obj, observedCoreFeatureObject)
    }
}