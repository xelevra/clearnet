package clearnet.error

class UnknownExternalException(source: Throwable) : ClearNetworkException(source) {

    init {
        kind = ClearNetworkException.KIND.UNKNOWN_EXTERNAL_ERROR
    }
}