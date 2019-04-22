package clearnet.help

import clearnet.interfaces.IAsyncController
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.internal.schedulers.ImmediateThinScheduler
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import io.reactivex.subjects.Subject

class TestAsyncController: IAsyncController {
    val subject: Subject<String> = PublishSubject.create()
    var subscribers = 0
    val pushes = ReplaySubject.create<String>()

    override fun listenInput(): Observable<String> {
        return subject.doOnSubscribe {
            subscribers++
        }.doOnDispose {
            subscribers--
        }.subscribeOn(ImmediateThinScheduler.INSTANCE)
    }

    override fun pushOutput(params: String): Single<Long> {
        return Single.just(params).doOnSuccess(pushes::onNext).map { 1L }
    }
}