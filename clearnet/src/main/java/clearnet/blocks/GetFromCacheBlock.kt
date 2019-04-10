package clearnet.blocks

import clearnet.StaticTask
import clearnet.InvocationBlockType
import clearnet.error.ConversionException
import clearnet.interfaces.ICacheProvider
import clearnet.interfaces.IInvocationSingleBlock
import clearnet.interfaces.ISerializer

class GetFromCacheBlock(
        private val cacheProvider: ICacheProvider,
        private val converter: ISerializer
) : IInvocationSingleBlock {
    override val invocationBlockType = InvocationBlockType.GET_FROM_CACHE

    override fun onEntity(promise: StaticTask.Promise) = with(promise) {
        val responseString = cacheProvider.obtain(taskRef.cacheKey)

        if (responseString != null) {
            try {
                setResult(
                        converter.deserialize(responseString, taskRef.postParams.resultType),
                        responseString,
                        invocationBlockType
                )
                return
            } catch (e: ConversionException) {
                // todo remove cache item
                // todo log exception
                e.printStackTrace()
            }
        }

        pass(invocationBlockType)
    }
}