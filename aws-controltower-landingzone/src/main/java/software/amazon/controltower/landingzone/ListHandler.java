package software.amazon.controltower.landingzone;

import software.amazon.awssdk.services.controltower.ControlTowerClient;
import software.amazon.awssdk.services.controltower.model.ListLandingZonesRequest;
import software.amazon.awssdk.services.controltower.model.ListLandingZonesResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class ListHandler extends BaseHandlerStd {
    private Logger logger;

    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final ProxyClient<ControlTowerClient> proxyClient,
            final Logger logger) {

        this.logger = logger;
        logger.log(String.format("[INFO] ListHandler called with StackId: [%s], RequestId: [%s], ", request.getStackId(), request.getClientRequestToken()));

        return proxy.initiate("AWS-ControlTower-LandingZone::List", proxyClient, request.getDesiredResourceState(), callbackContext)

                // STEP 1 [Construct a body of a request]
                .translateToServiceRequest((model) -> {
                    return Translator.translateToListRequest(request.getNextToken());
                })

                // STEP 2 [Make an api call]
                .makeServiceCall((listLandingZoneRequest, client) -> listResource(listLandingZoneRequest, client))
                .handleError((listLandingZoneRequest, exception, client, _model, context) -> handleError(listLandingZoneRequest, exception, _model, context, logger))

                // STEP 3 [Get a token for the next page]
                // STEP 4 [Construct resource models]
                .done(listLandingZonesResponse -> {
                    return ProgressEvent.<software.amazon.controltower.landingzone.ResourceModel, software.amazon.controltower.landingzone.CallbackContext>builder()
                            .resourceModels(Translator.translateFromListRequest(listLandingZonesResponse))
                            .status(OperationStatus.SUCCESS)
                            .nextToken(listLandingZonesResponse.nextToken())
                            .build();
                });
    }

    private ListLandingZonesResponse listResource(
            final ListLandingZonesRequest listLandingZonesRequest,
            final ProxyClient<ControlTowerClient> client) {
        logger.log(String.format("[INFO] Invoking ListLandingZone."));
        ListLandingZonesResponse listLandingZonesResponse = client.injectCredentialsAndInvokeV2(listLandingZonesRequest, client.client()::listLandingZones);
        logger.log(String.format("[INFO] ListLandingZone invoked successfully."));
        return listLandingZonesResponse;
    }
}
