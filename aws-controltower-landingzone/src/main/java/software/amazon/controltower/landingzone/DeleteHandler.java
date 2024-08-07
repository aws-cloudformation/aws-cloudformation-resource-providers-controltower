package software.amazon.controltower.landingzone;

import software.amazon.awssdk.services.controltower.ControlTowerClient;
import software.amazon.awssdk.services.controltower.model.CreateLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.CreateLandingZoneResponse;
import software.amazon.awssdk.services.controltower.model.DeleteLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.DeleteLandingZoneResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;

public class DeleteHandler extends BaseHandlerStd {
    private Logger logger;

    private final Constant backOffStrategy;

    public DeleteHandler() {
        this(DEFAULT_BACKOFF_STRATEGY);
    }

    public DeleteHandler(Constant backOffStrategy) {
        super();
        this.backOffStrategy = backOffStrategy;
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<ControlTowerClient> proxyClient,
        final Logger logger) {

        this.logger = logger;
        logger.log(String.format("[INFO] DeleteHandler called with StackId: [%s], RequestId: [%s], ", request.getStackId(), request.getClientRequestToken()));

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)

            // STEP 1 [check if resource already exists]
            // Existence Check is not needed as API throw Resource Not Found
            // STEP 2.0 [delete/stabilize progress chain - required for resource deletion]
            .then(progress ->
                // If your service API throws 'ResourceNotFoundException' for delete requests then DeleteHandler can return just proxy.initiate construction
                // STEP 2.0 [initialize a proxy context]
                // Implement client invocation of the delete request through the proxyClient, which is already initialised with
                // caller credentials, correct region and retry settings
                proxy.initiate("AWS-ControlTower-LandingZone::Delete", proxyClient, progress.getResourceModel(), progress.getCallbackContext())

                    // STEP 2.1 [construct a body of a request]
                    .translateToServiceRequest(Translator::translateToDeleteRequest)
                    .backoffDelay(backOffStrategy)

                    // STEP 2.2 [make an api call]
                    .makeServiceCall((deleteLandingZoneRequest, client) -> deleteResource(deleteLandingZoneRequest, client))

                    // STEP 2.3 [stabilize step is not necessarily required but typically involves describing the resource until it is in a certain status, though it can take many forms]
                    // for more information -> https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-test-contract.html
                    .stabilize((deleteLandingZoneRequest, deleteLandingZoneResponse, client, model, context) ->  stabilizationCheck(deleteLandingZoneResponse.operationIdentifier(), client, model, logger))
                    .handleError((deleteLandingZoneRequest, exception, client, _model, context) -> handleError(deleteLandingZoneRequest, exception, _model, context, logger))
                    .progress()
            )

            // STEP 3 [return the successful progress event without resource model]
            .then(progress -> ProgressEvent.defaultSuccessHandler(null));
    }

    private DeleteLandingZoneResponse deleteResource(
            final DeleteLandingZoneRequest deleteLandingZoneRequest,
            final ProxyClient<ControlTowerClient> client) {
        logger.log(String.format("[INFO] Invoking DeleteLandingZone."));
        DeleteLandingZoneResponse deleteLandingZoneResponse = client.injectCredentialsAndInvokeV2(deleteLandingZoneRequest, client.client()::deleteLandingZone);
        logger.log(String.format("[INFO] DeleteLandingZone invoked successfully."));
        return deleteLandingZoneResponse;
    }
}
