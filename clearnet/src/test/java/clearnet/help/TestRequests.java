package clearnet.help;

import clearnet.annotations.Body;
import clearnet.annotations.NotBindable;
import clearnet.annotations.Parameter;
import clearnet.annotations.RPCMethod;
import clearnet.annotations.RPCMethodScope;
import clearnet.annotations.InvocationStrategy;
import clearnet.annotations.NoBatch;
import io.reactivex.Observable;

import static clearnet.InvocationStrategy.AUTHORIZED_REQUEST;
import static clearnet.InvocationStrategy.PRIORITY_CACHE;
import static clearnet.InvocationStrategy.PRIORITY_REQUEST;
import static clearnet.InvocationStrategy.RETRY_IF_NO_NETWORK;

public interface TestRequests {

    // ---- RPCRequestBuildingTest ----

    @RPCMethodScope("testScope")
    void test();

    @RPCMethod("testScope.test")
    void test2();

    void withoutRPCAnnotation();

    @RPCMethodScope("test")
    void testParams(@Parameter("p1") String p1, @Parameter("p2") int p2, @Parameter("p3") int[] p3);

    @RPCMethodScope("test")
    void testBody(@Body int[] values);

    @RPCMethodScope("test")
    void multipleBody(@Body String p1, @Body String p2);

    @RPCMethodScope("test")
    void unknownArgs(String arg);

    @RPCMethodScope("test")
    void paramsAndBodyMixing(@Parameter("p1") int p1, @Body String p2);

    @RPCMethodScope("test")
    void paramsAndBodyMixingOnSingleArgument(@Parameter("p1") @Body int p1);

    // ---- BatchRequestTest ----

    @NotBindable
    @RPCMethodScope("test")
    Observable<String> firstOfBatch();

    @RPCMethodScope("test")
    Observable<String>  secondOfBatch();

    @RPCMethodScope("test")
    @InvocationStrategy(PRIORITY_CACHE)
    Observable<String> forBatchWithPriorityCache();

    @NotBindable
    @NoBatch
    @RPCMethodScope("test")
    Observable<String> batchNoBatch();

    // ---- CacheStrategyTest ----

    @RPCMethodScope("test")
    Observable<String> noCache();

    @InvocationStrategy(PRIORITY_REQUEST)
    @RPCMethodScope("test")
    Observable<String> priorityRequest();

    @InvocationStrategy(PRIORITY_CACHE)
    @RPCMethodScope("test")
    Observable<String> priorityCache();



    // ---- SuccessOrErrorResponsesVariantsTest ----
    @RPCMethodScope("test")
    Observable<TestObject> commonResponse();


    // ---- TasksSubscriptionTest ----
    @RPCMethodScope("test")
    Observable<String> bindableTask(@Parameter("param") int parameter);

    @NotBindable
    @RPCMethodScope("test")
    Observable<String> notBindableTask();

    @RPCMethodScope("test")
    @InvocationStrategy(PRIORITY_REQUEST)
    Observable<String> withCacheBindableTask();

    @RPCMethodScope("test")
    @InvocationStrategy({PRIORITY_CACHE, AUTHORIZED_REQUEST, RETRY_IF_NO_NETWORK})
    void mergeStrategiesTest();

    // ---- Reactive ----
    @RPCMethodScope("test")
    Observable<String> reactiveRequest(@Parameter("p1") int p1);
}
