package clearnet.blocks

import clearnet.InvocationBlockType
import clearnet.StaticTask
import clearnet.error.ClearNetworkException
import clearnet.error.UnknownExternalException
import clearnet.interfaces.ConversionStrategy
import clearnet.interfaces.IInvocationSubjectBlock
import clearnet.interfaces.ISerializer
import io.reactivex.Observable

class SubscriptionBlock(
    private val serializer: ISerializer
) : IInvocationSubjectBlock {
    override val invocationBlockType: InvocationBlockType
        get() = InvocationBlockType.SUBSCRIPTION

    override fun onEntity(promise: StaticTask.Promise): Unit = with(promise.taskRef.postParams) {
        requestExecutor.observe(headers, requestParams).map {
            ConversionStrategy.SmartConverter.convert(serializer, it, resultType, conversionStrategy) to it
        }.map { (result, flatResult) ->
            promise.createSuccessResult(result, flatResult, invocationBlockType)
        }.onErrorResumeNext { throwable: Throwable ->
            val exception = when (throwable) {
                is ClearNetworkException -> throwable
                else -> UnknownExternalException(throwable)
            }
            Observable.just(promise.createErrorResult(exception, invocationBlockType))
        }.subscribeDirect(promise)
    }

    private fun Observable<StaticTask.Result>.subscribeDirect(promise: StaticTask.Promise) = promise.subscribeDirect(this)
}