package software.amazon.controltower.enabledcontrol;

import com.amazonaws.services.controltower.AWSControlTower;
import com.amazonaws.services.controltower.model.AccessDeniedException;
import com.amazonaws.services.controltower.model.EnabledControlSummary;
import com.amazonaws.services.controltower.model.ListEnabledControlsRequest;
import com.amazonaws.services.controltower.model.ListEnabledControlsResult;
import com.amazonaws.services.controltower.model.ResourceNotFoundException;
import com.amazonaws.services.controltower.model.ThrottlingException;
import com.amazonaws.services.controltower.model.ValidationException;
import software.amazon.cloudformation.exceptions.CfnAccessDeniedException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNetworkFailureException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.exceptions.CfnThrottlingException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Optional;

import static software.amazon.controltower.enabledcontrol.HandlerUtils.logException;

public class ReadHandler extends BaseHandler<CallbackContext> {

    private final AWSControlTower controlTowerClient;
    private AmazonWebServicesClientProxy clientProxy;
    private Logger logger;

    public static final int MAX_RESULTS = 40;

    public ReadHandler() {
        controlTowerClient = ClientBuilder.getStandardClient(logger);
    }

    public ReadHandler(AWSControlTower awsControlTower) {
        controlTowerClient = awsControlTower;
    }

    @Override
    public ProgressEvent<ResourceModel, CallbackContext> handleRequest(
            final AmazonWebServicesClientProxy proxy,
            final ResourceHandlerRequest<ResourceModel> request,
            final CallbackContext callbackContext,
            final Logger logger) {

        final ResourceModel model = request.getDesiredResourceState();
        this.logger = logger;

        clientProxy = proxy;

        try {
            String nextToken = null;
            String controlIdentifier = model.getControlIdentifier();

            do {
                final ListEnabledControlsRequest listEnabledControlsRequest = new ListEnabledControlsRequest()
                        .withTargetIdentifier(model.getTargetIdentifier())
                        .withMaxResults(MAX_RESULTS)
                        .withNextToken(nextToken);
                final ListEnabledControlsResult listEnabledControlsResult = clientProxy.injectCredentialsAndInvoke(
                        listEnabledControlsRequest, controlTowerClient::listEnabledControls);
                nextToken = listEnabledControlsResult.getNextToken();

                Optional<EnabledControlSummary> controlSummaryOptional = listEnabledControlsResult.getEnabledControls().stream()
                        .filter(controlSummary -> controlSummary.getControlIdentifier().equals(controlIdentifier))
                        .findAny();

                if (controlSummaryOptional.isPresent()) {
                    return ProgressEvent.<ResourceModel, CallbackContext>builder()
                            .resourceModel(model)
                            .status(OperationStatus.SUCCESS)
                            .build();
                }
            } while (nextToken != null);
        } catch (AccessDeniedException e) {
            throw new CfnAccessDeniedException(e);
        } catch (ThrottlingException e) {
            throw new CfnThrottlingException(e);
        } catch (ValidationException e) {
            throw new CfnInvalidRequestException(e);
        } catch (ResourceNotFoundException e) {
            throw new CfnNotFoundException(e);
        } catch (Throwable e) {
            if (e.getMessage().contains("HttpTimeoutException")) {
                throw new CfnNetworkFailureException(e);
            }
            logException(e, this.logger);
            throw new CfnInternalFailureException(e);
        }
        return ProgressEvent.<ResourceModel, CallbackContext>builder()
                .resourceModel(model)
                .status(OperationStatus.FAILED)
                .errorCode(HandlerErrorCode.NotFound)
                .build();
    }
}
