package clearnet

import clearnet.model.PostParams
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import java.util.concurrent.atomic.AtomicInteger

internal class CoreTask internal constructor(
        postParams: PostParams,
        private val onFinishAction: (() -> Unit)? = null
) : StaticTask(postParams) {
//    private val delivered = ReplaySubject.create<Result>().toSerialized()
    private val delivered = PublishSubject.create<Result>().toSerialized()
    private val deliveredObservable = delivered.replay().refCount().doOnSubscribe {
        synchronized(this) {
            if (disposables == null) {
                disposables = CompositeDisposable()
            }
            disposables!!.add(it)
        }
    }.doOnDispose {
        synchronized(this) {
            if (disposables != null) {
                disposables!!.dispose()
                disposables = null
            }
        }
    }
    private val activities = AtomicInteger()
    @Volatile
    private var disposables: CompositeDisposable? = null
//    internal fun observe(): Observable<Result> = delivered.hide()
    internal fun observe(): Observable<Result> = deliveredObservable

    fun deliver(result: StaticTask.Result) = delivered.onNext(result)

    internal fun promise(lastResult: Result?, workScheduler: Scheduler) = Promise(lastResult).apply {
        activities.incrementAndGet()
//        System.out.println("Increment activity on ${this@CoreTask}: ${activities.get()}")
        observe().doAfterTerminate {
            workScheduler.scheduleDirect {
                if(activities.decrementAndGet() == 0){
                    results.onComplete()
                    delivered.onComplete()
                    onFinishAction?.invoke()
                }
//                System.out.println("Decrement activity on ${this@CoreTask}: ${activities.get()}")
            }
        }.subscribe(results::onNext)    // only elements
    }
}