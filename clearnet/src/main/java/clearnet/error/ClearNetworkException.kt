package clearnet.error

abstract class ClearNetworkException : Exception {
    var kind: KIND? = null
        protected set

    constructor()

    constructor(message: String) : super(message)

    constructor(cause: Throwable) : super(cause)

    constructor(message: String, cause: Throwable) : super(message, cause)

    enum class KIND {
        NETWORK, VALIDATION, CONVERSION, RESPONSE_ERROR, HTTP_CODE, INTERRUPT_FLOW_REQUESTED, UNKNOWN_EXTERNAL_ERROR
    }
}
