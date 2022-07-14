package software.amazon.controltower.enabledcontrol;

import com.amazonaws.services.controltower.AWSControlTower;
import com.amazonaws.services.controltower.model.AccessDeniedException;
import com.amazonaws.services.controltower.model.ConflictException;
import com.amazonaws.services.controltower.model.ControlOperation;
import com.amazonaws.services.controltower.model.ControlOperationStatus;
import com.amazonaws.services.controltower.model.DisableControlRequest;
import com.amazonaws.services.controltower.model.DisableControlResult;
import com.amazonaws.services.controltower.model.GetControlOperationRequest;
import com.amazonaws.services.controltower.model.GetControlOperationResult;
import com.amazonaws.services.controltower.model.ResourceNotFoundException;
import com.amazonaws.services.controltower.model.ServiceQuotaExceededException;
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
import software.amazon.cloudformation.exceptions.CfnNetworkFailureException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.exceptions.CfnServiceLimitExceededException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class DeleteHandlerTest {

    public static final String TEST_GR = "AWS-GR_TEST_GUARDRAIL";
    public static final String TEST_OUID = "ou-test-stpcyh2h";
    public static final String TEST_OPERATION_ID = "3e10c87d-44c5-746d-0207-843c3ce5734b";
    private static final String EXPECTED_TIMEOUT_MESSAGE = "Timed out waiting for deassociation of control to complete.";
    private static final String EXPECTED_FAILURE_MESSAGE = "Enable guardrail operation failed";
    private static final String ERROR = "Error";
    private static final String HTTP_TIMEOUT_EXCEPTION_MESSAGE = "HttpTimeoutException";
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
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final ControlOperation controlOperation = new ControlOperation()
                .withStatus(ControlOperationStatus.SUCCEEDED);
        final GetControlOperationResult getControlOperationResult = new GetControlOperationResult()
                .withControlOperation(controlOperation);
        doReturn(getControlOperationResult).when(proxy).injectCredentialsAndInvoke(any(GetControlOperationRequest.class), ArgumentMatchers.<Function<GetControlOperationRequest, GetControlOperationResult>>any());

        final CallbackContext context = CallbackContext.builder()
                .stabilizationRetriesRemaining(1)
                .operationIdentifier(TEST_OPERATION_ID)
                .build();

        // Execute
        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, context, logger);

        // Verify
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_CallbackContextNull_InProgress() {
        // Setup
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final DisableControlResult DisableControlResult = new DisableControlResult();
        DisableControlResult.setOperationIdentifier(TEST_OPERATION_ID);
        doReturn(DisableControlResult).when(proxy).injectCredentialsAndInvoke(any(DisableControlRequest.class), ArgumentMatchers.<Function<DisableControlRequest, DisableControlResult>>any());

        final CallbackContext desiredCallbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(1080)
                .operationIdentifier(TEST_OPERATION_ID)
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
        final DeleteHandler handler = new DeleteHandler();

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
                .build();

        final CallbackContext desiredOutputContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(2)
                .operationIdentifier(TEST_OPERATION_ID)
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
        final DeleteHandler handler = new DeleteHandler();

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
                .build();

        // Execute
        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, inputCallbackContext, logger);

        // Verify
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isNull();
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isEqualTo(EXPECTED_FAILURE_MESSAGE);
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void testStabilizationTimeout() {
        final DeleteHandler handler = new DeleteHandler();

        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        final CallbackContext inputCallbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(0)
                .build();

        try {
            handler.handleRequest(proxy, request, inputCallbackContext, logger);
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo(EXPECTED_TIMEOUT_MESSAGE);
        }
    }

    @Test
    public void DisableControl_ResourceNotFound_Fails() {
        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();
        final DeleteHandler handler = new DeleteHandler();

        doThrow(ResourceNotFoundException.class)
                .when(proxy)
                .injectCredentialsAndInvoke(any(DisableControlRequest.class), ArgumentMatchers.<Function<DisableControlRequest, DisableControlResult>>any());

        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void stabilization_ResourceNotFound_Fails() {
        final ResourceModel model = ResourceModel.builder().controlIdentifier(TEST_GR).targetIdentifier(TEST_OUID).build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();
        final DeleteHandler handler = new DeleteHandler();

        doThrow(ResourceNotFoundException.class)
                .when(proxy)
                .injectCredentialsAndInvoke(any(GetControlOperationRequest.class), ArgumentMatchers.<Function<GetControlOperationRequest, GetControlOperationResult>>any());

        final CallbackContext callbackContext = CallbackContext.builder()
                .stabilizationRetriesRemaining(10)
                .operationIdentifier("id")
                .build();

        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, callbackContext, logger));
    }

    @Test
    public void testDisableControl_ExceptionHandling() {
        final DeleteHandler handler = new DeleteHandler();
        final ResourceModel model = ResourceModel.builder().build();
        model.setControlIdentifier(TEST_GR);
        model.setTargetIdentifier(TEST_OUID);

        doThrow(new AccessDeniedException(ERROR),
                new ConflictException(ERROR),
                new ValidationException(ERROR),
                new ResourceNotFoundException(ERROR),
                new ThrottlingException(ERROR),
                new RuntimeException(ERROR),
                new RuntimeException(HTTP_TIMEOUT_EXCEPTION_MESSAGE),
                new ServiceQuotaExceededException(ERROR))
                .when(proxy).injectCredentialsAndInvoke(any(DisableControlRequest.class), ArgumentMatchers.<Function<DisableControlRequest, DisableControlResult>>any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        assertThrows(CfnAccessDeniedException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnResourceConflictException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnThrottlingException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnInternalFailureException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnNetworkFailureException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnServiceLimitExceededException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
    }

    @Test
    public void testGetControlOperationStatus_ExceptionHandling() {
        final DeleteHandler handler = new DeleteHandler();
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
}
