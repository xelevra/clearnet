package clearnet

import clearnet.error.ClearNetworkException
import clearnet.help.*
import clearnet.model.RPCRequestBody
import clearnet.support.AsyncRequestExecutor
import io.reactivex.internal.schedulers.ImmediateThinScheduler
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncRequestExecutorTest : CoreBlocksTest() {

    lateinit var controller: TestAsyncController
    val serializer = GsonTestSerializer()
    private lateinit var converterExecutor: Core
    private lateinit var testRequests: TestRequests
    lateinit var executor: AsyncRequestExecutor

    @Before
    fun initialize(){
        controller = TestAsyncController()
        executor = AsyncRequestExecutor(controller)
        converterExecutor = Core(ImmediateExecutor, testScheduler, blocks = *coreBlocks.getAll())
        testRequests = ExecutorWrapper(converterExecutor, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequests::class.java, executor, Int.MAX_VALUE)
    }

    @Test
    fun functionalTest() {
        val request = RPCRequest("xynta")
        val flatRequest = serializer.serialize(request)
        var result: String? = null

        executor.postAsync(flatRequest, emptyMap(), emptyMap(), request)
                .observeOn(ImmediateThinScheduler.INSTANCE)
                .subscribe { (body, _) ->
                    result = body
                }

        assertNull(result)
        assertEquals(1, controller.subscribers)
        assertEquals(1, controller.pushes.values.size)
        assertEquals(flatRequest, controller.pushes.value)

        val testResult = JSONObject().apply {
            put("id", -1)
            put("result", "ok")
        }

        controller.subject.onNext(testResult.toString())

        // not the same id
        assertNull(result)
        assertEquals(1, controller.subscribers)

        testResult.put("id", request.id)
        controller.subject.onNext(testResult.toString())

        assertEquals(1, controller.pushes.values.size)
        assertEquals(0, controller.subscribers)
        assertEquals(testResult.toString(), result)
    }

    @Test
    fun subscribeTest() {
        var result: String? = null
        var error: Throwable? = null
        executor.observe(emptyMap(), mapOf(
            "method" to "showAss"
        )).observeOn(ImmediateThinScheduler.INSTANCE).subscribe ({
            result = it
        }, {
            error = it
        })
        assertNull(result)
        assertNull(error)
        assertEquals(1, controller.subscribers)
        assertEquals(0, controller.pushes.values.size)


        var obj = RPCRequestBody(1, "anyMethod", "test")
        controller.subject.onNext(serializer.serialize(obj))
        assertNull(result)
        assertNull(error)
        assertEquals(1, controller.subscribers)

        obj = RPCRequestBody(1, "showAss", "test2")
        serializer.serialize(obj).let {
            controller.subject.onNext(it)
            assertEquals(it, result)
            assertNull(error)
            assertEquals(1, controller.subscribers)
        }

        obj = RPCRequestBody(1, "showAss", "test3")
        serializer.serialize(obj).let {
            controller.subject.onNext(serializer.serialize(obj))
            assertEquals(it, result)
            assertNull(error)
            assertEquals(1, controller.subscribers)
        }

        result = null
        controller.subject.onNext("not valid")
        assertNull(result)
        assertNotNull(error)
        assertTrue(error is ClearNetworkException)
        assertEquals(0, controller.subscribers)
    }

    @Test
    fun integrationTest(){
        var result: String? = null
        var error: Throwable? = null

        testRequests.asyncBlockTest().observeOn(ImmediateThinScheduler.INSTANCE).subscribe({
            result = it
        }, {
            error = it
        })

        forwardScheduler()

        assertNull(result)
        assertEquals(1, controller.subscribers)
        assertEquals(1, controller.pushes.values.size)

        var requestId = JSONObject(controller.pushes.value).getLong("id")

        // second
        testRequests.asyncBlockTest().subscribe({
            result = it
        }, {
            error = it
        })

        forwardScheduler()

        assertNull(error)
        assertEquals(1, controller.subscribers)
        assertEquals(2, controller.pushes.values.size)


        val testResult = JSONObject().apply {
            put("id", requestId)
            put("result", "ok")
        }

        controller.subject.onNext(testResult.toString())

        forwardScheduler()

        assertNull(error)
        assertEquals(2, controller.pushes.values.size)
        assertEquals(1, controller.subscribers)
        assertEquals("ok", result)

        requestId = JSONObject(controller.pushes.value).getLong("id")

        testResult.put("id", requestId)
        testResult.put("result", "ok2")

        controller.subject.onNext(testResult.toString())

        forwardScheduler()

        assertNull(error)
        assertEquals(2, controller.pushes.values.size)
        assertEquals(0, controller.subscribers)
        assertEquals("ok2", result)
    }

}