package software.amazon.controltower.landingzone;

import java.util.Map;
import java.util.Set;

import software.amazon.awssdk.services.controltower.ControlTowerClient;
import software.amazon.awssdk.services.controltower.model.UpdateLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.UpdateLandingZoneResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;


public class UpdateHandler extends BaseHandlerStd {
    private Logger logger;
    private Constant backOffStrategy;
    private TagHelper tagHelper = new TagHelper();

    public UpdateHandler() {
        this(DEFAULT_BACKOFF_STRATEGY);
    }

    public UpdateHandler(Constant backOffStrategy) {
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
        logger.log(String.format("[INFO] UpdateHandler called with StackId: [%s], RequestId: [%s], ", request.getStackId(), request.getClientRequestToken()));

        TagHelper.validateRequestDoesNotIncludeProhibitedTags(request);

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            // STEP 1 [check if resource already exists]
            // Handled as part of UpdateLandingZone ResourceNotFoundException

            // STEP 2 [first update/stabilize progress chain - required for resource update]
            .then(progress ->
                // STEP 2.0 [initialize a proxy context]
                // Implement client invocation of the update request through the proxyClient, which is already initialised with
                // caller credentials, correct region and retry settings
                proxy.initiate("AWS-ControlTower-LandingZone::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())

                    // STEP 2.1 [Construct a body of a request]
                    .translateToServiceRequest(Translator::translateToUpdateRequest)

                    // default stabilization timeout is 20 minutes
                    // https://github.com/aws-cloudformation/cloudformation-cli-java-plugin/blob/master/src/main/java/software/amazon/cloudformation/proxy/DelayFactory.java#L22
                    // setting it to a higher value
                    .backoffDelay(backOffStrategy)

                    // STEP 2.2 [Make an api call]
                    .makeServiceCall((requestMap, client) -> updateResource(requestMap, client))

                    // STEP 2.3 [Stabilize step is not necessarily required but typically involves describing the resource until it is in a certain status, though it can take many forms]
                    // stabilization step may or may not be needed after each API call
                    // for more information -> https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-test-contract.html
                    .stabilize((requestMap, updateLandingZoneResponse, client, model, context) -> stabilizationCheck(updateLandingZoneResponse.operationIdentifier(), client, model, logger))
                    .handleError((requestMap, exception, client, _model, context) -> {
                        return handleError(Translator.translateToUpdateRequest(requestMap), exception, _model, context, logger);
                    }).progress())

            // STEP 3 [Update tags]
            .then(progress -> tagHelper.listTagsForResource(proxy, proxyClient, progress.getResourceModel(), request, progress.getCallbackContext(), logger, true))
            .then(progress -> updateTags(proxy, proxyClient, progress, request))

            // STEP 4 [Describe call/chain to return the resource model]
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private UpdateLandingZoneResponse updateResource(
            final Map<String, Object> requestMap,
            final ProxyClient<ControlTowerClient> client) {

        final UpdateLandingZoneRequest updateLandingZoneRequest = Translator.translateToUpdateRequest(requestMap);

        logger.log(String.format("[INFO] Invoking UpdateLandingZone."));
        UpdateLandingZoneResponse updateLandingZoneResponse = client.injectCredentialsAndInvokeV2(updateLandingZoneRequest, client.client()::updateLandingZone);
        logger.log(String.format("[INFO] UpdateLandingZone invoked successfully."));

        return updateLandingZoneResponse;
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateTags(
            final AmazonWebServicesClientProxy proxy,
            final ProxyClient<ControlTowerClient> proxyClient,
            final ProgressEvent<ResourceModel, CallbackContext> progressEvent,
            final ResourceHandlerRequest<ResourceModel> request) {
        ResourceModel model = progressEvent.getResourceModel();
        CallbackContext callbackContext = progressEvent.getCallbackContext();

        if (!tagHelper.shouldUpdateTags(request)) {
            return ProgressEvent.progress(model, callbackContext);
        }

        final Map<String, String> oldTags = tagHelper.getPreviouslyAttachedTags(request);
        final Map<String, String> newTags = tagHelper.getNewDesiredTags(request);

        final Map<String, String> tagsToAdd = tagHelper.generateTagsToAdd(oldTags, newTags);
        final Set<String> tagsToRemove = tagHelper.generateTagsToRemove(oldTags, newTags);

        return tagHelper.untagResource(proxy, proxyClient, model, request, callbackContext, tagsToRemove, logger)
                .then(progressEvent1 -> tagHelper.tagResource(proxy, proxyClient, model, request, callbackContext, tagsToAdd, logger));
    }
}
