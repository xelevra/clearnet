package clearnet.android.help;

import annotations.RPCMethodScope;
import io.reactivex.Observable;

public interface TestRequests {

    @RPCMethodScope("test")
    Observable<Object> reactiveRequest();
}
