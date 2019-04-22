package clearnet.conversion

import clearnet.interfaces.ConversionStrategy
import clearnet.interfaces.ConversionStrategy.ConversionStrategyError
import clearnet.model.RpcErrorResponse
import org.json.JSONObject

open class DefaultConversionStrategy : ConversionStrategy {
    override fun checkErrorOrResult(response: JSONObject): String? {
        checkOuterError(response)
        return response.optString("result")
    }

    object INSTANCE: ConversionStrategy by DefaultConversionStrategy()
}

fun ConversionStrategy.checkOuterError(response: JSONObject) {
    if (response.has("error")) {
        throw ConversionStrategyError(response.optString("error"), RpcErrorResponse::class.java)
    }
}

class ServerSideRpcConversionStrategy : ConversionStrategy {
    override fun checkErrorOrResult(response: JSONObject): String? {
        return response.toString()
    }
}

class ServerSideRpcOnlyBodyConversionStrategy : ConversionStrategy {
    override fun checkErrorOrResult(response: JSONObject): String? {
        return response.optString("params")
    }
}