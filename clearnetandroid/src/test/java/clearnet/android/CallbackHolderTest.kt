package clearnet.android

import clearnet.ExecutorWrapper
import clearnet.android.help.*
import junit.framework.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallbackHolderTest {
    lateinit var testConverterExecutor: TestConverterExecutor
    var callbackHolder = CallbackHolder(ImmediateExecutor)
    lateinit var testRequests: TestRequests

    @Before
    fun prepare() {
        testConverterExecutor = TestConverterExecutor()
        callbackHolder.callbackList.clear()
        testRequests = ExecutorWrapper(testConverterExecutor, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequests::class.java, RequestExecutorStub(), 1, callbackHolder)
    }

    @Test
    fun testDisposablesManualClear() {
        val testObserver = TestObserver<Any>()

        testRequests.reactiveRequest().subscribe(testObserver)

        assertEquals(1, callbackHolder.disposables.size())
        val params = testConverterExecutor.lastParams!!

        callbackHolder.clear()
        assertEquals(0, callbackHolder.disposables.size())

        testConverterExecutor.results.onNext(1)
        assertFalse(testObserver.called)
        assertEquals(0, callbackHolder.disposables.size())
    }

    @Test
    fun testExecuteOnCorrectExecutor(){
        val callFlag = AtomicInteger()
        val runFlag = AtomicInteger()

        val callbackHolder = clearnet.android.CallbackHolder(Executor {
            runFlag.incrementAndGet()
            it.run()
        })

        var testClass: Runnable? = Runnable { callFlag.incrementAndGet() }

        val testWrapped = callbackHolder.wrap(testClass!!, Runnable::class.java)


        testWrapped.run()

        assertEquals(1, callFlag.get())
        assertEquals(1, runFlag.get())

        callFlag.set(0)
        runFlag.set(0)

        val reference = WeakReference(testClass)

        testClass = null
        callbackHolder.clear()

        val started = System.currentTimeMillis()

        while (reference.get() != null && System.currentTimeMillis() - started < 2000) {
            System.gc()
        }

        testWrapped.run()

        assertEquals(0, callFlag.get())
        assertEquals(0, runFlag.get())

        assertNull(reference.get())
    }

    @Test
    fun testExecuteOnCorrectScheduler() {
        val testExecutor = TestExecutor()
        val specialCallbackHolder = CallbackHolder(testExecutor)
        val specialTestRequests = ExecutorWrapper(testConverterExecutor, HeadersProviderStub, GsonTestSerializer())
                .create(TestRequests::class.java, RequestExecutorStub(), 1, specialCallbackHolder)

        val testObserver = TestObserver<Any>()

        specialTestRequests.reactiveRequest().subscribe(testObserver)
        val params = testConverterExecutor.lastParams!!

        testConverterExecutor.results.onNext(1)

        assertFalse(testObserver.called)

        testExecutor.run()

        assertTrue(testObserver.called)
    }

    internal class TestObserver<T> : ObserverStub<T>() {
        var called = false
        var errorCalled = false

        override fun onNext(t: T) {
            called = true
        }

        override fun onError(e: Throwable) {
            errorCalled = true
        }
    }

    private class TestExecutor : Executor {
        private val tasks: Queue<Runnable> = LinkedList<Runnable>()

        override fun execute(command: Runnable?) {
            tasks += command
        }

        fun run() {
            while (tasks.isNotEmpty()){
                tasks.poll().run()
            }
        }
    }
}