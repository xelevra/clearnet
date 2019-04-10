package clearnet.blocks

import clearnet.StaticTask
import clearnet.InvocationBlockType
import clearnet.error.ConversionException
import clearnet.interfaces.ICacheProvider
import clearnet.interfaces.IInvocationSingleBlock

class SaveToCacheBlock(
        private val cacheProvider: ICacheProvider
) : IInvocationSingleBlock {
    override val invocationBlockType = InvocationBlockType.SAVE_TO_CACHE

    override fun onEntity(promise: StaticTask.Promise) = with(promise) {
        try {
            taskRef.getLastResult().plainResult?.let {
                cacheProvider.store(
                        taskRef.cacheKey,
                        it,
                        taskRef.postParams.expiresAfter
                )
            }
        } catch (e: ConversionException) {
            // todo log error
            e.printStackTrace()
        }
        promise.next(invocationBlockType)
    }
}