package clearnet

import clearnet.error.UnknownExternalException
import clearnet.interfaces.*
import clearnet.model.PostParams
import io.reactivex.Observable
import io.reactivex.Scheduler
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
    private val flow: Map<InvocationBlockType, Subject<CoreTask>>
    private val taskStorage: MutableList<CoreTask> = CopyOnWriteArrayList()
    private val ioScheduler = Schedulers.from(ioExecutor)

    // todo support nulls
    private val collector = PublishSubject.create<Pair<CoreTask, StaticTask.Result>>().toSerialized()

    init {
        flow = blocks.associate { block ->
            val subject = PublishSubject.create<CoreTask>().toSerialized()

            when (block) {
                is IInvocationSingleBlock -> subject.subscribeImmediate(block)
                is IInvocationBatchBlock -> subject.subscribeWithTimeThreshold(block)
                else -> throw IllegalArgumentException("Unsupported block type ${block::class.java.name}")
            }

            block.invocationBlockType to subject
        }
    }


    override fun executePost(postParams: PostParams) {
        Observable.just(postParams.bindable).subscribeOn(worker).flatMap { bindable ->
            if (bindable) Observable.fromIterable(taskStorage)
            else Observable.empty()
        }.filter { taskItem ->
            taskItem.respond(postParams.requestTypeIdentifier, postParams.cacheKey)
        }.sorted(StaticTask.ResultsCountComparator).switchIfEmpty {
            val task = CoreTask(postParams)
            taskStorage += task
            placeToQueue(task, InvocationBlockType.INITIAL)
            task.observe().subscribe { collector.onNext(task to it) }
            it.onNext(task)
        }.take(1).flatMap {
            it.observe()
        }.flatMap {
            if (it is StaticTask.ErrorResult) Observable.error(it.error)
            else Observable.just((it as StaticTask.SuccessResult).result)
        }.observeOn(ioScheduler).subscribe(postParams.subject)
    }


    override fun <T> observe(method: String): Observable<T> {
        return collector.filter { (task, _) -> task.respond(method, null) }
                .map { it.second }
                .filter { it is StaticTask.SuccessResult }
                .map { (it as StaticTask.SuccessResult).result as T }
    }


    private fun placeToQueue(task: CoreTask, index: InvocationBlockType) {
        flow[index]!!.onNext(task)
    }

    private fun placeToQueues(from: InvocationBlockType?, task: CoreTask, indexes: Array<InvocationBlockType>) {
        task.move(from, indexes)
        indexes.forEach { placeToQueue(task, it) }
        if (task.isFinished()) {
            taskStorage.remove(task)
            timeTracker?.onTaskFinished(
                    task.postParams.invocationStrategy,
                    task.postParams.requestTypeIdentifier,
                    System.currentTimeMillis() - task.startTime
            )
        }
    }


    private fun handleTaskResult(block: IInvocationBlock, task: CoreTask, result: StaticTask.Result) {
        placeToQueues(block.invocationBlockType, task, result.nextIndexes)
    }

    private fun Observable<CoreTask>.subscribeImmediate(block: IInvocationSingleBlock) {
        this.observeOn(worker).subscribe { task ->
            val promise = task.promise().apply {
                observe().observeOn(Schedulers.trampoline()).subscribe { result ->
                    handleTaskResult(block, task, result)
                }
            }

            // todo need test this
            ioExecutor.execute {
                try {
                    block.onEntity(promise)
                } catch (e: Throwable) {
                    promise.setError(UnknownExternalException(e.message), block.invocationBlockType)
                }
            }
        }
    }

    private fun Observable<CoreTask>.subscribeWithTimeThreshold(block: IInvocationBatchBlock) {
        this.buffer(block.queueTimeThreshold, TimeUnit.MILLISECONDS, worker).filter {
            !it.isEmpty()
        }.subscribe { taskList ->
            val promises = taskList.map { task ->
                task.promise().apply {
                    observe().observeOn(Schedulers.trampoline()).subscribe { result ->
                        handleTaskResult(block, task, result)
                    }
                }
            }

            // todo need test this
            ioExecutor.execute {
                try {
                    block.onQueueConsumed(promises)
                } catch (e: Throwable) {
                    promises.forEach {
                        it.setError(UnknownExternalException(e.message), block.invocationBlockType)
                    }
                }
            }
        }
    }
}
