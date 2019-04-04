package clearnet

import clearnet.error.ClearNetworkException
import clearnet.model.PostParams
import io.reactivex.subjects.ReplaySubject
import java.util.concurrent.atomic.AtomicLong

abstract class StaticTask(
    val postParams: PostParams) {
    val id = idIterator.incrementAndGet()
    val cacheKey: String by lazy { postParams.cacheKey }
    val requestKey: String by lazy { postParams.flatRequest }
    val startTime = System.currentTimeMillis()

    protected val results = ReplaySubject.create<Result>()

    // Warning not reactive transformations
    fun getLastResult(): SuccessResult = results.values.asSequence()
            .map { it as Result }
            .last { !it.isAncillary && it is SuccessResult } as SuccessResult

    // Warning not reactive transformations
    fun getLastErrorResult(): ErrorResult = results.values.asSequence()
            .map { it as Result }
            .last { !it.isAncillary && it is ErrorResult} as ErrorResult

    fun getRequestIdentifier() = postParams.requestTypeIdentifier

    @Deprecated("")
    fun isFinished() = results.hasComplete()

    fun respond(method: String, params: String?): Boolean {
        return postParams.requestTypeIdentifier == method && (params == null || cacheKey == params)
    }

    private fun resolveNextIndexes(index: InvocationBlockType, isSuccess: Boolean) = postParams.invocationStrategy[index][isSuccess]

    companion object {
        private val idIterator = AtomicLong()
    }

    inner class Promise {
        private val resultSubject = ReplaySubject.create<Result>()
        val taskRef: StaticTask = this@StaticTask
        internal fun observe() = resultSubject.hide()

        // Unfortunately we must handle null responses
        fun setResult(result: Any?, plainResult: String?, from: InvocationBlockType) {
            dispatch(SuccessResult(result, plainResult, resolveNextIndexes(from, true)))
        }

        fun setError(exception: ClearNetworkException, from: InvocationBlockType) {
            dispatch(ErrorResult(exception, resolveNextIndexes(from, false)))
        }

        fun next(from: InvocationBlockType, success: Boolean = true) = setNextIndexes(resolveNextIndexes(from, success))

        fun pass(from: InvocationBlockType) = next(from, false)

        /**
         * Edit the InvocationStrategy flow:
         * a manual set index will be used instead of InvocationStrategy's indexes
         */
        fun setNextIndex(nextIndex: InvocationBlockType) = setNextIndexes(arrayOf(nextIndex))

        /**
         * Edit the InvocationStrategy flow:
         * manual set indexes will be used instead of InvocationStrategy's indexes
         */
        fun setNextIndexes(nextIndexes: Array<InvocationBlockType>) {
            dispatch(Result(nextIndexes))
        }

        private fun dispatch(result: Result) {
            resultSubject.onNext(result)
            resultSubject.onComplete()
        }
    }


    open class Result(val nextIndexes: Array<InvocationBlockType>, internal val isAncillary: Boolean = true)

    class ErrorResult(val error: ClearNetworkException, nextIndexes: Array<clearnet.InvocationBlockType>) : Result(nextIndexes, false)

    class SuccessResult(val result: kotlin.Any?, val plainResult: kotlin.String?, nextIndexes: kotlin.Array<clearnet.InvocationBlockType>) : Result(nextIndexes, false)
}