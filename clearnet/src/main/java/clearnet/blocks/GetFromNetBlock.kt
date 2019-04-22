package clearnet.blocks

import clearnet.*
import clearnet.error.*
import clearnet.interfaces.*
import clearnet.interfaces.ConversionStrategy.SmartConverter
import io.reactivex.Single
import io.reactivex.internal.schedulers.ImmediateThinScheduler
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.NoSuchElementException

class GetFromNetBlock(
        private val validator: IBodyValidator,
        private val converter: ISerializer
) : IInvocationBatchBlock {
    override val invocationBlockType = InvocationBlockType.GET_FROM_NET

    private val headersObserver = SimpleHeadersObserver()

    fun getHeadersObserver(): HeaderObserver = headersObserver

    override fun onQueueConsumed(promises: List<StaticTask.Promise>) {
        when {
            promises.isEmpty() -> return
            promises.size == 1 -> obtainFromNet(promises[0])
            checkExecutors(promises) -> groupByBatchSize(promises)
            else -> {
                val runningTasks = ArrayList<StaticTask.Promise>()
                promises.forEach {
                    if (it.taskRef.postParams.requestExecutor == promises[0].taskRef.postParams.requestExecutor) runningTasks.add(it)
                    else it.setNextIndex(InvocationBlockType.GET_FROM_NET)
                }

                if (runningTasks.size == 1) {
                    obtainFromNet(runningTasks[0])
                } else {
                    groupByBatchSize(runningTasks)
                }
            }
        }
    }

    private fun checkExecutors(promises: List<StaticTask.Promise>): Boolean {
        return (1 until promises.size).none { promises[it - 1].taskRef.postParams.requestExecutor != promises[it].taskRef.postParams.requestExecutor }
    }

    private fun obtainFromNet(promise: StaticTask.Promise) = with(promise.taskRef.postParams) {
        if (httpRequestType == "POST") {
            requestExecutor.postAsync(promise.taskRef.requestKey, headers, requestParams, requestBody as? RPCRequest)
        } else {
            requestExecutor.getAsync(requestParams, headers)
        }.subscribeOn(ImmediateThinScheduler.INSTANCE).onErrorResumeNext { error ->
            val mappedError = when (error) {
                is IOException -> NetworkException(error)
                is ClearNetworkException -> error
                else -> UnknownExternalException(error)
            }
            Single.error(mappedError)
        }.map { (responseString, headers) ->
            headersObserver.propagateHeaders(requestTypeIdentifier, headers)
            val stringResult = SmartConverter.getStringResultOrThrow(converter, responseString, conversionStrategy)
            val result = converter.deserialize(stringResult, resultType)
            validator.validate(result)

            result to stringResult
        }.subscribe({ (result, stringResult) ->
            promise.setResult(result, stringResult, invocationBlockType)
        }, {
            promise.setError(it as ClearNetworkException, invocationBlockType)
        })
    }

    private fun groupByBatchSize(promises: List<StaticTask.Promise>) {
        val executingList = mutableListOf<StaticTask.Promise>()
        var max = promises[0].taskRef.postParams.maxBatchSize

        promises.forEach {
            if (executingList.size < max && it.taskRef.postParams.maxBatchSize > executingList.size) {
                executingList += it
                if (it.taskRef.postParams.maxBatchSize < max) max = it.taskRef.postParams.maxBatchSize
            } else {
                it.setNextIndex(InvocationBlockType.GET_FROM_NET)
            }
        }

        trimAndExecuteOnSingleExecutor(executingList)
    }

    private fun trimAndExecuteOnSingleExecutor(promises: List<StaticTask.Promise>) {
        val maxBatchSize = promises[0].taskRef.postParams.maxBatchSize
        if (promises.size > maxBatchSize) {
            val runningList = promises.subList(0, maxBatchSize)
            val overflowList = promises.subList(maxBatchSize, promises.size)
            overflowList.forEach { it.setNextIndex(InvocationBlockType.GET_FROM_NET) }
            executeSequenceOnSingleExecutor(runningList)
        } else {
            executeSequenceOnSingleExecutor(promises)
        }
    }

    private fun executeSequenceOnSingleExecutor(promises: List<StaticTask.Promise>) {
        if (promises.size == 1) { // in case of maxBatchSize == 1
            obtainFromNet(promises[0])
            return
        }
        try {
            val result: String
            val tasksToWork = promises.toMutableList()
            try {
                val conflictedHeadersTasks = mutableListOf<StaticTask.Promise>()
                val combinedHeaders = combineHeaders(promises, conflictedHeadersTasks)
                conflictedHeadersTasks.forEach { it ->
                    it.setNextIndex(InvocationBlockType.GET_FROM_NET)
                }
                tasksToWork.removeAll(conflictedHeadersTasks)

                val responseWithHeaders = tasksToWork[0].taskRef.postParams.requestExecutor.executePost(
                        createBatchString(tasksToWork),
                        combinedHeaders,
                        mapOf("applicationMethod" to combineRpcMethods(promises))
                )
                result = responseWithHeaders.first
                tasksToWork.forEach { headersObserver.propagateHeaders(it.taskRef.getRequestIdentifier(), responseWithHeaders.second) }
            } catch (e: IOException) {
                throw NetworkException(e)
            }
            getRequestResponseList(tasksToWork, result).forEach {
                try {
                    val stringResult = SmartConverter.getStringResultOrThrow(converter, it.second, it.first.taskRef.postParams.conversionStrategy)
                    val convertedResult = converter.deserialize(stringResult, it.first.taskRef.postParams.resultType)

                    validator.validate(convertedResult)
                    it.first.setResult(convertedResult, stringResult, invocationBlockType)
                } catch (e: ClearNetworkException) {
                    it.first.setError(e, invocationBlockType)
                }
            }
        } catch (e: ClearNetworkException) {
            promises.forEach { task ->
                task.setError(e, invocationBlockType)
            }
        }
    }

    private fun combineHeaders(
            promises: List<StaticTask.Promise>,
            conflictedHeadersTasks: MutableList<StaticTask.Promise>
    ): Map<String, String> = mutableMapOf<String, String>().apply {
        promises.forEach { promise ->
            promise.taskRef.postParams.headers.entries.forEach {
                if (it.key in keys && this[it.key] != it.value) {
                    conflictedHeadersTasks.add(promise)
                } else {
                    this[it.key] = it.value
                }
            }
        }
    }

    @Deprecated("")
    // todo move this logic to Post params
    // It's difficult because it uses strange protocol with comma instead of HTTP params array
    private fun combineRpcMethods(promises: List<StaticTask.Promise>) = promises.joinToString(",") { it.taskRef.getRequestIdentifier() }

    @Throws(ConversionException::class)
    private fun createBatchString(promises: List<StaticTask.Promise>): String {
        return converter.serialize(promises.map { it.taskRef.postParams.requestBody })
    }

    @Throws(ConversionException::class)
    private fun getRequestResponseList(promises: List<StaticTask.Promise>, source: String): List<Pair<StaticTask.Promise, JSONObject>> {
        try {
            val array = JSONArray(source)
            return (0 until array.length())
                    .map { array.getJSONObject(it) }
                    .map { getTaskPromiseById(promises, it.getLong("id")) to it }
        } catch (e: JSONException) {
            throw ConversionException("Incorrect batch response: $source", e)
        }

    }

    @Throws(ConversionException::class)
    private fun getTaskPromiseById(promises: List<StaticTask.Promise>, id: Long): StaticTask.Promise {
        try {
            return promises.first {
                // todo remove manual casting
                (it.taskRef.postParams.requestBody as RPCRequest).id == id
            }
        } catch (e: NoSuchElementException) {
            throw ConversionException("Responses ids not comparable with requests ids", e)
        }
    }
}