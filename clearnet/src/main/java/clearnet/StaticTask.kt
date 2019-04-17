package clearnet

import clearnet.error.ClearNetworkException
import clearnet.model.PostParams
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.ReplaySubject
import io.reactivex.subjects.Subject
import java.util.Comparator
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

abstract class StaticTask(
    val postParams: PostParams) {
    val id = idIterator.incrementAndGet()
    val cacheKey: String by lazy { postParams.cacheKey }
    val requestKey: String by lazy { postParams.flatRequest }
    val startTime = System.currentTimeMillis()

    protected val results: Subject<Result> = ReplaySubject.create<Result>().toSerialized()

    private val lastSuccess: Subject<SuccessResult> = BehaviorSubject.create<SuccessResult>().toSerialized()
    private val lastError: Subject<ErrorResult> = BehaviorSubject.create<ErrorResult>().toSerialized()
    private val resultsCount = AtomicInteger()

    init {
        results.doOnNext { resultsCount.incrementAndGet() }
                .filter { !it.isAncillary && it is SuccessResult }
                .map { it as SuccessResult }
                .subscribe(lastSuccess)
        results.filter { !it.isAncillary && it is ErrorResult }
                .map { it as ErrorResult }
                .subscribe(lastError)
    }

    fun getLastResult(): SuccessResult = lastSuccess.blockingFirst()

    fun getLastErrorResult(): ErrorResult = lastError.blockingFirst()

    fun getRequestIdentifier() = postParams.requestTypeIdentifier


    fun respond(method: String, params: String?): Boolean {
        return postParams.requestTypeIdentifier == method && (params == null || cacheKey == params)
    }

    override fun toString(): String {
        return "Task $id"
    }

    private fun resolveNextIndexes(index: InvocationBlockType, isSuccess: Boolean) = postParams.invocationStrategy[index][isSuccess]

    companion object {
        private val idIterator = AtomicLong()
    }

    inner class Promise {
        private val resultSubject = ReplaySubject.create<Result>()
        val taskRef: StaticTask = this@StaticTask
        internal fun observe() = resultSubject.hide()

        fun isFinished() = resultSubject.hasComplete() || resultSubject.hasThrowable()

        // Unfortunately we must handle null responses
        fun setResult(result: Any?, plainResult: String?, from: InvocationBlockType) {
            dispatch(SuccessResult(result, plainResult, resolveNextIndexes(from, true)))
        }

        fun setError(exception: ClearNetworkException, from: InvocationBlockType) {
            dispatch(ErrorResult(exception, resolveNextIndexes(from, false)))
            complete()
        }

        fun next(from: InvocationBlockType, success: Boolean = true) = move(resolveNextIndexes(from, success))

        fun pass(from: InvocationBlockType) = next(from, false)

        /**
         * Edit the InvocationStrategy flow:
         * a manual set index will be used instead of InvocationStrategy's indexes
         */
        fun move(nextIndex: InvocationBlockType) = move(arrayOf(nextIndex))

        /**
         * Edit the InvocationStrategy flow:
         * manual set indexes will be used instead of InvocationStrategy's indexes
         */
        fun move(nextIndexes: Array<InvocationBlockType>) {
            dispatch(Result(nextIndexes))
        }

        fun complete() {
            resultSubject.onComplete()
        }

        private fun dispatch(result: Result) {
            resultSubject.onNext(result)
            resultSubject.onComplete()
        }
    }


    open class Result(val nextIndexes: Array<InvocationBlockType>, internal val isAncillary: Boolean = true)

    class ErrorResult(val error: ClearNetworkException, nextIndexes: Array<clearnet.InvocationBlockType>) : Result(nextIndexes, false)

    class SuccessResult(val result: kotlin.Any?, val plainResult: kotlin.String?, nextIndexes: kotlin.Array<clearnet.InvocationBlockType>) : Result(nextIndexes, false)

    object ResultsCountComparator : Comparator<StaticTask> {
        override fun compare(p0: StaticTask, p1: StaticTask) = p0.resultsCount.get().compareTo(p1.resultsCount.get())
    }
}