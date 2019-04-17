package clearnet.blocks

import clearnet.InvocationBlockType
import clearnet.StaticTask
import clearnet.interfaces.IInvocationSingleBlock

object InitialBlock : IInvocationSingleBlock {
    override val invocationBlockType = InvocationBlockType.INITIAL

    override fun onEntity(promise: StaticTask.Promise) {
        promise.next(invocationBlockType)
    }
}