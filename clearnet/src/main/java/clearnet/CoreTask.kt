package clearnet

import clearnet.model.PostParams
import io.reactivex.subjects.ReplaySubject
import java.util.*

class CoreTask internal constructor(
        postParams: PostParams
) : StaticTask(postParams) {
    private val delivered = ReplaySubject.create<Result>().toSerialized()
    private val inQueues: MutableList<InvocationBlockType> = ArrayList(2)

    internal fun observe() = delivered.hide()

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