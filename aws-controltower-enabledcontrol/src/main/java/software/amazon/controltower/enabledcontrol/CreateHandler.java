package software.amazon.controltower.enabledcontrol;

import com.amazonaws.services.controltower.AWSControlTower;
import com.amazonaws.services.controltower.model.AccessDeniedException;
import com.amazonaws.services.controltower.model.ConflictException;
import com.amazonaws.services.controltower.model.ControlOperation;
import com.amazonaws.services.controltower.model.ControlOperationStatus;
import com.amazonaws.services.controltower.model.EnableControlRequest;
import com.amazonaws.services.controltower.model.EnableControlResult;
import com.amazonaws.services.controltower.model.GetControlOperationRequest;
import com.amazonaws.services.controltower.model.ResourceNotFoundException;
import com.amazonaws.services.controltower.model.ServiceQuotaExceededException;
import com.amazonaws.services.controltower.model.ThrottlingException;
import com.amazonaws.services.controltower.model.ValidationException;
import software.amazon.cloudformation.exceptions.BaseHandlerException;
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

public class CreateHandler extends BaseHandler<CallbackContext> {

    private AWSControlTower controlTowerClient;
    private ResourceHandlerRequest<ResourceModel> request;
    private AmazonWebServicesClientProxy clientProxy;
    private Logger logger;

