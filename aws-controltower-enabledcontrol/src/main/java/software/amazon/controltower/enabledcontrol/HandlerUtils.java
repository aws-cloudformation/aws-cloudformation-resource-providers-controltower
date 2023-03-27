package software.amazon.controltower.enabledcontrol;

import software.amazon.cloudformation.proxy.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HandlerUtils {
    public static void logException(Throwable e, Logger logger) {
        final String stackTrace = Stream.of(e.getStackTrace())
                .map(StackTraceElement::toString)
                .collect(Collectors.joining("\n"));
        logger.log(String.format("Unhandled exception: %s\n%s", e, stackTrace));
    }
}
