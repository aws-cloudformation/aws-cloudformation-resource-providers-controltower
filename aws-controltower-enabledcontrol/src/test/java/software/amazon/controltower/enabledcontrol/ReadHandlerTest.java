package software.amazon.controltower.enabledcontrol;

import com.amazonaws.services.controltower.AWSControlTower;
import com.amazonaws.services.controltower.model.AccessDeniedException;
import com.amazonaws.services.controltower.model.EnabledControlSummary;
import com.amazonaws.services.controltower.model.ListEnabledControlsRequest;
import com.amazonaws.services.controltower.model.ListEnabledControlsResult;
import com.amazonaws.services.controltower.model.ResourceNotFoundException;
import com.amazonaws.services.controltower.model.ThrottlingException;
import com.amazonaws.services.controltower.model.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import java.util.Collections;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class ReadHandlerTest {

    private static final String TEST_CONTROL_NAME_1 = "AWS-GR_TEST_1";
    private static final String TEST_CONTROL_NAME_2 = "AWS-GR_TEST_2";
    private static final String TEST_CONTROL_IDENTIFIER_1 = "arn:aws:controltower:us-east-1::control/" + TEST_CONTROL_NAME_1;
    private static final String TEST_CONTROL_IDENTIFIER_2 = "arn:aws:controltower:us-east-1::control/" + TEST_CONTROL_NAME_2;
    private static final String TEST_TARGET_IDENTIFIER = "arn:aws:organizations::123456789012:ou/o-test-org/ou-test-ouid";
    private static final String TEST_NEXT_TOKEN = "1234567890";
    private static final EnvironmentVariables environmentVariables = new EnvironmentVariables("AWS_REGION", "us-east-1");

    @Mock
    private static AWSControlTower controlTowerClient;
    @Mock
    private AmazonWebServicesClientProxy proxy;
    @Mock
    private Logger logger;

    @BeforeEach
    public void setup() throws Exception {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
        controlTowerClient = mock(AWSControlTower.class);
        environmentVariables.setup();
    }

    @Test
    public void handleRequest_EnableControl_Success() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder().build();
        model.setControlIdentifier(TEST_CONTROL_IDENTIFIER_1);
        model.setTargetIdentifier(TEST_TARGET_IDENTIFIER);

        EnabledControlSummary controlSummary = new EnabledControlSummary().withControlIdentifier(TEST_CONTROL_IDENTIFIER_1);
        ListEnabledControlsResult ListEnabledControlsResult = new ListEnabledControlsResult()
                .withEnabledControls(Collections.singletonList(controlSummary));

        doReturn(ListEnabledControlsResult).when(proxy).injectCredentialsAndInvoke(any(ListEnabledControlsRequest.class), ArgumentMatchers.<Function<ListEnabledControlsRequest, ListEnabledControlsResult>>any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_EnableControl_Failed() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder().build();
        model.setControlIdentifier(TEST_CONTROL_IDENTIFIER_2);
        model.setTargetIdentifier(TEST_TARGET_IDENTIFIER);

        EnabledControlSummary controlSummary = new EnabledControlSummary().withControlIdentifier(TEST_CONTROL_IDENTIFIER_1);
        ListEnabledControlsResult ListEnabledControlsResult = new ListEnabledControlsResult()
                .withEnabledControls(Collections.singletonList(controlSummary));

        doReturn(ListEnabledControlsResult).when(proxy).injectCredentialsAndInvoke(any(ListEnabledControlsRequest.class), ArgumentMatchers.<Function<ListEnabledControlsRequest, ListEnabledControlsResult>>any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.NotFound);
    }

    @Test
    public void handleRequest_PaginatedResult_Success() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder().build();
        model.setControlIdentifier(TEST_CONTROL_IDENTIFIER_2);
        model.setTargetIdentifier(TEST_TARGET_IDENTIFIER);

        EnabledControlSummary controlSummary1 = new EnabledControlSummary().withControlIdentifier(TEST_CONTROL_IDENTIFIER_1);
        EnabledControlSummary controlSummary2 = new EnabledControlSummary().withControlIdentifier(TEST_CONTROL_IDENTIFIER_2);

        ListEnabledControlsResult ListEnabledControlsResultWithNextToken = new ListEnabledControlsResult()
                .withEnabledControls(Collections.singletonList(controlSummary1))
                .withNextToken(TEST_NEXT_TOKEN);

        ListEnabledControlsResult ListEnabledControlsResult = new ListEnabledControlsResult()
                .withEnabledControls(Collections.singletonList(controlSummary2));

        doReturn(ListEnabledControlsResultWithNextToken, ListEnabledControlsResult).when(proxy).injectCredentialsAndInvoke(any(ListEnabledControlsRequest.class), ArgumentMatchers.<Function<ListEnabledControlsRequest, ListEnabledControlsResult>>any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testExceptionMapping() {
        final ReadHandler handler = new ReadHandler();

        final ResourceModel model = ResourceModel.builder().build();
        model.setControlIdentifier(TEST_CONTROL_IDENTIFIER_1);
        model.setTargetIdentifier(TEST_TARGET_IDENTIFIER);

        String errorMessage = "Error";

        doThrow(new AccessDeniedException(errorMessage),
                new ThrottlingException(errorMessage),
                new ValidationException(errorMessage),
                new ResourceNotFoundException(errorMessage),
                new RuntimeException(errorMessage))
                .when(proxy).injectCredentialsAndInvoke(any(ListEnabledControlsRequest.class), ArgumentMatchers.<Function<ListEnabledControlsRequest, ListEnabledControlsResult>>any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        assertThrows(CfnAccessDeniedException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnThrottlingException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnInternalFailureException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

}