    private static final int NUMBER_OF_STATE_POLL_RETRIES = 1080;
    private static final int CALLBACK_DELAY_SECONDS = 20;
    private static final String TIMED_OUT_MESSAGE = "Timed out waiting for enable control operation to complete.";
    private static final String INTERNAL_ERROR_MESSAGE = "AWS Control Tower could not enable the control due to an internal error.";

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final Logger logger) {

        clientProxy = proxy;
        final ResourceModel model = request.getDesiredResourceState();
        this.request = request;
        this.logger = logger;

        controlTowerClient = ClientBuilder.getStandardClient(logger);

        final CallbackContext currentContext = callbackContext == null ?
                                               CallbackContext
                                                       .builder()
                                                       .stabilizationRetriesRemaining(NUMBER_OF_STATE_POLL_RETRIES)
                                                       .isCreateInProgress(false)
                                                       .build() :
                                               callbackContext;

        if(!currentContext.getIsCreateInProgress()) {
            try {
                final ProgressEvent<ResourceModel, CallbackContext> readResponse = (new ReadHandler(controlTowerClient))
                        .handleRequest(proxy, request, CallbackContext.builder().build(), logger);

                if(OperationStatus.SUCCESS.equals(readResponse.getStatus())) {
                    logger.log(String.format("StackId [%s] skipping create as control %s is already enabled on target %s",
                            request.getStackId(), model.getControlIdentifier(), model.getTargetIdentifier()));
                    return ProgressEvent.<ResourceModel, CallbackContext>builder()
                            .status(OperationStatus.FAILED)
                            .errorCode(HandlerErrorCode.AlreadyExists)
                            .build();
                }
            } catch (BaseHandlerException e) {
                logger.log(String.format("StackId [%s] readHandler failed with an exception %s", request.getStackId(), e.getErrorCode()));
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .status(OperationStatus.FAILED)
                        .errorCode(e.getErrorCode())
                        .build();
            } catch (Throwable e) {
                throw new CfnInternalFailureException(e);
            }
        }

        currentContext.setIsCreateInProgress(true);

        // This Lambda will continually be re-invoked with the current state of the Guardrail, finally succeeding when state stabilizes.
        return createEnabledGuardrailAndUpdateProgress(model, currentContext);
    }

    private ProgressEvent<ResourceModel, CallbackContext> createEnabledGuardrailAndUpdateProgress(ResourceModel model, CallbackContext callbackContext) {
        // This Lambda will continually be re-invoked with the current state of the instance, finally succeeding when state stabilizes.
        String operationId = callbackContext.getOperationIdentifier();

        if (callbackContext.getStabilizationRetriesRemaining() == 0) {
            throw new RuntimeException(TIMED_OUT_MESSAGE);
        }

        if (operationId == null) {
            logger.log(String.format("StackId [%s] invoking enableControl for control %s and target %s",
                    request.getStackId(), model.getControlIdentifier(), model.getTargetIdentifier()));
            try {
                operationId = enableControl(model);
            } catch (CfnAlreadyExistsException e) {
                logger.log(String.format("StackId [%s] failed to enable as control %s already exists on target %s",
                        request.getStackId(), model.getControlIdentifier(), model.getTargetIdentifier()));
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(model)
                        .status(OperationStatus.FAILED)
                        .message(INTERNAL_ERROR_MESSAGE)
                        .build();
            }

            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                                .resourceModel(model)
                                .status(OperationStatus.IN_PROGRESS)
                                .callbackContext(CallbackContext.builder()
                                                                .operationIdentifier(operationId)
                                                                .stabilizationRetriesRemaining(NUMBER_OF_STATE_POLL_RETRIES)
                                                                .isCreateInProgress(callbackContext.getIsCreateInProgress())
                                                                .build())
                                .build();
        } else {
            logger.log(String.format("StackId [%s] invoking getControlOperation for operationId %s",
                    request.getStackId(), operationId));
            final ControlOperation controlOperation = getControlOperation(operationId);
            final String currentStatus = controlOperation.getStatus();
            logger.log(String.format("StackId [%s] returned getControlOperation status as %s for operationId %s",
                    request.getStackId(), currentStatus, operationId));
            if (ControlOperationStatus.SUCCEEDED.toString().equals(currentStatus)) {
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                                    .resourceModel(model)
                                    .status(OperationStatus.SUCCESS)
                                    .build();
            } else if (ControlOperationStatus.FAILED.toString().equals(currentStatus)) {
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                                    .resourceModel(model)
                                    .status(OperationStatus.FAILED)
                                    .message(controlOperation.getStatusMessage())
                                    .build();
            } else {
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                                    .resourceModel(model)
                                    .status(OperationStatus.IN_PROGRESS)
                                    .callbackDelaySeconds(CALLBACK_DELAY_SECONDS)
                                    .callbackContext(CallbackContext.builder()
                                                                    .operationIdentifier(operationId)
                                                                    .stabilizationRetriesRemaining(callbackContext.getStabilizationRetriesRemaining() - 1)
                                                                    .isCreateInProgress(callbackContext.getIsCreateInProgress())
                                                                    .build())
                                    .build();
            }
        }
    }

    private String enableControl(ResourceModel model) {
        try {
            final EnableControlResult enableControlResult = clientProxy.injectCredentialsAndInvoke(new EnableControlRequest()
                    .withControlIdentifier(model.getControlIdentifier())
                    .withTargetIdentifier(model.getTargetIdentifier()), controlTowerClient::enableControl);

            logger.log(String.format("StackId [%s] enableControl received operation id %s for control %s and target %s",
                    request.getStackId(), enableControlResult.getOperationIdentifier(), model.getControlIdentifier(), model.getTargetIdentifier()));

            return enableControlResult.getOperationIdentifier();
        } catch (ValidationException e) {
            if(e.getMessage().contains("already enabled on organizational unit")) {
                throw new CfnAlreadyExistsException(e);
            } else {
                throw new CfnInvalidRequestException(e);
            }
        } catch (final AccessDeniedException e) {
            throw new CfnAccessDeniedException(e);
        } catch (final ConflictException e) {
            throw new CfnResourceConflictException(e);
        } catch (final ResourceNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (final ThrottlingException e) {
            throw new CfnThrottlingException(e);
        } catch(final ServiceQuotaExceededException e) {
            throw new CfnServiceLimitExceededException(e);
        } catch (final Exception e) {
            if (e.getMessage().contains("HttpTimeoutException")) {
                throw new CfnGeneralServiceException(e);
            }
            throw new CfnInternalFailureException(e);
        }
    }

    private ControlOperation getControlOperation(String operationId) {
        try {
            return clientProxy.injectCredentialsAndInvoke(new GetControlOperationRequest()
                    .withOperationIdentifier(operationId), controlTowerClient::getControlOperation).getControlOperation();
        } catch (final AccessDeniedException e) {
            throw new CfnAccessDeniedException(e.getMessage());
        } catch (final ValidationException e) {
            throw new CfnInvalidRequestException(e.getMessage());
        } catch (final ResourceNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (final ThrottlingException e) {
            throw new CfnThrottlingException(e);
        } catch (final Exception e) {
            if (e.getMessage().contains("HttpTimeoutException")) {
                throw new CfnNetworkFailureException(e);
            }
            throw new CfnInternalFailureException(e);
        }
    }

}
