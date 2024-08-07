package software.amazon.controltower.landingzone;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import software.amazon.awssdk.services.controltower.model.ConflictException;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneOperationRequest;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneOperationResponse;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneResponse;
import software.amazon.awssdk.services.controltower.model.InternalServerException;
import software.amazon.awssdk.services.controltower.model.LandingZoneDetail;
import software.amazon.awssdk.services.controltower.model.LandingZoneDriftStatusSummary;
import software.amazon.awssdk.services.controltower.model.LandingZoneOperationDetail;
import software.amazon.awssdk.services.controltower.model.LandingZoneOperationStatus;
import software.amazon.awssdk.services.controltower.model.LandingZoneOperationType;
import software.amazon.awssdk.services.controltower.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.controltower.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.controltower.model.ResourceNotFoundException;
import software.amazon.awssdk.services.controltower.model.TagResourceRequest;
import software.amazon.awssdk.services.controltower.model.TagResourceResponse;
import software.amazon.awssdk.services.controltower.model.ThrottlingException;
import software.amazon.awssdk.services.controltower.model.UntagResourceRequest;
import software.amazon.awssdk.services.controltower.model.UntagResourceResponse;
import software.amazon.awssdk.services.controltower.model.UpdateLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.UpdateLandingZoneResponse;
import software.amazon.awssdk.services.controltower.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;


