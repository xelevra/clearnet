package clearnet.blocks

import clearnet.InvocationBlockType
import clearnet.StaticTask
import clearnet.error.ClearNetworkException
import clearnet.error.ResponseErrorException
import clearnet.error.UnknownExternalException
import clearnet.interfaces.IInvocationSingleBlock
import clearnet.interfaces.IAsyncController
import clearnet.interfaces.ISerializer
import clearnet.model.RpcPostParams
import io.reactivex.Observable
import io.reactivex.Single
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GetAsyncBlock(
        private val serializer: ISerializer,
        private val asyncController: IAsyncController
) : IInvocationSingleBlock {
    override val invocationBlockType = InvocationBlockType.GET_FROM_NET
    private val results: Observable<JSONObject>


    init {
        results = asyncController.listenInput().map {
            try {
                JSONObject(it)
            } catch (e: JSONException) {
                JSONObject().apply {
                    put("error", JSONObject().apply {
                        put("code", 540)
                        put("message", "Cannot convert result to json: $it")
                    })
                }
            }
        }.replay(200, TimeUnit.MILLISECONDS).refCount() // buffer for publisher subscription after push
    }

    override fun onEntity(promise: StaticTask.Promise) {
        val postParams = promise.taskRef.postParams as RpcPostParams
        asyncController.pushOutput(postParams.flatRequest).flatMap {
            results.filter {
                postParams.requestBody.id == it.optLong("id")
            }.firstOrError()
        }.flatMap {
            if (it.has("error")) {
                Single.error(ResponseErrorException(it.getString("error")))
            } else {
                Single.just(
                        it.optString("result")
                )
            }
        }.map {
            it to serializer.deserialize(it, postParams.resultType)
        }.doOnSubscribe {

        }.subscribe({
            promise.setResult(it.second, it.first, invocationBlockType)
        }, {
            val error = if(it is ClearNetworkException) it else UnknownExternalException(it)
            promise.setError(error, invocationBlockType)
        })
    }
}