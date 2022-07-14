package software.amazon.controltower.enabledcontrol;

import com.amazonaws.handlers.HandlerAfterAttemptContext;
import com.amazonaws.handlers.RequestHandler2;
import software.amazon.cloudformation.proxy.Logger;

public class RequestLoggingHandler extends RequestHandler2 {
    private final Logger logger;

    public RequestLoggingHandler(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void afterAttempt(HandlerAfterAttemptContext context) {
        if (context.getException() != null) {
            logger.log(String.format("Call failed. exception=%s", context.getException()));
        }
    }
}
