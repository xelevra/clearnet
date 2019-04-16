package clearnet.help

import clearnet.interfaces.IConverterExecutor
import clearnet.model.PostParams
import io.reactivex.Observable

class TestConverterExecutor : IConverterExecutor {
    var lastParams: PostParams? = null

    override fun executePost(postParams: PostParams): Observable<Any> {
        lastParams = postParams
        return Observable.just(1)
    }
}