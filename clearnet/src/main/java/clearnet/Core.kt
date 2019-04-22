package clearnet

import clearnet.error.UnknownExternalException
import clearnet.interfaces.*
import clearnet.model.PostParams
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.lang.IllegalArgumentException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Default realization of [IConverterExecutor] with the validation models feature
 */
class Core(
        private val ioExecutor: Executor,
        private val worker: Scheduler = Schedulers.single(),
        private val timeTracker: TaskTimeTracker? = null,
        vararg blocks: IInvocationBlock
) : IConverterExecutor, ICallbackStorage {
    private val flow: Map<InvocationBlockType, Subject<StaticTask.Promise>>
    private val taskStorage: MutableList<CoreTask> = CopyOnWriteArrayList()

    // todo support nulls
    private val collector = PublishSubject.create<Pair<CoreTask, StaticTask.Result>>().toSerialized()
    private val unknownErrors = PublishSubject.create<UnknownExternalException>().toSerialized()

    init {
        flow = blocks.associate { block ->
            val subject = PublishSubject.create<StaticTask.Promise>().toSerialized()

            when (block) {
                is IInvocationSingleBlock -> subject.subscribeImmediate(block)
                is IInvocationBatchBlock -> subject.subscribeWithTimeThreshold(block)
                is IInvocationSubjectBlock -> subject.subscribeSubjectBlock(block)
                else -> throw IllegalArgumentException("Unsupported block type ${block::class.java.name}")
            }

            block.invocationBlockType to subject
        }

        collector.filter { (_, result) ->
            result is StaticTask.ErrorResult && result.error is UnknownExternalException
        }.map { (_, result) ->
            (result as StaticTask.ErrorResult).error as UnknownExternalException
        }.subscribe(unknownErrors)
    }


    override fun executePost(postParams: PostParams): Observable<Any> {
        return Observable.just(postParams.bindable).subscribeOn(worker).flatMap { bindable ->
            if (bindable) Observable.fromIterable(taskStorage)
            else Observable.empty()
        }.filter { taskItem ->
            taskItem.respond(postParams.requestTypeIdentifier, postParams.cacheKey)
        }.sorted(StaticTask.ResultsCountComparator).map {
            it.observe()
        }.switchIfEmpty {
            createNewTask(postParams).let { task ->
                it.onNext(task.observe().compose(taskObservationActions(task)))
            }
        }.take(1).flatMap {
            it
        }.flatMap {
            if (it is StaticTask.ErrorResult) Observable.error(it.error)
            else Observable.just((it as StaticTask.SuccessResult).result)
        }
    }


    override fun <T> observe(method: String): Observable<T> {
        return collector.filter { (task, _) -> task.respond(method, null) }
                .map { it.second }
                .filter { it is StaticTask.SuccessResult }
                .map { (it as StaticTask.SuccessResult).result as T }
    }

    private fun createNewTask(postParams: PostParams) =  CoreTask(postParams).also {
        taskStorage += it
    }

    private fun taskObservationActions(task: CoreTask) = ObservableTransformer<StaticTask.Result, StaticTask.Result> { upstream ->
        upstream.doOnNext {
            collector.onNext(task to it)
        }.doOnSubscribe {
            placeToQueue(task, null, InvocationBlockType.INITIAL)
        }.doOnTerminate {
            taskStorage.remove(task)
            timeTracker?.onTaskFinished(
                    task.postParams.invocationStrategy,
                    task.postParams.requestTypeIdentifier,
                    System.currentTimeMillis() - task.startTime
            )
        }
    }

    private fun placeToQueue(task: CoreTask, lastResult: StaticTask.Result?, index: InvocationBlockType) {
        flow[index]!!.onNext(task.promise(lastResult, worker))
    }

    private fun Observable<StaticTask.Promise>.subscribeImmediate(block: IInvocationSingleBlock): Disposable {
        return this.observeOn(worker).subscribe { promise ->
            System.out.println("Block ${block.invocationBlockType} received task ${promise.taskRef}")
            promise.observe().firstElement().observeOn(Schedulers.trampoline()).subscribe { result ->
                handleTaskResult(promise, result)
            }

            // todo need test this
            ioExecutor.execute {
                try {
                    block.onEntity(promise)
                } catch (e: Throwable) {
                    promise.setError(UnknownExternalException(e), block.invocationBlockType)
                }
            }
        }
    }

    private fun Observable<StaticTask.Promise>.subscribeWithTimeThreshold(block: IInvocationBatchBlock): Disposable {
        return this.buffer(block.queueTimeThreshold, TimeUnit.MILLISECONDS, worker).filter {
            !it.isEmpty()
        }.subscribe { promises ->
//            System.out.println("Block ${block.invocationBlockType} received tasks list ${promises.joinToString(", "){ it.taskRef.toString() }}")
            promises.forEach { promise ->
                promise.observe().firstElement().observeOn(Schedulers.trampoline()).subscribe { result ->
                    handleTaskResult(promise, result)
                }
            }

            // todo need test this
            ioExecutor.execute {
                try {
                    block.onQueueConsumed(promises)
                } catch (e: Throwable) {
                    promises.forEach {
                        it.setError(UnknownExternalException(e), block.invocationBlockType)
                    }
                }
            }
        }
    }

    private fun Observable<StaticTask.Promise>.subscribeSubjectBlock(block: IInvocationSubjectBlock): Disposable {
        return this.observeOn(worker).subscribe { promise ->
            promise.observe().observeOn(Schedulers.trampoline()).subscribe { result ->
                handleTaskResult(promise, result)
            }

            // todo need test this
            ioExecutor.execute {
                try {
                    block.onEntity(promise)
                } catch (e: Throwable) {
                    promise.setError(UnknownExternalException(e), block.invocationBlockType)
                }
            }
        }
    }

    private fun handleTaskResult(promise: StaticTask.Promise, result: StaticTask.Result) {
        System.out.println("Handle ${promise.taskRef} result")
        val materialResult = if(result.isAncillary) promise.lastResult else result
        result.nextIndexes.forEach { placeToQueue(promise.taskRef as CoreTask, materialResult, it) }
    }
}
