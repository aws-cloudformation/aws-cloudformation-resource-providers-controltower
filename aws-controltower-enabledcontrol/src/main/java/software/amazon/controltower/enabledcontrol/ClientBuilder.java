package software.amazon.controltower.enabledcontrol;

import com.amazonaws.services.controltower.AWSControlTower;
import com.amazonaws.services.controltower.AWSControlTowerClientBuilder;
import software.amazon.cloudformation.proxy.Logger;

public class ClientBuilder {

    public static AWSControlTower getStandardClient(Logger logger) {
        String region = System.getenv("AWS_REGION");
        return AWSControlTowerClientBuilder.standard()
                .withRegion(region)
                .withRequestHandlers(new RequestLoggingHandler(logger))
                .build();
    }
}
