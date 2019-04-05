package clearnet.redis

import clearnet.InvocationBlockType
import clearnet.interfaces.IInvocationSingleBlock
import redis.clients.jedis.JedisPool

class GetFromRedisBlock(
    redis: JedisPool
): IInvocationSingleBlock {
    override val invocationBlockType = InvocationBlockType.GET_FROM_NET

    
}