package software.amazon.controltower.landingzone;

import java.util.Map;

import software.amazon.awssdk.services.controltower.ControlTowerClient;
import software.amazon.awssdk.services.controltower.model.CreateLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.CreateLandingZoneResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import software.amazon.cloudformation.proxy.delay.Constant;


public class CreateHandler extends BaseHandlerStd {
    private Logger logger;
    private Constant backOffStrategy;
    private TagHelper tagHelper = new TagHelper();

    public CreateHandler() {
        this(DEFAULT_BACKOFF_STRATEGY);
    }

    public CreateHandler(Constant backOffStrategy) {
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
        logger.log(String.format("[INFO] CreateHandler called with StackId: [%s], RequestId: [%s], ", request.getStackId(), request.getClientRequestToken()));

        TagHelper.validateRequestDoesNotIncludeProhibitedTags(request);

        Map<String, String> tags = tagHelper.getNewDesiredTags(request);

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            // STEP 1 [check if resource already exists]
            // Handled as part of CreateLandingZone Conflict Exception

            // STEP 2 [create/stabilize progress chain - required for resource creation]
            .then(progress ->
                // If your service API throws 'ResourceAlreadyExistsException' for create requests then CreateHandler can return just proxy.initiate construction
                // STEP 2.0 [initialize a proxy context]
                // Implement client invocation of the create request through the proxyClient, which is already initialised with
                // caller credentials, correct region and retry settings
                proxy.initiate("AWS-ControlTower-LandingZone::Create", proxyClient, progress.getResourceModel(), progress.getCallbackContext())

                    // STEP 2.1 [Construct a body of a request]
                    .translateToServiceRequest(model -> Translator.translateToCreateRequest(model, tags))

                    // default stabilization timeout is 20 minutes
                    // https://github.com/aws-cloudformation/cloudformation-cli-java-plugin/blob/master/src/main/java/software/amazon/cloudformation/proxy/DelayFactory.java#L22
                    // setting it to a higher value
                    .backoffDelay(backOffStrategy)

                    // STEP 2.2 [Make an api call]
                    .makeServiceCall((requestMap, client) -> createResource(requestMap, client, progress.getResourceModel()))

                    // STEP 2.3 [Stabilize step is not necessarily required but typically involves describing the resource until it is in a certain status, though it can take many forms]
                    // for more information -> https://docs.aws.amazon.com/cloudformation-cli/latest/userguide/resource-type-test-contract.html
                    // If your resource requires some form of stabilization (e.g. service does not provide strong consistency), you will need to ensure that your code
                    // accounts for any potential issues, so that a subsequent read/update requests will not cause any conflicts (e.g. NotFoundException/InvalidRequestException)
                    .stabilize((requestMap, createLandingZoneResponse, client, model, context) -> stabilizationCheck(createLandingZoneResponse.operationIdentifier(), client, model, logger))
                    .handleError((requestMap, exception, client, _model, context) -> {
                        return handleError(Translator.translateToCreateRequest(requestMap), exception, _model, context, logger);
                    })
                    .progress())

            // STEP 3 [Describe call/chain to return the resource model]
            .then(progress -> new ReadHandler().handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private CreateLandingZoneResponse createResource(
        final Map<String, Object> requestMap,
        final ProxyClient<ControlTowerClient> client,
        final ResourceModel model) {
        final CreateLandingZoneRequest createLandingZoneRequest = Translator.translateToCreateRequest(requestMap);

        logger.log(String.format("[INFO] Invoking CreateLandingZone."));
        CreateLandingZoneResponse createLandingZoneResponse = client.injectCredentialsAndInvokeV2(createLandingZoneRequest, client.client()::createLandingZone);
        model.setLandingZoneIdentifier(createLandingZoneResponse.arn());
        logger.log(String.format("[INFO] CreateLandingZone invoked successfully."));

        return createLandingZoneResponse;
    }
}
