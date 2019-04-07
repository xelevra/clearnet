package clearnet.error;

import java.io.IOException;

public class NetworkException extends ClearNetworkException {
    {
        setKind(KIND.NETWORK);
    }

    public NetworkException(IOException cause){
        super(cause);
    }
}
