package software.amazon.controltower.enabledcontrol;

import com.amazonaws.services.controltower.AWSControlTower;
import com.amazonaws.services.controltower.model.AccessDeniedException;
import com.amazonaws.services.controltower.model.ConflictException;
import com.amazonaws.services.controltower.model.ControlOperation;
import com.amazonaws.services.controltower.model.ControlOperationStatus;
import com.amazonaws.services.controltower.model.DisableControlRequest;
import com.amazonaws.services.controltower.model.DisableControlResult;
import com.amazonaws.services.controltower.model.GetControlOperationRequest;
import com.amazonaws.services.controltower.model.ResourceNotFoundException;
import com.amazonaws.services.controltower.model.ServiceQuotaExceededException;
import com.amazonaws.services.controltower.model.ThrottlingException;
import com.amazonaws.services.controltower.model.ValidationException;
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

import static software.amazon.controltower.enabledcontrol.HandlerUtils.logException;

public class DeleteHandler extends BaseHandler<CallbackContext> {

    private static final int NUMBER_OF_STATE_POLL_RETRIES = 1080;
    private static final int CALLBACK_DELAY_SECONDS = 20;
    private static final String TIMED_OUT_MESSAGE = "Timed out waiting for deassociation of control to complete.";
    ResourceHandlerRequest<ResourceModel> request;
    private AWSControlTower controlTowerClient;
    private AmazonWebServicesClientProxy clientProxy;
    private Logger logger;

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();
        clientProxy = proxy;
        controlTowerClient = ClientBuilder.getStandardClient(logger);
        this.request = request;
        this.logger = logger;

        final CallbackContext currentContext = callbackContext == null ?
                CallbackContext.builder().stabilizationRetriesRemaining(NUMBER_OF_STATE_POLL_RETRIES).build() :
                callbackContext;

        // This Lambda will continually be re-invoked with the current state of the Guardrail, finally succeeding when state stabilizes.
        return disableControlAndUpdateProgress(model, currentContext);
    }

    private ProgressEvent<ResourceModel, CallbackContext> disableControlAndUpdateProgress(ResourceModel model, CallbackContext callbackContext) {
        // This Lambda will continually be re-invoked with the current state of the instance, finally succeeding when state stabilizes.
        final String operationId = callbackContext.getOperationIdentifier();

        if (callbackContext.getStabilizationRetriesRemaining() == 0) {
            throw new RuntimeException(TIMED_OUT_MESSAGE);
        }

        if (operationId == null) {
            logger.log("Invoking Delete handler for new resource.");
            return ProgressEvent.<ResourceModel, CallbackContext>builder()
                    .resourceModel(model)
                    .status(OperationStatus.IN_PROGRESS)
                    .callbackContext(CallbackContext.builder()
                            .operationIdentifier(disableControl(model))
                            .stabilizationRetriesRemaining(NUMBER_OF_STATE_POLL_RETRIES)
                            .build())
                    .build();
        } else {
            logger.log(String.format("Invoking Delete handler for stabilizing resource operation %s", operationId));
            final ControlOperation controlOperation = getControlOperation(operationId);
            final String currentStatus = controlOperation.getStatus();
            this.logger.log("Operation Id:" + operationId + "\n Delete Stabilization: " + currentStatus);
            if (ControlOperationStatus.SUCCEEDED.toString().equals(currentStatus)) {
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(null)
                        .status(OperationStatus.SUCCESS)
                        .build();
            } else if (ControlOperationStatus.FAILED.toString().equals(currentStatus)) {
                return ProgressEvent.<ResourceModel, CallbackContext>builder()
                        .resourceModel(null)
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
                                .build())
                        .build();
            }
        }
    }

    private String disableControl(ResourceModel model) {
        try {
            final DisableControlResult disableControlResult = clientProxy.injectCredentialsAndInvoke(new DisableControlRequest()
                    .withControlIdentifier(model.getControlIdentifier())
                    .withTargetIdentifier(model.getTargetIdentifier()), controlTowerClient::disableControl);
            logger.log(String.format("Received operation id: %s", disableControlResult.getOperationIdentifier()));
            return disableControlResult.getOperationIdentifier();
        } catch (final AccessDeniedException e) {
            throw new CfnAccessDeniedException(e);
        } catch (final ConflictException e) {
            throw new CfnResourceConflictException(e);
        } catch (final ValidationException e) {
            throw new CfnInvalidRequestException(e);
        } catch (final ResourceNotFoundException e) {
            logger.log(String.format("StackId %s skipping delete for control %s and target %s", request.getStackId(), model.getControlIdentifier(), model.getTargetIdentifier()));
            throw new CfnNotFoundException(e);
        } catch (final ThrottlingException e) {
            throw new CfnThrottlingException(e);
        } catch(final ServiceQuotaExceededException e) {
            throw new CfnServiceLimitExceededException(e);
        } catch (final Exception e) {
            if (e.getMessage().contains("HttpTimeoutException")) {
                throw new CfnNetworkFailureException(e);
            }
            logException(e, this.logger);
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
            logException(e, this.logger);
            throw new CfnInternalFailureException(e);
        }
    }

}
