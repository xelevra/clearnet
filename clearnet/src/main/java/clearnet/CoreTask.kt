package clearnet

import clearnet.model.PostParams
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class CoreTask internal constructor(
        postParams: PostParams
) : StaticTask(postParams) {
    private val delivered = PublishSubject.create<Result>().toSerialized()
    private val inQueues: MutableList<InvocationBlockType> = ArrayList(2)
    private val deliveredObservable = delivered.hide().replay().refCount()

    internal fun observe(): Observable<Result> = deliveredObservable

    fun deliver(result: StaticTask.Result) = delivered.onNext(result)

    // todo no queues
    fun move(from: InvocationBlockType?, to: Array<InvocationBlockType>) = synchronized(inQueues) {
        if (from != null) inQueues.remove(from)
        inQueues.addAll(to)
        if (inQueues.isEmpty()) results.onComplete()
    }

    internal fun promise() = Promise().apply {
        observe().subscribe(results::onNext)    // only elements
    }
}