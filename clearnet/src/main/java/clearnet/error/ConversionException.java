package clearnet.error;

public class ConversionException extends ClearNetworkException {
    {
        setKind(KIND.CONVERSION);
    }

    public ConversionException(Throwable cause){
        super(cause);
    }

    public ConversionException(String message, Throwable cause){
        super(message, cause);
    }
}
