package clearnet.support

import clearnet.RPCRequest
import clearnet.error.ClearNetworkException
import clearnet.error.ConversionException
import clearnet.error.UnknownExternalException
import clearnet.interfaces.IAsyncController
import clearnet.interfaces.IAsyncRequestExecutor
import io.reactivex.Observable
import io.reactivex.Single
import org.json.JSONException
import org.json.JSONObject

class AsyncRequestExecutor(
        private val asyncController: IAsyncController
) : IAsyncRequestExecutor {
    private val results: Observable<Pair<Long, String>>

    init {
        results = asyncController.listenInput().map { flatResult ->
            JSONObject(flatResult).getLong("id") to flatResult
        }.share()
    }

    override fun getAsync(headers: Map<String, String>, queryParams: Map<String, String>): Single<Pair<String, Map<String, String>>> {
        throw NotImplementedError("Only post methods currently supported")
    }

    override fun postAsync(body: String, headers: Map<String, String>, queryParams: Map<String, String>, bodyObject: RPCRequest?): Single<Pair<String, Map<String, String>>> {
        return results.filter {
            bodyObject!!.id == it.first
        }.firstOrError().map {
            it.second to emptyMap<String, String>()
        }.onErrorResumeNext { throwable ->
            Single.error(mapError(throwable))
        }.doOnSubscribe {
            asyncController.pushOutput(body).blockingGet()
        }
    }

    override fun observe(headers: Map<String, String>, queryParams: Map<String, String>): Observable<String> {
        return results.map {
            JSONObject(it.second) to it.second
        }.filter { (json, _) ->
            queryParams.all { (key, value) ->
                value == json.optString(key)
            }
        }.map {
            it.second
        }.onErrorResumeNext { throwable: Throwable ->
            Observable.error(mapError(throwable))
        }
    }

    private fun mapError(throwable: Throwable): ClearNetworkException = when (throwable) {
        is JSONException -> ConversionException(throwable)
        else -> UnknownExternalException(throwable)
    }
}