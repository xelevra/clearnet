package clearnet.support

import clearnet.RPCRequest
import clearnet.interfaces.IAsyncRequestExecutor
import clearnet.interfaces.IRequestExecutor
import io.reactivex.Observable
import io.reactivex.Single

class CombinedRequestExecutor private constructor(
        private val syncExecutor: IRequestExecutor,
        private val asyncExecutor: IAsyncRequestExecutor,
        private val sourceIsSync: Boolean
) : IRequestExecutor by syncExecutor, IAsyncRequestExecutor by asyncExecutor {

    constructor(syncExecutor: IRequestExecutor) : this(syncExecutor, object : IAsyncRequestExecutor {
        override fun getAsync(headers: Map<String, String>, queryParams: Map<String, String>): Single<Pair<String, Map<String, String>>> {
            return Single.fromCallable {
                syncExecutor.executeGet(headers, queryParams)
            }
        }

        override fun postAsync(body: String, headers: Map<String, String>, queryParams: Map<String, String>, bodyObject: RPCRequest?): Single<Pair<String, Map<String, String>>> {
            return Single.fromCallable {
                syncExecutor.executePost(body, headers, queryParams)
            }
        }

        override fun observe(headers: Map<String, String>, queryParams: Map<String, String>): Observable<String> {
            return getAsync(headers, queryParams).map { it.first }.toObservable()
        }
    }, true)

    constructor(asyncExecutor: IAsyncRequestExecutor) : this(object : IRequestExecutor {
        override fun executeGet(headers: Map<String, String>, queryParams: Map<String, String>): Pair<String, Map<String, String>> {
            return asyncExecutor.getAsync(headers, queryParams).blockingGet()
        }

        override fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String, String>): Pair<String, Map<String, String>> {
            return asyncExecutor.postAsync(body, headers, queryParams, null).blockingGet()
        }
    }, asyncExecutor, false)

    /**
     * comparison by source executor reference
     */
    override fun equals(other: Any?): Boolean {
        return other is CombinedRequestExecutor &&
                (sourceIsSync && syncExecutor === other.syncExecutor || !sourceIsSync && asyncExecutor === other.asyncExecutor)
    }

    override fun hashCode(): Int {
        return 0
    }
}