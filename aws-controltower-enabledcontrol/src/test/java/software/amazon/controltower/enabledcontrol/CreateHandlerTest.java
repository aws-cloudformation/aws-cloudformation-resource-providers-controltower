package software.amazon.controltower.enabledcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.amazonaws.services.controltower.AWSControlTower;
import com.amazonaws.services.controltower.model.AccessDeniedException;
import com.amazonaws.services.controltower.model.ConflictException;
import com.amazonaws.services.controltower.model.ControlOperation;
import com.amazonaws.services.controltower.model.ControlOperationStatus;
import com.amazonaws.services.controltower.model.EnableControlRequest;
import com.amazonaws.services.controltower.model.EnableControlResult;
import com.amazonaws.services.controltower.model.EnabledControlSummary;
import com.amazonaws.services.controltower.model.GetControlOperationRequest;
import com.amazonaws.services.controltower.model.GetControlOperationResult;
import com.amazonaws.services.controltower.model.ListEnabledControlsRequest;
import com.amazonaws.services.controltower.model.ListEnabledControlsResult;
import com.amazonaws.services.controltower.model.ResourceNotFoundException;
import com.amazonaws.services.controltower.model.ServiceQuotaExceededException;
import com.amazonaws.services.controltower.model.ThrottlingException;
import com.amazonaws.services.controltower.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNetworkFailureException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

@ExtendWith(MockitoExtension.class)
public class CreateHandlerTest {

    public static final String TEST_GR = "AWS-GR_TEST_GUARDRAIL";
    public static final String TEST_GR_1 = "AWS-GR_TEST_GUARDRAIL_1";
    public static final String TEST_OUID = "ou-test-stpcyh2h";
    public static final String TEST_OPERATION_ID = "3e10c87d-44c5-746d-0207-843c3ce5734b";
    private static final String EXPECTED_TIMEOUT_MESSAGE = "Timed out waiting for enable control operation to complete.";
    private static final String EXPECTED_FAILURE_MESSAGE = "Enable guardrail operation failed";
    private static final String HTTP_TIMEOUT_EXCEPTION_MESSAGE = "HttpTimeoutException";
    private static final String ERROR = "Error";
    private static final String ALREADY_EXISTS = "already enabled on organizational unit";
    private static final String EXPECTED_INTERNAL_ERROR_MESSAGE = "AWS Control Tower could not enable the control due to an internal error.";
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
    public void handleRequest_CallbackContextWithSucceededOperation_Success() {
        // Setup
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                                    .desiredResourceState(model)
                                                                                    .build();

        final ControlOperation controlOperation = new ControlOperation()
                .withStatus(ControlOperationStatus.SUCCEEDED);
        final GetControlOperationResult getControlOperationResult = new GetControlOperationResult()
                .withControlOperation(controlOperation);

        doReturn(getControlOperationResult).when(proxy).injectCredentialsAndInvoke(any(GetControlOperationRequest.class), ArgumentMatchers.<Function<GetControlOperationRequest, GetControlOperationResult>>any());

        final CallbackContext context = CallbackContext
                .builder()
                .stabilizationRetriesRemaining(1)
                .operationIdentifier(TEST_OPERATION_ID)
                .isCreateInProgress(true)
                .build();

        // Execute
        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, context, logger);

        // Verify
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
    public void handleRequest_CallbackContextNull_InProgress() {
        // Setup
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                                    .desiredResourceState(model)
                                                                                    .build();

        EnabledControlSummary controlSummary = new EnabledControlSummary().withControlIdentifier(TEST_GR_1);
        ListEnabledControlsResult listEnabledControlsResult = new ListEnabledControlsResult()
                .withEnabledControls(Collections.singletonList(controlSummary));
        doReturn(listEnabledControlsResult).when(proxy).injectCredentialsAndInvoke(any(ListEnabledControlsRequest.class), ArgumentMatchers.<Function<ListEnabledControlsRequest, ListEnabledControlsResult>>any());

        final EnableControlResult enableControlResult = new EnableControlResult();
        enableControlResult.setOperationIdentifier(TEST_OPERATION_ID);
        doReturn(enableControlResult).when(proxy).injectCredentialsAndInvoke(any(EnableControlRequest.class), ArgumentMatchers.<Function<EnableControlRequest, EnableControlResult>>any());

        final CallbackContext desiredCallbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(1080)
                .operationIdentifier(TEST_OPERATION_ID)
                .isCreateInProgress(true)
                .build();

        // Execute
        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, null, logger);

