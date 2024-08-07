package software.amazon.controltower.landingzone;

import software.amazon.awssdk.services.controltower.ControlTowerClient;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ReadHandler extends BaseHandlerStd {
    private Logger logger;
    private TagHelper tagHelper = new TagHelper();

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<ControlTowerClient> proxyClient,
            final Logger logger) {

        this.logger = logger;
        logger.log(String.format("[INFO] ReadHandler called with StackId: [%s], RequestId: [%s], ", request.getStackId(), request.getClientRequestToken()));

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress ->
                        proxy.initiate("AWS-ControlTower-LandingZone::Read", proxyClient, request.getDesiredResourceState(), callbackContext)
                        .translateToServiceRequest(Translator::translateToReadRequest)
                        .makeServiceCall((getLandingZoneRequest, client) -> readResource(getLandingZoneRequest, client))
                        .handleError((getLandingZoneRequest, exception, client, _model, context) -> handleError(getLandingZoneRequest, exception, _model, context, logger))
                        .done((getLandingZoneResponse) -> ProgressEvent.progress(Translator.translateFromReadResponse(getLandingZoneResponse, request.getDesiredResourceState()), callbackContext)))

                .then(progress -> tagHelper.listTagsForResource(proxy, proxyClient, progress.getResourceModel(), request, progress.getCallbackContext(), logger, false))
                .then(progress -> {
                    logger.log(String.format("[INFO] ResourceModel: [%s]", progress.getResourceModel()));
                    return ProgressEvent.defaultSuccessHandler(progress.getResourceModel());
                });
    }

    private GetLandingZoneResponse readResource(
            final GetLandingZoneRequest getLandingZoneRequest,
            final ProxyClient<ControlTowerClient> client) {
        logger.log(String.format("[INFO] Invoking GetLandingZone."));
        GetLandingZoneResponse getLandingZoneResponse = client.injectCredentialsAndInvokeV2(getLandingZoneRequest, client.client()::getLandingZone);
        logger.log(String.format("[INFO] GetLandingZone invoked successfully."));
        return getLandingZoneResponse;
    }
}
