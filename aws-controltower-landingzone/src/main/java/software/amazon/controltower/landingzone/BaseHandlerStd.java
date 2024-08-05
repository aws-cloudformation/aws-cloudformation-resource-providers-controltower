package software.amazon.controltower.landingzone;

import java.time.Duration;

import software.amazon.awssdk.services.controltower.ControlTowerClient;
import software.amazon.awssdk.services.controltower.model.AccessDeniedException;
import software.amazon.awssdk.services.controltower.model.ConflictException;
import software.amazon.awssdk.services.controltower.model.ControlTowerException;
import software.amazon.awssdk.services.controltower.model.ControlTowerRequest;
import software.amazon.awssdk.services.controltower.model.CreateLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneOperationRequest;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneOperationResponse;
import software.amazon.awssdk.services.controltower.model.InternalServerException;
import software.amazon.awssdk.services.controltower.model.LandingZoneOperationStatus;
import software.amazon.awssdk.services.controltower.model.ResourceNotFoundException;
import software.amazon.awssdk.services.controltower.model.ThrottlingException;
import software.amazon.awssdk.services.controltower.model.ValidationException;
import software.amazon.cloudformation.exceptions.BaseHandlerException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnAlreadyExistsException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnNotStabilizedException;
import software.amazon.cloudformation.exceptions.CfnResourceConflictException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;


// Placeholder for the functionality that could be shared across Create/Read/Update/Delete/List Handlers
public abstract class BaseHandlerStd extends BaseHandler<CallbackContext> {
    protected static final Constant DEFAULT_BACKOFF_STRATEGY = Constant.of().timeout(Duration.ofHours(6L)).delay(Duration.ofMinutes(3L)).build();

    @Override
    public final ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {
        return handleRequest(
                proxy,
                request,
                callbackContext != null ? callbackContext : new CallbackContext(),
                proxy.newProxy(ClientBuilder::getClient),
                logger
        );
    }

    protected abstract ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<ControlTowerClient> proxyClient,
            final Logger logger);

    protected Boolean stabilizationCheck(String operationIdentifier, ProxyClient<ControlTowerClient> proxyClient, ResourceModel model, Logger logger) {
        GetLandingZoneOperationRequest getLandingZoneOperationRequest = Translator.translateToGetLandingZoneOperationReadRequest(operationIdentifier);
        logger.log(String.format("[INFO] Invoking GetLandingZoneOperation"));
        GetLandingZoneOperationResponse getLandingZoneOperationResponse = proxyClient.injectCredentialsAndInvokeV2(getLandingZoneOperationRequest, proxyClient.client()::getLandingZoneOperation);
        logger.log(String.format("[INFO] GetLandingZoneOperation invoked successfully."));

        LandingZoneOperationStatus landingZoneOperationStatus = getLandingZoneOperationResponse.operationDetails().status();
        if (LandingZoneOperationStatus.SUCCEEDED.equals(landingZoneOperationStatus)) {
            logger.log(String.format("[INFO] %s [%s] has been stabilized.", ResourceModel.TYPE_NAME, model.getPrimaryIdentifier()));
            return true;
        } else if (LandingZoneOperationStatus.FAILED.equals(landingZoneOperationStatus)) {
            logger.log(String.format("[INFO] %s [%s] has failed to stabilized.", ResourceModel.TYPE_NAME, model.getPrimaryIdentifier()));
            throw new CfnNotStabilizedException(ResourceModel.TYPE_NAME, model.getLandingZoneIdentifier());
        }
        return false;
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleError(
            final ControlTowerRequest controlTowerRequest,
            final Exception e,
            final ResourceModel resourceModel,
            final CallbackContext callbackContext,
            final Logger logger) {

        BaseHandlerException ex;
        logger.log(String.format("[Error] received for %s with error %s", resourceModel.getLandingZoneIdentifier(), e.getMessage()));

        if (e instanceof IllegalArgumentException) {
            ex = new CfnInvalidRequestException(e);
        } else if (e instanceof AccessDeniedException) {
            ex = new CfnAccessDeniedException(e);
        } else if (e instanceof InternalServerException) {
            ex = new CfnInternalFailureException(e);
        } else if (e instanceof ThrottlingException) {
            ex = new CfnThrottlingException(e);
        } else if (e instanceof ValidationException) {
            ex = new CfnInvalidRequestException(e);
        } else if (e instanceof ResourceNotFoundException) {
            ex = new CfnNotFoundException(e);
        } else if (e instanceof ConflictException ) {
            ex = (controlTowerRequest instanceof CreateLandingZoneRequest) ? new CfnAlreadyExistsException(e) : new CfnResourceConflictException(e);
        } else if (e instanceof ControlTowerException) {
            ex = new CfnInternalFailureException(e);
        } else {
            ex = new CfnGeneralServiceException(e);
        }

        return ProgressEvent.failed(resourceModel, callbackContext, ex.getErrorCode(), ex.getMessage());
    }
}
