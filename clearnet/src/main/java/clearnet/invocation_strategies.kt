package clearnet

import clearnet.InvocationBlockType.*
import clearnet.interfaces.IInvocationStrategy
import clearnet.interfaces.IInvocationStrategy.Decision


enum class InvocationStrategy(
        override val algorithm: Map<InvocationBlockType, Decision>,
        override val metaData: Map<String, String> = emptyMap()
): IInvocationStrategy {
    NO_CACHE(mapOf(
            INITIAL toNext GET_FROM_NET,
            CHECK_AUTH_TOKEN to GET_FROM_NET / DELIVER_ERROR,
            GET_FROM_NET to DELIVER_RESULT / RESOLVE_ERROR
    )),

    SUBSCRIBE(mapOf(
            INITIAL toNext SUBSCRIPTION,
            SUBSCRIPTION to DELIVER_RESULT / DELIVER_ERROR
    )),

    PRIORITY_REQUEST(mapOf(
            INITIAL toNext GET_FROM_NET,
            CHECK_AUTH_TOKEN to GET_FROM_NET / DELIVER_ERROR,
            GET_FROM_NET to arrayOf(DELIVER_RESULT, SAVE_TO_CACHE) / GET_FROM_CACHE,
            GET_FROM_CACHE to DELIVER_RESULT / RESOLVE_ERROR
    )),

    PRIORITY_CACHE(mapOf(
            INITIAL toNext GET_FROM_CACHE,
            CHECK_AUTH_TOKEN to GET_FROM_CACHE / DELIVER_ERROR,
            GET_FROM_CACHE to DELIVER_RESULT / GET_FROM_NET,
            GET_FROM_NET to arrayOf(DELIVER_RESULT, SAVE_TO_CACHE) / RESOLVE_ERROR
    )),

    REQUEST_EXCLUDE_CACHE(mapOf(
            INITIAL toNext GET_FROM_NET,
            CHECK_AUTH_TOKEN to GET_FROM_NET / DELIVER_ERROR,
            GET_FROM_NET to arrayOf(DELIVER_RESULT, SAVE_TO_CACHE) / RESOLVE_ERROR
    )),

    RETRY_IF_NO_NETWORK(emptyMap(), mapOf("retry_network_error" to "true")),

    AUTHORIZED_REQUEST(mapOf(
            INITIAL toNext CHECK_AUTH_TOKEN
    ));
}

private infix fun InvocationBlockType.toNext(destination: InvocationBlockType) = this to Decision(destination)
private operator fun InvocationBlockType.div(ifError: InvocationBlockType) = Decision(this, ifError)
private operator fun Array<InvocationBlockType>.div(ifError: InvocationBlockType) = Decision(this, ifError)