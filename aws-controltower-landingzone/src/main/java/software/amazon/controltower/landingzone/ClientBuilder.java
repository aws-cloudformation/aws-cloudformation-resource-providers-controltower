package software.amazon.controltower.landingzone;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.controltower.ControlTowerClient;

public class ClientBuilder {
    private static final String CLOUDFORMATION_USER_AGENT =  "ct-cfn-landing-zone";

    public static ControlTowerClient getClient() {
        String region = System.getenv("AWS_REGION");
        return ControlTowerClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryPolicy(RetryMode.ADAPTIVE)
                        .putHeader("User-Agent", CLOUDFORMATION_USER_AGENT)
                        .build())
                .build();
    }
}
