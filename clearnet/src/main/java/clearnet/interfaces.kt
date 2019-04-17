package clearnet.interfaces

import clearnet.InvocationBlockType
import clearnet.RPCRequest
import clearnet.StaticTask
import clearnet.error.ClearNetworkException
import clearnet.error.ConversionException
import clearnet.model.MergedInvocationStrategy
import clearnet.model.PostParams
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.internal.schedulers.ImmediateThinScheduler
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.lang.reflect.Type

interface ConversionStrategy {
    @Throws(JSONException::class, ConversionStrategyError::class)
    fun checkErrorOrResult(response: JSONObject): String?

    fun init(parameter: String){
        // nop
    }

    class ConversionStrategyError(val serializedError: String?, val errorType: Type) : Exception()

    object SmartConverter {
        @Throws(ClearNetworkException::class)
        fun convert(converter: ISerializer, body: String, type: Type, strategy: ConversionStrategy): Any? {
            return converter.deserialize(getStringResultOrThrow(converter, body, strategy), type)
        }

        @Throws(ClearNetworkException::class)
        fun getStringResultOrThrow(converter: ISerializer, body: String, strategy: ConversionStrategy): String? {
            try {
                return getStringResultOrThrow(converter, JSONObject(body), strategy)
            } catch (e: JSONException) {
                throw ConversionException(e)
            }
        }

        @Throws(ClearNetworkException::class)
        fun getStringResultOrThrow(converter: ISerializer, body: JSONObject, strategy: ConversionStrategy): String? {
            try {
                return strategy.checkErrorOrResult(body)
            } catch (e: JSONException) {
                throw ConversionException(e)
            } catch (e: ConversionStrategyError) {
                throw clearnet.error.ResponseErrorException(converter.deserialize(e.serializedError, e.errorType))
            }
        }
    }
}

/**
 * Validates fields of model instances which has been deserialized from the server response.
 * The validator must check required fields and can contains some additional checking rules.
 */
interface IBodyValidator {
    @Throws(clearnet.error.ValidationException::class)
    fun validate(body: Any?)
}

interface ICacheProvider {
    fun store(key: String, value: String, expiresAfter: Long)

    fun obtain(key: String): String?
}

/**
 * The upper level abstraction of [IRequestExecutor].
 * It should serialize the object, send the request and deserialize the response to the model.
 */
interface IConverterExecutor {
    fun executePost(postParams: PostParams): Observable<Any>
}

/**
 * Request executor which should just push data to server and return response as String
 */
interface IRequestExecutor {
    @Throws(IOException::class, ClearNetworkException::class)
    fun executeGet(headers: Map<String, String>, queryParams: Map<String, String> = emptyMap()): Pair<String, Map<String, String>>

    @Throws(IOException::class, ClearNetworkException::class)
    fun executePost(body: String, headers: Map<String, String>, queryParams: Map<String, String> = emptyMap()): Pair<String, Map<String, String>>
}

/**
 * Do not implement async execution for REST http requests.
 * Only use it for socket execution and another non-blocking protocols
 */
interface IAsyncRequestExecutor {
    fun getAsync(headers: Map<String, String>, queryParams: Map<String, String> = emptyMap()): Single<Pair<String, Map<String, String>>>

    fun postAsync(body: String, headers: Map<String, String>, queryParams: Map<String, String> = emptyMap(), bodyObject: RPCRequest? = null): Single<Pair<String, Map<String, String>>>
}

/**
 * Serializes and deserializes models to/from String
 */
interface ISerializer {
    @Throws(ConversionException::class)
    fun serialize(obj: Any?): String

    @Throws(ConversionException::class)
    fun deserialize(body: String?, objectType: Type): Any?
}

interface ISmartConverter {
    @Throws(ClearNetworkException::class)
    fun convert(body: String, type: Type, strategy: ConversionStrategy): Any?
}

interface ICallbackHolder {
    val scheduler: Scheduler
        get() = ImmediateThinScheduler.INSTANCE

    fun init()
    fun hold(disposable: Disposable)
    fun clear()

    @Deprecated("")
    fun <I> createEmpty(type: Class<in I>): I

    @Deprecated("")
    fun <I> wrap(source: I, interfaceType: Class<in I>): I
}

interface HeaderProvider {
    fun obtainHeadersList(): Map<String, String>
}

interface HeaderListener {
    fun onNewHeader(method: String, name: String, value: String)
}

interface HeaderObserver {
    fun register(method: String, listener: HeaderListener, vararg header: String): Subscription
}

interface ICallbackStorage {
    fun <T> observe(method: String): Observable<T>
}

@Deprecated("")
interface Subscription {
    fun unsubscribe()
}

interface IInvocationBlock {
    val invocationBlockType: InvocationBlockType
}

interface IInvocationSingleBlock: IInvocationBlock {
    fun onEntity(promise: StaticTask.Promise)
}

interface IInvocationBatchBlock: IInvocationBlock {
    val queueTimeThreshold: Long
        get() = 100L

    fun onQueueConsumed(promises: List<StaticTask.Promise>)
}

interface IInvocationSubjectBlock: IInvocationBlock {
    fun onEntity(promise: StaticTask.Promise)
}

interface TaskTimeTracker {
    fun onTaskFinished(invocationStrategy: MergedInvocationStrategy, method: String, time: Long)
}

interface IInvocationStrategy {
    val algorithm: Map<InvocationBlockType, Decision>
    val metaData: Map<String, String>

    class Decision(private val onResult: Array<InvocationBlockType>, private val onError: Array<InvocationBlockType> = emptyArray()) {
        constructor(onResult: InvocationBlockType) : this(arrayOf(onResult))
        constructor(onResult: InvocationBlockType, onError: InvocationBlockType) : this(arrayOf(onResult), arrayOf(onError))
        constructor(onResult: Array<InvocationBlockType>, onError: InvocationBlockType) : this(onResult, arrayOf(onError))

        operator fun get(hasResult: Boolean) = if (hasResult) onResult else onError
    }
}

interface IAsyncController {
    fun listenInput(): Observable<String>
    fun pushOutput(params: String): Single<Long>
}