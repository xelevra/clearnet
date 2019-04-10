package clearnet.blocks

import clearnet.InvocationBlockType
import clearnet.interfaces.IInvocationSingleBlock

object InitialBlock : IInvocationSingleBlock {
    override val invocationBlockType = InvocationBlockType.INITIAL
}