@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest extends AbstractTestBase {
    protected static final Constant TEST_UPDATE_BACKOFF_STRATEGY = Constant.of().timeout(Duration.ofSeconds(10L)).delay(Duration.ofSeconds(1L)).build();

    private static final List<Tag> PREVIOUS_TAGS = new ArrayList<Tag>(){{ add(Tag.builder().key("k1").value("v1").build()); }};
    private static final List<Tag> TAGS_WITH_ADDED_VALUES = new ArrayList<Tag>(){{
        add(Tag.builder().key("k1").value("v1").build());
        add(Tag.builder().key("k2").value("v2").build());
    }};

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<ControlTowerClient> proxyClient;

    @Mock
    private ControlTowerClient sdkClient;

    private final UpdateHandler handler = new UpdateHandler();
    private final UpdateHandler customHandlerToTestStabilization = new UpdateHandler(TEST_UPDATE_BACKOFF_STRATEGY);
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

    private final ResourceModel modelWithAddedTags = ResourceModel.builder()
            .manifest(MANIFEST)
            .arn(LANDING_ZONE_IDENTIFIER)
            .version(VERSION)
            .status(LANDING_ZONE_STATUS)
            .latestAvailableVersion(VERSION)
            .driftStatus(DRIFT_STATUS)
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .tags(TAGS_WITH_ADDED_VALUES)
            .build();

    private final ResourceModel previousModelChanged = ResourceModel.builder()
            .manifest(MANIFEST)
            .arn(LANDING_ZONE_IDENTIFIER)
            .version(VERSION)
            .status(LANDING_ZONE_STATUS)
            .latestAvailableVersion(VERSION)
            .driftStatus(DRIFT_STATUS)
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .tags(PREVIOUS_TAGS)
            .build();

    private final ResourceModel previousModelUnchanged = ResourceModel.builder()
            .manifest(MANIFEST)
            .arn(LANDING_ZONE_IDENTIFIER)
            .version(VERSION)
            .status(LANDING_ZONE_STATUS)
            .latestAvailableVersion(VERSION)
            .driftStatus(DRIFT_STATUS)
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .tags(TAGS)
            .build();

    private final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(previousModelUnchanged)
            .previousResourceTags(TagHelper.convertTags(TagHelper.convertTagObjects(PREVIOUS_TAGS)))
            .build();

    private final ResourceHandlerRequest<ResourceModel> requestWithUpdatedTags = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(modelWithAddedTags)
            .previousResourceState(previousModelChanged)
            .previousResourceTags(TagHelper.convertTags(TagHelper.convertTagObjects(PREVIOUS_TAGS)))
            .build();

    private final ResourceHandlerRequest<ResourceModel> requestWithChangedPreviousResourceTags = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(previousModelChanged)
            .previousResourceTags(TagHelper.convertTags(TagHelper.convertTagObjects(TAGS)))
            .build();

    private final ResourceHandlerRequest<ResourceModel> requestWithEmptyPreviousStateTags = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(previousModelUnchanged)
            .previousResourceTags(null)
            .build();

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(ControlTowerClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
    }

    @AfterEach
    public void tear_down() {
        verify(sdkClient, atLeastOnce()).updateLandingZone(any(UpdateLandingZoneRequest.class));
    }

    @Test
    public void handleRequest_SimpleSuccess() {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneResponse getLandingZoneResponse = buildGetLandingZoneResponse();
        when(proxyClient.client().getLandingZone(any(GetLandingZoneRequest.class))).thenReturn(getLandingZoneResponse);

        GetLandingZoneOperationResponse getLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.SUCCEEDED);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).thenReturn(getLandingZoneOperationResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponse();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        UntagResourceResponse untagResourceResponse = buildUntagResourceRequest();
        when(proxyClient.client().untagResource(any(UntagResourceRequest.class))).thenReturn(untagResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response);
        verify(sdkClient, atLeastOnce()).getLandingZone(any(GetLandingZoneRequest.class));
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(sdkClient, never()).tagResource(any(TagResourceRequest.class));
        verify(sdkClient, atLeastOnce()).untagResource(any(UntagResourceRequest.class));
    }

    @Test
    public void handleRequest_AddedTags() {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneResponse getLandingZoneResponse = buildGetLandingZoneResponse();
        when(proxyClient.client().getLandingZone(any(GetLandingZoneRequest.class))).thenReturn(getLandingZoneResponse);

        GetLandingZoneOperationResponse getLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.SUCCEEDED);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).thenReturn(getLandingZoneOperationResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponseForChangedTags();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        TagResourceResponse tagResourceResponse = buildTagResourceRequest();
        when(proxyClient.client().tagResource(any(TagResourceRequest.class))).thenReturn(tagResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, requestWithUpdatedTags, new CallbackContext(), proxyClient, logger);

        assertSuccess(response, requestWithUpdatedTags);
        verify(sdkClient, atLeastOnce()).getLandingZone(any(GetLandingZoneRequest.class));
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(sdkClient, atLeastOnce()).tagResource(any(TagResourceRequest.class));
        verify(sdkClient, never()).untagResource(any(UntagResourceRequest.class));
    }

    @Test
    public void handleRequest_withChangedPreviousResourceTags_SimpleSuccess() {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneResponse getLandingZoneResponse = buildGetLandingZoneResponse();
        when(proxyClient.client().getLandingZone(any(GetLandingZoneRequest.class))).thenReturn(getLandingZoneResponse);

        GetLandingZoneOperationResponse getLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.SUCCEEDED);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).thenReturn(getLandingZoneOperationResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponseForChangedTags();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, requestWithChangedPreviousResourceTags, new CallbackContext(), proxyClient, logger);

        assertSuccess(response);
        verify(sdkClient, atLeastOnce()).getLandingZone(any(GetLandingZoneRequest.class));
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }


    @Test
    public void handleRequest_withEmptyPreviousStateTags_SimpleSuccess() {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneResponse getLandingZoneResponse = buildGetLandingZoneResponse();
        when(proxyClient.client().getLandingZone(any(GetLandingZoneRequest.class))).thenReturn(getLandingZoneResponse);

        GetLandingZoneOperationResponse getLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.SUCCEEDED);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).thenReturn(getLandingZoneOperationResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponse();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, requestWithEmptyPreviousStateTags, new CallbackContext(), proxyClient, logger);

        assertSuccess(response);
        verify(sdkClient, atLeastOnce()).getLandingZone(any(GetLandingZoneRequest.class));
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void handleRequest_withStabilizationSuccess() {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneResponse getLandingZoneResponse = buildGetLandingZoneResponse();
        when(proxyClient.client().getLandingZone(any(GetLandingZoneRequest.class))).thenReturn(getLandingZoneResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponse();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        GetLandingZoneOperationResponse succeededGetLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.SUCCEEDED);
        GetLandingZoneOperationResponse inProgressGetLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.IN_PROGRESS);

        AtomicInteger attempt = new AtomicInteger(5);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).then((m) -> {
            switch (attempt.getAndDecrement()) {
                case 0:
                    return succeededGetLandingZoneOperationResponse;
                default:
                    return inProgressGetLandingZoneOperationResponse;
            }
        });

        final ProgressEvent<ResourceModel, CallbackContext> response = customHandlerToTestStabilization.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertSuccess(response);
        verify(sdkClient, atLeastOnce()).getLandingZone(any(GetLandingZoneRequest.class));
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
    }

    @Test
    public void handleRequest_withStabilization_failed() {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneOperationResponse failedGetLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.FAILED);
        GetLandingZoneOperationResponse inProgressGetLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.IN_PROGRESS);

        AtomicInteger attempt = new AtomicInteger(3);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).then((m) -> {
            switch (attempt.getAndDecrement()) {
                case 0:
                    return failedGetLandingZoneOperationResponse;
                default:
                    return inProgressGetLandingZoneOperationResponse;
            }
        });

        assertThatThrownBy(() -> customHandlerToTestStabilization.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger))
                .isInstanceOf(CfnNotStabilizedException.class);
    }

    @Test
    public void handleRequest_withStabilizationTimeout_failed() {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneOperationResponse inProgressGetLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.IN_PROGRESS);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).thenReturn(inProgressGetLandingZoneOperationResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = customHandlerToTestStabilization.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertFailed(response);
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw")
    public void handleRequest_updateLandingZoneThrowsException(Class<Exception> expectedException) {
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertFailed(response);
        assertThat(response.getErrorCode()).isEqualTo(EXCEPTION_TO_ERROR_CODE_MAP.get(expectedException));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw")
    public void handleRequest_getLandingZoneThrowsException(Class<Exception> expectedException) {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneOperationResponse getLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.SUCCEEDED);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).thenReturn(getLandingZoneOperationResponse);

        UntagResourceResponse untagResourceResponse = buildUntagResourceRequest();
        when(proxyClient.client().untagResource(any(UntagResourceRequest.class))).thenReturn(untagResourceResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponse();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        when(proxyClient.client().getLandingZone(any(GetLandingZoneRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertFailed(response);
        assertThat(response.getErrorCode()).isEqualTo(EXCEPTION_TO_ERROR_CODE_MAP.get(expectedException));
        verify(sdkClient, atLeastOnce()).getLandingZone(any(GetLandingZoneRequest.class));
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
        verify(sdkClient, never()).tagResource(any(TagResourceRequest.class));
        verify(sdkClient, atLeastOnce()).untagResource(any(UntagResourceRequest.class));
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw_for_tags")
    public void handleRequest_listTagsForResourceThrowsException(Class<Exception> expectedException) {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneOperationResponse getLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.SUCCEEDED);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).thenReturn(getLandingZoneOperationResponse);

        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertFailed(response);
        assertThat(response.getErrorCode()).isEqualTo(EXCEPTION_TO_ERROR_CODE_MAP.get(expectedException));
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw_for_tags")
    public void handleRequest_tagResourceThrowsException(Class<Exception> expectedException) {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneOperationResponse getLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.SUCCEEDED);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).thenReturn(getLandingZoneOperationResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponseForChangedTags();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        when(proxyClient.client().tagResource(any(TagResourceRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, requestWithUpdatedTags, new CallbackContext(), proxyClient, logger);

        assertFailed(response);
        assertThat(response.getErrorCode()).isEqualTo(EXCEPTION_TO_ERROR_CODE_MAP.get(expectedException));
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
        verify(sdkClient, atLeastOnce()).tagResource(any(TagResourceRequest.class));
        verify(sdkClient, never()).untagResource(any(UntagResourceRequest.class));
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw_for_tags")
    public void handleRequest_untagResourceThrowsException(Class<Exception> expectedException) {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        GetLandingZoneOperationResponse getLandingZoneOperationResponse = buildGetLandingZoneOperationResponse(LandingZoneOperationStatus.SUCCEEDED);
        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).thenReturn(getLandingZoneOperationResponse);

        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponse();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        when(proxyClient.client().untagResource(any(UntagResourceRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertFailed(response);
        assertThat(response.getErrorCode()).isEqualTo(EXCEPTION_TO_ERROR_CODE_MAP.get(expectedException));
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
        verify(sdkClient, atLeastOnce()).untagResource(any(UntagResourceRequest.class));
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw")
    public void handleRequest_getLandingZoneOperationThrowsException(Class<Exception> expectedException) {
        UpdateLandingZoneResponse updateLandingZoneResponse = buildUpdateLandingZoneResponse();
        when(proxyClient.client().updateLandingZone(any(UpdateLandingZoneRequest.class))).thenReturn(updateLandingZoneResponse);

        when(proxyClient.client().getLandingZoneOperation(any(GetLandingZoneOperationRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = handler.handleRequest(proxy, request, new CallbackContext(), proxyClient, logger);

        assertFailed(response);
        assertThat(response.getErrorCode()).isEqualTo(EXCEPTION_TO_ERROR_CODE_MAP.get(expectedException));
        verify(sdkClient, atLeastOnce()).getLandingZoneOperation(any(GetLandingZoneOperationRequest.class));
    }

    private static Stream<Arguments> exception_to_throw() {
        return Stream.of(
                Arguments.of(AccessDeniedException.class),
                Arguments.of(ThrottlingException.class),
                Arguments.of(ValidationException.class),
                Arguments.of(InternalServerException.class),
                Arguments.of(ResourceNotFoundException.class),
                Arguments.of(ConflictException.class)
        );
    }

    private static Stream<Arguments> exception_to_throw_for_tags() {
        return Stream.of(
                Arguments.of(ValidationException.class),
                Arguments.of(InternalServerException.class),
                Arguments.of(ResourceNotFoundException.class)
        );
    }

    private UpdateLandingZoneResponse buildUpdateLandingZoneResponse() {
        return UpdateLandingZoneResponse.builder()
                .operationIdentifier(OPERATION_IDENTIFIER)
                .build();
    }

    private GetLandingZoneOperationResponse buildGetLandingZoneOperationResponse(LandingZoneOperationStatus landingZoneOperationStatus) {
        LandingZoneOperationDetail inProgressLandingZoneOperationDetail = LandingZoneOperationDetail.builder()
                .operationType(LandingZoneOperationType.UPDATE)
                .status(landingZoneOperationStatus)
                .build();
        return GetLandingZoneOperationResponse.builder()
                .operationDetails(inProgressLandingZoneOperationDetail)
                .build();
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

    private ListTagsForResourceResponse buildListTagsForResourceResponseForChangedTags() {
        return ListTagsForResourceResponse.builder()
                .tags(TagHelper.convertTags(TagHelper.convertTagObjects(PREVIOUS_TAGS)))
                .build();
    }

    private TagResourceResponse buildTagResourceRequest() {
        return TagResourceResponse.builder().build();
    }

    private UntagResourceResponse buildUntagResourceRequest() {
        return UntagResourceResponse.builder().build();
    }

    private TagResourceResponse buildtagResourceResponse() {
        return TagResourceResponse.builder().build();
    }

    private void assertSuccess(ProgressEvent<ResourceModel, CallbackContext> response, ResourceHandlerRequest<ResourceModel> request) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
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
