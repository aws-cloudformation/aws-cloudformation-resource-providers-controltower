package software.amazon.controltower.landingzone;

import java.time.Duration;
import java.util.Collections;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.controltower.ControlTowerClient;
import software.amazon.awssdk.services.controltower.model.AccessDeniedException;
import software.amazon.awssdk.services.controltower.model.InternalServerException;
import software.amazon.awssdk.services.controltower.model.LandingZoneSummary;
import software.amazon.awssdk.services.controltower.model.ListLandingZonesRequest;
import software.amazon.awssdk.services.controltower.model.ListLandingZonesResponse;
import software.amazon.awssdk.services.controltower.model.ThrottlingException;
import software.amazon.awssdk.services.controltower.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class ListHandlerTest extends AbstractTestBase {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<ControlTowerClient> proxyClient;

    @Mock
    private ControlTowerClient sdkClient;

    private final ListHandler handler = new ListHandler();
    private final ResourceModel model = ResourceModel.builder()
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .build();
    private final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .nextToken(NEXT_TOKEN)
            .build();

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(ControlTowerClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
    }

    @AfterEach
    public void tear_down() {
        verify(sdkClient, atLeastOnce()).listLandingZones(any(ListLandingZonesRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        LandingZoneSummary landingZoneSummary = LandingZoneSummary
                .builder()
                .arn(LANDING_ZONE_IDENTIFIER)
                .build();
        ListLandingZonesResponse listLandingZonesResponse = ListLandingZonesResponse
                .builder()
                .landingZones(Collections.singletonList(landingZoneSummary))
                .nextToken(NEXT_TOKEN)
                .build();

        when(proxyClient.client().listLandingZones(any(ListLandingZonesRequest.class))).thenReturn(listLandingZonesResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNotNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw")
    public void handleRequest_throwsException(Class<Exception> expectedException) {
        when(proxyClient.client().listLandingZones(any(ListLandingZonesRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isNotNull();
        assertThat(response.getErrorCode()).isEqualTo(EXCEPTION_TO_ERROR_CODE_MAP.get(expectedException));
    }

    private static Stream<Arguments> exception_to_throw() {
        return Stream.of(
                Arguments.of(AccessDeniedException.class),
                Arguments.of(ThrottlingException.class),
                Arguments.of(ValidationException.class),
                Arguments.of(InternalServerException.class)
        );
    }
}
