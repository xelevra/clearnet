package clearnet.blocks

import clearnet.CoreTask
import clearnet.StaticTask
import clearnet.InvocationBlockType
import clearnet.interfaces.*

object DeliverErrorBlock: IInvocationSingleBlock {
    override val invocationBlockType = InvocationBlockType.DELIVER_ERROR

    override fun onEntity(promise: StaticTask.Promise) = with(promise) {
        (taskRef as CoreTask).deliver(taskRef.getLastErrorResult())
        super.onEntity(promise)
    }
}