        // Verify
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(desiredCallbackContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_CallbackContextWithInProgressOperation_InProgress() {
        // Setup
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                                    .desiredResourceState(model)
                                                                                    .build();

        final ControlOperation controlOperation = new ControlOperation()
                .withStatus(ControlOperationStatus.IN_PROGRESS);
        final GetControlOperationResult getControlOperationResult = new GetControlOperationResult()
                .withControlOperation(controlOperation);

        doReturn(getControlOperationResult).when(proxy).injectCredentialsAndInvoke(any(GetControlOperationRequest.class), ArgumentMatchers.<Function<GetControlOperationRequest, GetControlOperationResult>>any());

        final CallbackContext inputCallbackContext = CallbackContext.builder()
                                                                    .stabilizationRetriesRemaining(3)
                                                                    .operationIdentifier(TEST_OPERATION_ID)
                                                                    .isCreateInProgress(true)
                                                                    .build();

        final CallbackContext desiredOutputContext = CallbackContext.builder()
                                                                    .stabilizationRetriesRemaining(2)
                                                                    .operationIdentifier(TEST_OPERATION_ID)
                                                                    .isCreateInProgress(true)
                                                                    .build();

        // Execute
        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, inputCallbackContext, logger);

        // Verify
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackContext()).isEqualToComparingFieldByField(desiredOutputContext);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(20);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_ControlOperationStatusFailed_Fail() {
        // Setup
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                                    .desiredResourceState(model)
                                                                                    .build();

        final ControlOperation controlOperation = new ControlOperation()
                .withStatus(ControlOperationStatus.FAILED)
                .withStatusMessage(EXPECTED_FAILURE_MESSAGE);
        final GetControlOperationResult getControlOperationResult = new GetControlOperationResult()
                .withControlOperation(controlOperation);
        doReturn(getControlOperationResult).when(proxy).injectCredentialsAndInvoke(any(GetControlOperationRequest.class), ArgumentMatchers.<Function<GetControlOperationRequest, GetControlOperationResult>>any());

        final CallbackContext inputCallbackContext = CallbackContext.builder()
                                                                    .stabilizationRetriesRemaining(3)
                                                                    .operationIdentifier(TEST_OPERATION_ID)
                                                                    .isCreateInProgress(true)
                                                                    .build();

        // Execute
        final ProgressEvent<ResourceModel, CallbackContext> response
            = handler.handleRequest(proxy, request, inputCallbackContext, logger);

        // Verify
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isEqualTo(EXPECTED_FAILURE_MESSAGE);
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testStabilizationTimeout() {
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                                                                                    .desiredResourceState(model)
                                                                                    .build();

        final CallbackContext inputCallbackContext = CallbackContext.builder()
                                                                    .stabilizationRetriesRemaining(0)
                                                                    .isCreateInProgress(true)
                                                                    .build();

        try {
            handler.handleRequest(proxy, request, inputCallbackContext, logger);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo(EXPECTED_TIMEOUT_MESSAGE);
        }
    }

    @Test
    public void testEnableControl_ExceptionHandling() {
        final CreateHandler handler = new CreateHandler();
        final ResourceModel model = ResourceModel.builder().build();
        model.setControlIdentifier(TEST_GR);
        model.setTargetIdentifier(TEST_OUID);

        doThrow(new ValidationException(ERROR),
                new AccessDeniedException(ERROR),
                new ConflictException(ERROR),
                new ResourceNotFoundException(ERROR),
                new ThrottlingException(ERROR),
                new RuntimeException(ERROR),
                new RuntimeException(HTTP_TIMEOUT_EXCEPTION_MESSAGE),
                new ServiceQuotaExceededException(ERROR))
                .when(proxy).injectCredentialsAndInvoke(any(EnableControlRequest.class), ArgumentMatchers.<Function<EnableControlRequest, EnableControlResult>>any());

        final CallbackContext inputCallbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(3)
                .isCreateInProgress(true)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, request, inputCallbackContext, logger));
        assertThrows(CfnAccessDeniedException.class,
                () -> handler.handleRequest(proxy, request, inputCallbackContext, logger));
        assertThrows(CfnResourceConflictException.class,
                () -> handler.handleRequest(proxy, request, inputCallbackContext, logger));
        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, inputCallbackContext, logger));
        assertThrows(CfnThrottlingException.class,
                () -> handler.handleRequest(proxy, request, inputCallbackContext, logger));
        assertThrows(CfnInternalFailureException.class,
                () -> handler.handleRequest(proxy, request, inputCallbackContext, logger));
        assertThrows(CfnGeneralServiceException.class,
                () -> handler.handleRequest(proxy, request, inputCallbackContext, logger));
        assertThrows(CfnServiceLimitExceededException.class,
                () -> handler.handleRequest(proxy, request, inputCallbackContext, logger));
    }

    @Test
    public void testEnableControl_AlreadyExistsException() {
        final CreateHandler handler = new CreateHandler();
        final ResourceModel model = ResourceModel.builder().build();
        model.setControlIdentifier(TEST_GR);
        model.setTargetIdentifier(TEST_OUID);

        doThrow(new ValidationException(ALREADY_EXISTS))
                .when(proxy).injectCredentialsAndInvoke(any(EnableControlRequest.class), ArgumentMatchers.<Function<EnableControlRequest, EnableControlResult>>any());

        final CallbackContext inputCallbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(3)
                .isCreateInProgress(true)
                .build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        // Execute
        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, inputCallbackContext, logger);

        // Verify
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isEqualTo(EXPECTED_INTERNAL_ERROR_MESSAGE);
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testGetControlOperationStatus_ExceptionHandling() {
        final CreateHandler handler = new CreateHandler();
        final ResourceModel model = ResourceModel.builder().build();
        model.setControlIdentifier(TEST_GR);
        model.setTargetIdentifier(TEST_OUID);

        doThrow(new AccessDeniedException(ERROR),
                new ValidationException(ERROR),
                new ResourceNotFoundException(ERROR),
                new ThrottlingException(ERROR),
                new RuntimeException(ERROR),
                new RuntimeException(HTTP_TIMEOUT_EXCEPTION_MESSAGE))
                .when(proxy).injectCredentialsAndInvoke(any(GetControlOperationRequest.class), ArgumentMatchers.<Function<GetControlOperationRequest, GetControlOperationResult>>any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final CallbackContext context = CallbackContext.builder()
                .stabilizationRetriesRemaining(5)
                .operationIdentifier(TEST_OPERATION_ID)
                .isCreateInProgress(true)
                .build();

        assertThrows(CfnAccessDeniedException.class,
                () -> handler.handleRequest(proxy, request, context, logger));
        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, request, context, logger));
        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, context, logger));
        assertThrows(CfnThrottlingException.class,
                () -> handler.handleRequest(proxy, request, context, logger));
        assertThrows(CfnInternalFailureException.class,
                () -> handler.handleRequest(proxy, request, context, logger));
        assertThrows(CfnNetworkFailureException.class,
                () -> handler.handleRequest(proxy, request, context, logger));
    }

    @Test
    public void listEnabledControls_ControlAlreadyExists() {
        // Setup
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        EnabledControlSummary controlSummary = new EnabledControlSummary().withControlIdentifier(TEST_GR);
        ListEnabledControlsResult listEnabledControlsResult = new ListEnabledControlsResult()
                .withEnabledControls(Collections.singletonList(controlSummary));
        doReturn(listEnabledControlsResult).when(proxy).injectCredentialsAndInvoke(any(ListEnabledControlsRequest.class), ArgumentMatchers.<Function<ListEnabledControlsRequest, ListEnabledControlsResult>>any());

        // Execute
        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        // Verify
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.AlreadyExists);
    }

    @Test
    public void listEnabledControls_throwsException() {
        // Setup
        final CreateHandler handler = new CreateHandler();

        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        doThrow(new ValidationException(ERROR)).when(proxy).injectCredentialsAndInvoke(any(ListEnabledControlsRequest.class), ArgumentMatchers.<Function<ListEnabledControlsRequest, ListEnabledControlsResult>>any());

        // Execute
        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        // Verify
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(HandlerErrorCode.InvalidRequest);
    }
}
