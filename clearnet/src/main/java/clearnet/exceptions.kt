package clearnet.error

import java.io.IOException

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


class ConversionException : ClearNetworkException {
    init {
        kind = ClearNetworkException.KIND.CONVERSION
    }

    constructor(cause: Throwable) : super(cause) {}

    constructor(message: String, cause: Throwable) : super(message, cause) {}
}

class HTTPCodeError(val code: Int, val response: String?) : ClearNetworkException("Http code error: $code") {
    init {
        kind = KIND.HTTP_CODE
    }
}

class InterruptFlowRequest(message: String) : ClearNetworkException(message) {
    init {
        kind = KIND.INTERRUPT_FLOW_REQUESTED
    }
}

class NetworkException(cause: IOException) : ClearNetworkException(cause) {
    init {
        kind = ClearNetworkException.KIND.NETWORK
    }
}

class ResponseErrorException(val error: Any?) : ClearNetworkException() {
    init {
        kind = ClearNetworkException.KIND.RESPONSE_ERROR
    }
}

class UnknownExternalException(source: Throwable) : ClearNetworkException(source) {
    init {
        kind = ClearNetworkException.KIND.UNKNOWN_EXTERNAL_ERROR
    }
}

class ValidationException(message: String, val model: Any) : ClearNetworkException(message) {
    init {
        kind = ClearNetworkException.KIND.VALIDATION
    }
}