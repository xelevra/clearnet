package clearnet.help

import clearnet.Wrapper
import clearnet.interfaces.*
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import java.util.*

object CallbackHolderStub : ICallbackHolder {
    override fun hold(disposable: Disposable) {}

    override fun init() {}
    override fun clear() {}

    override fun <I> createEmpty(type: Class<in I>): I = Wrapper.stub(type) as I
    override fun <I> wrap(source: I, interfaceType: Class<in I>): I = source
}

object BodyValidatorStub : IBodyValidator {
    override fun validate(body: Any?) {}
}

object CacheProviderStub : ICacheProvider {
    override fun store(key: String, value: String, expiresAfter: Long) {}
    override fun obtain(key: String): String? = null
}

object HeadersProviderStub : HeaderProvider {
    override fun obtainHeadersList(): Map<String, String> = emptyMap()
}

abstract class ObserverStub<T> : Observer<T> {
    override fun onComplete() {}
    override fun onSubscribe(d: Disposable) {}

    override fun onError(e: Throwable) {
        throw e
    }
}

open class RequestExecutorStub : IRequestExecutor {
    override fun executeGet(headers: Map<String, String>, queryParams: Map<String, String>): Pair<String, Map<String, String>> = Pair("", emptyMap())

    override fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String,String>): Pair<String, Map<String, String>> {
        return Pair("{\"id\":1, \"result\":\"test\"}", Collections.emptyMap<String, String>())
    }
}

object SubscriptionStub : Subscription {
    override fun unsubscribe() {}
}
