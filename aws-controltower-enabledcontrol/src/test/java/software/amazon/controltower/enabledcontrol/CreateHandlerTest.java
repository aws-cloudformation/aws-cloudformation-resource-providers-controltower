package software.amazon.controltower.enabledcontrol;

import com.amazonaws.services.controltower.AWSControlTower;
import com.amazonaws.services.controltower.model.AccessDeniedException;
import com.amazonaws.services.controltower.model.ControlOperation;
import com.amazonaws.services.controltower.model.ControlOperationStatus;
import com.amazonaws.services.controltower.model.EnableControlRequest;
import com.amazonaws.services.controltower.model.EnableControlResult;
import com.amazonaws.services.controltower.model.ConflictException;
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
public class CreateHandlerTest {

    public static final String TEST_GR = "AWS-GR_TEST_GUARDRAIL";
    public static final String TEST_OUID = "ou-test-stpcyh2h";
    public static final String TEST_OPERATION_ID = "3e10c87d-44c5-746d-0207-843c3ce5734b";
    private static final String EXPECTED_TIMEOUT_MESSAGE = "Timed out waiting for association of control to complete.";
    private static final String EXPECTED_FAILURE_MESSAGE = "Enable guardrail operation failed";
    private static final String HTTP_TIMEOUT_EXCEPTION_MESSAGE = "HttpTimeoutException";
    private static final String ERROR = "Error";
    private static final String ALREADY_EXISTS = "already enabled on organizational unit";
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

        final EnableControlResult EnableControlResult = new EnableControlResult();
        EnableControlResult.setOperationIdentifier(TEST_OPERATION_ID);
        doReturn(EnableControlResult).when(proxy).injectCredentialsAndInvoke(any(EnableControlRequest.class), ArgumentMatchers.<Function<EnableControlRequest, EnableControlResult>>any());

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

        doThrow(new ValidationException(ALREADY_EXISTS),
                new ValidationException(ERROR),
                new AccessDeniedException(ERROR),
                new ConflictException(ERROR),
                new ResourceNotFoundException(ERROR),
                new ThrottlingException(ERROR),
                new RuntimeException(ERROR),
                new RuntimeException(HTTP_TIMEOUT_EXCEPTION_MESSAGE),
                new ServiceQuotaExceededException(ERROR))
                .when(proxy).injectCredentialsAndInvoke(any(EnableControlRequest.class), ArgumentMatchers.<Function<EnableControlRequest, EnableControlResult>>any());

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        assertThrows(CfnAlreadyExistsException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnInvalidRequestException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnAccessDeniedException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnResourceConflictException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnNotFoundException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnThrottlingException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnInternalFailureException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnGeneralServiceException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
        assertThrows(CfnServiceLimitExceededException.class,
                () -> handler.handleRequest(proxy, request, null, logger));
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
