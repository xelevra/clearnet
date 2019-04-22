package clearnet

import clearnet.help.*
import clearnet.interfaces.IAsyncController
import clearnet.support.AsyncRequestExecutor
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.internal.schedulers.ImmediateThinScheduler
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import io.reactivex.subjects.Subject
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    class TestAsyncController: IAsyncController {
        val subject: Subject<String> = PublishSubject.create()
        var subscribers = 0
        val pushes = ReplaySubject.create<String>()

        override fun listenInput(): Observable<String> {
            return subject.doOnSubscribe {
                subscribers++
            }.doOnDispose {
                subscribers--
            }.subscribeOn(ImmediateThinScheduler.INSTANCE)
        }

        override fun pushOutput(params: String): Single<Long> {
            return Single.just(params).doOnSuccess(pushes::onNext).map { 1L }
        }
    }
}