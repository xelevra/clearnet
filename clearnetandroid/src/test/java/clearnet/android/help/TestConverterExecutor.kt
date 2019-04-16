package clearnet.android.help

import clearnet.interfaces.IConverterExecutor
import clearnet.model.PostParams
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject

class TestConverterExecutor : IConverterExecutor {
    var lastParams: PostParams? = null
    val results: Subject<Any> = PublishSubject.create()

    override fun executePost(postParams: PostParams): Observable<Any> {
        lastParams = postParams
        return results
    }
}