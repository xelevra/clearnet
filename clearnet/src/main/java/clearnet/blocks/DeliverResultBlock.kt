package clearnet.blocks

import clearnet.CoreTask
import clearnet.StaticTask
import clearnet.InvocationBlockType
import clearnet.interfaces.IInvocationSingleBlock

object DeliverResultBlock : IInvocationSingleBlock {
    override val invocationBlockType = InvocationBlockType.DELIVER_RESULT

    override fun onEntity(promise: StaticTask.Promise) = with(promise) {
        (taskRef as CoreTask).deliver(taskRef.getLastResult())
        super.onEntity(promise)
    }
}