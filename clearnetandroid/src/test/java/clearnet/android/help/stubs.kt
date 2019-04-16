package clearnet.android.help

import clearnet.interfaces.*
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import java.util.*

object BodyValidatorStub : IBodyValidator {
    override fun validate(body: Any?) {}
}

object CacheProviderStub : ICacheProvider {
    override fun store(key: String, value: String, expiresAfter: Long) {}
    override fun obtain(key: String): String? = null
}

abstract class ObserverStub<T> : Observer<T> {
    override fun onComplete() {}
    override fun onSubscribe(d: Disposable) {}

    override fun onError(e: Throwable) {
        throw e
    }
}

object HeadersProviderStub : HeaderProvider {
    override fun obtainHeadersList(): Map<String, String> = emptyMap()
}

open class RequestExecutorStub : IRequestExecutor {
    override fun executeGet(headers: Map<String, String>, queryParams: Map<String, String>): Pair<String, Map<String, String>> = "" to emptyMap()

    override fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String,String>): Pair<String, Map<String, String>> {
        return Pair("{\"id\":1, \"result\":\"test\"}", Collections.emptyMap<String, String>())
    }
}
