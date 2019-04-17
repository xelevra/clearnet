package clearnet

import clearnet.model.PostParams
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.subjects.ReplaySubject
import java.util.concurrent.atomic.AtomicInteger

class CoreTask internal constructor(
        postParams: PostParams
) : StaticTask(postParams) {
    private val delivered = ReplaySubject.create<Result>().toSerialized()
//    private val delivered = PublishSubject.create<Result>().toSerialized()
    private val activities = AtomicInteger()
//    private val deliveredObservable = delivered.hide().replay().refCount()

    internal fun observe(): Observable<Result> = delivered.hide()
//    internal fun observe(): Observable<Result> = deliveredObservable

    fun deliver(result: StaticTask.Result) = delivered.onNext(result)

    internal fun promise(workScheduler: Scheduler) = Promise().apply {
        activities.incrementAndGet()
//        System.out.println("Increment activity on ${this@CoreTask}: ${activities.get()}")
        observe().doAfterTerminate {
            workScheduler.scheduleDirect {
                if(activities.decrementAndGet() == 0){
                    results.onComplete()
                    delivered.onComplete()
                }
//                System.out.println("Decrement activity on ${this@CoreTask}: ${activities.get()}")
            }
        }.subscribe(results::onNext)    // only elements
    }
}