package clearnet.blocks

import clearnet.CoreTask
import clearnet.InvocationBlockType
import clearnet.interfaces.*

object DeliverErrorBlock: IInvocationSingleBlock {
    override val invocationBlockType = InvocationBlockType.DELIVER_ERROR

    override fun onEntity(promise: CoreTask.Promise) = with(promise) {
        taskRef.deliver(taskRef.getLastErrorResult())
        super.onEntity(promise)
    }
}