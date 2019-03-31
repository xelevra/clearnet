package clearnet

import clearnet.ConversionStrategiesTest.CallbackState.COMMON_ERROR
import clearnet.ConversionStrategiesTest.CallbackState.COMMON_OBJECT
import clearnet.error.ResponseErrorException
import clearnet.help.*
import clearnet.interfaces.ISerializer
import clearnet.model.RpcErrorResponse
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

@RunWith(Parameterized::class)
class ConversionStrategiesTest(
        var callbackState: CallbackState
) : CoreBlocksTest() {

    private lateinit var testRequestExecutor: TestRequestExecutor
    private lateinit var converterExecutor: Core
    private lateinit var testRequests: TestRequests
    private lateinit var observer: TestObserver

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Array<Any?>> {
            val d: ArrayList<Array<Any?>> = ArrayList() // I was confused with generic typing problems =(
            d.add(arrayOf(COMMON_OBJECT))
            d.add(arrayOf(COMMON_ERROR))
            return d
        }
    }

    @Before
    fun setup(){
        testRequestExecutor = TestRequestExecutor()
        converterExecutor = Core(ImmediateExecutor, testScheduler, blocks = *coreBlocks.getAll())
        testRequests = ExecutorWrapper(converterExecutor, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequests::class.java, testRequestExecutor, Int.MAX_VALUE)
        observer =  TestObserver()
    }


    @Before
    fun clean() {
        observer.testObject = null
        observer.exception = null
        testRequestExecutor.callbackState = callbackState
    }

    @Test
    fun test() {

        when (callbackState) {
            COMMON_OBJECT, COMMON_ERROR -> testRequests.commonResponse().subscribe(observer)
        }

        forwardScheduler()

        when (callbackState) {
            COMMON_OBJECT -> {
                assertNotNull(observer.testObject)
                assertEquals(1, observer.testObject!!.test)
                assertNull(observer.exception)
            }
            else -> {
                assertNull(observer.testObject)
                assertNotNull(observer.exception)

                val responseError = observer.exception as clearnet.error.ResponseErrorException
                assertNotNull(responseError.error)
                when (callbackState) {
                    COMMON_ERROR -> {
                        val rpcErrorResponse = responseError.error as RpcErrorResponse
                        assertEquals(1, rpcErrorResponse.code)
                        assertEquals("test", rpcErrorResponse.message)
                    }
                    else -> {
                        fail("Unexpected branch")
                    }
                }
            }
        }
    }


    class TestRequestExecutor : RequestExecutorStub() {
        private val converter: ISerializer by lazy { GsonTestSerializer() }
        lateinit var callbackState: CallbackState

        override fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String,String>): Pair<String, Map<String, String>> {
            val response =  when (callbackState) {
                COMMON_OBJECT -> "{\"result\":{\"test\":1}}"
                COMMON_ERROR -> "{\"error\":" + converter.serialize(RpcErrorResponse(1, "test", null)) + "}"
                else -> "ERROR"
            }
            return Pair(response, Collections.emptyMap())
        }
    }

    private class TestObserver : Observer<TestObject>{
        var testObject: TestObject? = null
        var exception: clearnet.error.ResponseErrorException? = null

        override fun onNext(t: TestObject) {
            testObject = t
        }

        override fun onError(e: Throwable) {
            if (e !is clearnet.error.ResponseErrorException) throw e
            this.exception = e
        }

        override fun onComplete() {}
        override fun onSubscribe(d: Disposable) {}
    }

    enum class CallbackState {
        COMMON_OBJECT, COMMON_ERROR
    }
}