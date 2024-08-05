package software.amazon.controltower.landingzone;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import converters.DocumentConverter;
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
import software.amazon.awssdk.services.controltower.model.GetLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneResponse;
import software.amazon.awssdk.services.controltower.model.InternalServerException;
import software.amazon.awssdk.services.controltower.model.LandingZoneDetail;
import software.amazon.awssdk.services.controltower.model.LandingZoneDriftStatusSummary;
import software.amazon.awssdk.services.controltower.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.controltower.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.controltower.model.ResourceNotFoundException;
import software.amazon.awssdk.services.controltower.model.ThrottlingException;
import software.amazon.awssdk.services.controltower.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest extends AbstractTestBase {
    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<ControlTowerClient> proxyClient;

    @Mock
    private ControlTowerClient sdkClient;

    private final ReadHandler handler = new ReadHandler();

    private final ResourceModel model = ResourceModel.builder()
            .manifest(MANIFEST)
            .arn(LANDING_ZONE_IDENTIFIER)
            .version(VERSION)
            .status(LANDING_ZONE_STATUS)
            .latestAvailableVersion(VERSION)
            .driftStatus(DRIFT_STATUS)
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .tags(TAGS)
            .build();

    final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .build();

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(ControlTowerClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
    }

    @AfterEach
    public void tear_down() {
        verify(sdkClient, times(1)).getLandingZone(any(GetLandingZoneRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        GetLandingZoneResponse getLandingZoneResponse = buildGetLandingZoneResponse();
        when(proxyClient.client().getLandingZone(any(GetLandingZoneRequest.class))).thenReturn(getLandingZoneResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponse();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response);
        verify(sdkClient, atLeastOnce()).getLandingZone(any(GetLandingZoneRequest.class));
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw")
    public void handleRequest_getLandingZoneThrowsException(Class<Exception> expectedException) {
        when(proxyClient.client().getLandingZone(any(GetLandingZoneRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertFailed(response);
        assertThat(response.getErrorCode()).isEqualTo(EXCEPTION_TO_ERROR_CODE_MAP.get(expectedException));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw_for_tags")
    public void handleRequest_listTagsForResourceThrowsException(Class<Exception> expectedException) {
        GetLandingZoneResponse getLandingZoneResponse = buildGetLandingZoneResponse();
        when(proxyClient.client().getLandingZone(any(GetLandingZoneRequest.class))).thenReturn(getLandingZoneResponse);

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertFailed(response);
        assertThat(response.getErrorCode()).isEqualTo(EXCEPTION_TO_ERROR_CODE_MAP.get(expectedException));
        verify(sdkClient, atLeastOnce()).getLandingZone(any(GetLandingZoneRequest.class));
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    private static Stream<Arguments> exception_to_throw() {
        return Stream.of(
                Arguments.of(AccessDeniedException.class),
                Arguments.of(InternalServerException.class),
                Arguments.of(ThrottlingException.class),
                Arguments.of(ValidationException.class),
                Arguments.of(ResourceNotFoundException.class)
        );
    }

    private static Stream<Arguments> exception_to_throw_for_tags() {
        return Stream.of(
                Arguments.of(ValidationException.class),
                Arguments.of(InternalServerException.class),
                Arguments.of(ResourceNotFoundException.class)
        );
    }

    private GetLandingZoneResponse buildGetLandingZoneResponse() {
        final DocumentConverter converter = new DocumentConverter();
        LandingZoneDriftStatusSummary landingZoneDriftStatusSummary = LandingZoneDriftStatusSummary.builder().status(LANDING_ZONE_STATUS).build();
        LandingZoneDetail landingZoneDetail = LandingZoneDetail.builder()
                .manifest(converter.toDocument(model.getManifest()))
                .version(VERSION)
                .arn(LANDING_ZONE_IDENTIFIER)
                .latestAvailableVersion(VERSION)
                .driftStatus(landingZoneDriftStatusSummary)
                .status(LANDING_ZONE_STATUS)
                .build();
        return GetLandingZoneResponse.builder()
                .landingZone(landingZoneDetail)
                .build();
    }

    private ListTagsForResourceResponse buildListTagsForResourceResponse() {
        return ListTagsForResourceResponse.builder()
                .tags(TagHelper.convertTags(TagHelper.convertTagObjects(TAGS)))
                .build();
    }

    private void assertSuccess(ProgressEvent<ResourceModel, CallbackContext> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    private void assertFailed(ProgressEvent<ResourceModel, CallbackContext> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isNotNull();
    }
}
