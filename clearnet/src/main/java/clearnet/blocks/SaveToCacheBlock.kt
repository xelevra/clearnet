package clearnet.blocks

import clearnet.InvocationBlockType
import clearnet.StaticTask
import clearnet.interfaces.ICacheProvider
import clearnet.interfaces.IInvocationSingleBlock

class SaveToCacheBlock(
        private val cacheProvider: ICacheProvider
) : IInvocationSingleBlock {
    override val invocationBlockType = InvocationBlockType.SAVE_TO_CACHE

    override fun onEntity(promise: StaticTask.Promise) = with(promise) {
        (lastResult as StaticTask.SuccessResult).plainResult?.let {
            cacheProvider.store(taskRef.cacheKey, it, taskRef.postParams.expiresAfter)
        }
        promise.next(invocationBlockType)
    }
}