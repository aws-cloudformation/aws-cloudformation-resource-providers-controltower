package software.amazon.controltower.landingzone;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import software.amazon.awssdk.awscore.AwsResponse;
// TODO: Critical! Please replace the CloudFormation Tag model below with your service's own SDK Tag model
import software.amazon.controltower.landingzone.Tag;
import software.amazon.awssdk.services.controltower.ControlTowerClient;
import software.amazon.awssdk.services.controltower.model.InternalServerException;
import software.amazon.awssdk.services.controltower.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.controltower.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.controltower.model.ResourceNotFoundException;
import software.amazon.awssdk.services.controltower.model.TagResourceResponse;
import software.amazon.awssdk.services.controltower.model.UntagResourceResponse;
import software.amazon.awssdk.services.controltower.model.ValidationException;
import software.amazon.cloudformation.exceptions.BaseHandlerException;
import software.amazon.cloudformation.exceptions.CfnGeneralServiceException;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class TagHelper {
    private static final String AWS_SYSTEM_TAG_PREFIX = "aws:";
    /**
     * shouldUpdateTags
     *
     * Determines whether user defined tags have been changed during update.
     */
    public final boolean shouldUpdateTags(final ResourceHandlerRequest<ResourceModel> handlerRequest) {
        final Map<String, String> previousTags = getPreviouslyAttachedTags(handlerRequest);
        final Map<String, String> desiredTags = getNewDesiredTags(handlerRequest);
        return ObjectUtils.notEqual(previousTags, desiredTags);
    }

    /**
     * getPreviouslyAttachedTags
     *
     * If stack tags and resource tags are not merged together in Configuration class,
     * we will get previously attached system (with `aws:cloudformation` prefix) and user defined tags from
     * handlerRequest.getPreviousSystemTags() (system tags),
     * handlerRequest.getPreviousResourceTags() (stack tags),
     * handlerRequest.getPreviousResourceState().getTags() (resource tags).
     *
     * System tags are an optional feature. Merge them to your tags if you have enabled them for your resource.
     * System tags can change on resource update if the resource is imported to the stack.
     */
    public Map<String, String> getPreviouslyAttachedTags(final ResourceHandlerRequest<ResourceModel> handlerRequest) {
        final Map<String, String> previousTags = new HashMap<>();

        // get previous system tags if your service supports CloudFormation system tags
        if (handlerRequest.getPreviousSystemTags() != null) {
            previousTags.putAll(handlerRequest.getPreviousSystemTags());
        }

        // get previous stack level tags from handlerRequest
        if (handlerRequest.getPreviousResourceTags() != null) {
            previousTags.putAll(handlerRequest.getPreviousResourceTags());
        }

        // get resource level tags from previous resource state based on your tag property name
        if (handlerRequest.getPreviousResourceState().getTags() != null) {
            previousTags.putAll(convertTags(convertTagObjects(handlerRequest.getPreviousResourceState().getTags())));
        }
        return previousTags;
    }

    /**
     * getNewDesiredTags
     *
     * If stack tags and resource tags are not merged together in Configuration class,
     * we will get new desired system (with `aws:cloudformation` prefix) and user defined tags from
     * handlerRequest.getSystemTags() (system tags),
     * handlerRequest.getDesiredResourceTags() (stack tags),
     * handlerRequest.getDesiredResourceState().getTags() (resource tags).
     *
     * System tags are an optional feature. Merge them to your tags if you have enabled them for your resource.
     * System tags can change on resource update if the resource is imported to the stack.
     */
    public Map<String, String> getNewDesiredTags(final ResourceHandlerRequest<ResourceModel> handlerRequest) {
        final Map<String, String> desiredTags = new HashMap<>();

        // merge system tags with desired resource tags if your service supports CloudFormation system tags
        if (handlerRequest.getSystemTags() != null) {
            desiredTags.putAll(handlerRequest.getSystemTags());
        }

        // get desired stack level tags from handlerRequest
        if (handlerRequest.getDesiredResourceTags() != null) {
            desiredTags.putAll(handlerRequest.getDesiredResourceTags());
        }

        // get resource level tags from resource model based on your tag property name
        if (handlerRequest.getDesiredResourceState().getTags() != null) {
            desiredTags.putAll(convertTags(convertTagObjects(handlerRequest.getDesiredResourceState().getTags())));
        }
        return desiredTags;
    }

    /**
     * generateTagsToAdd
     *
     * Determines the tags the customer desired to define or redefine.
     */
    public Map<String, String> generateTagsToAdd(final Map<String, String> previousTags, final Map<String, String> desiredTags) {
        return desiredTags.entrySet().stream()
            .filter(e -> !previousTags.containsKey(e.getKey()) || !Objects.equals(previousTags.get(e.getKey()), e.getValue()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue));
    }

    /**
     * getTagsToRemove
     *
     * Determines the tags the customer desired to remove from the function.
     */
    public Set<String> generateTagsToRemove(final Map<String, String> previousTags, final Map<String, String> desiredTags) {
        final Set<String> desiredTagNames = desiredTags.keySet();

        return previousTags.keySet().stream()
            .filter(tagName -> !desiredTagNames.contains(tagName))
            .filter(tagName -> !tagName.startsWith(AWS_SYSTEM_TAG_PREFIX))
            .collect(Collectors.toSet());
    }

    /**
     * tagResource during update
     *
     * Calls the service:TagResource API.
     */
    public ProgressEvent<ResourceModel, CallbackContext> tagResource(
            final AmazonWebServicesClientProxy proxy,
            final ProxyClient<ControlTowerClient> serviceClient,
            final ResourceModel resourceModel,
            final ResourceHandlerRequest<ResourceModel> handlerRequest,
            final CallbackContext callbackContext,
            final Map<String, String> addedTags,
            final Logger logger) {
        logger.log(String.format("[INFO] [UPDATE][IN PROGRESS]  Going to add tags for resource: %s with AccountId: %s",
                resourceModel.TYPE_NAME, handlerRequest.getAwsAccountId()));

        if (addedTags.isEmpty()) {
            logger.log("No tags to add, skipping service call");
            return ProgressEvent.progress(resourceModel, callbackContext);
        }

        return proxy.initiate("AWS-ControlTower-LandingZone::Tag", serviceClient, resourceModel, callbackContext)
                .translateToServiceRequest(model -> Translator.tagResourceRequest(model, addedTags))
                .makeServiceCall((tagResourceRequest, client) -> {
                    logger.log(String.format("[INFO] Invoking TagResource."));
                    TagResourceResponse tagResourceResponse = client.injectCredentialsAndInvokeV2(tagResourceRequest, client.client()::tagResource);
                    logger.log(String.format("[INFO] TagResource invoked successfully."));
                    return tagResourceResponse;
                })
                .handleError((tagResourceRequest, exception, client, _model, context) -> handleError(exception, _model, context, logger))
                .progress();
    }

    /**
     * untagResource during update
     *
     * Calls the service:UntagResource API.
     */
    public ProgressEvent<ResourceModel, CallbackContext> untagResource(
            final AmazonWebServicesClientProxy proxy,
            final ProxyClient<ControlTowerClient> serviceClient,
            final ResourceModel resourceModel,
            final ResourceHandlerRequest<ResourceModel> handlerRequest,
            final CallbackContext callbackContext,
            final Set<String> removedTags,
            final Logger logger) {
        logger.log(String.format("[INFO] [UPDATE][IN PROGRESS]  Going to remove tags for resource: %s with AccountId: %s",
                resourceModel.TYPE_NAME, handlerRequest.getAwsAccountId()));

        if (removedTags.isEmpty()) {
            logger.log("No tags to remove, skipping service call");
            return ProgressEvent.progress(resourceModel, callbackContext);
        }

        return proxy.initiate("AWS-ControlTower-LandingZone::Untag", serviceClient, resourceModel, callbackContext)
                .translateToServiceRequest(model -> Translator.untagResourceRequest(model, removedTags))
                .makeServiceCall((untagResourceRequest, client) -> {
                    logger.log(String.format("[INFO] Invoking UntagResource."));
                    UntagResourceResponse untagResourceResponse = client.injectCredentialsAndInvokeV2(untagResourceRequest, client.client()::untagResource);
                    logger.log(String.format("[INFO] UntagResource invoked successfully."));
                    return untagResourceResponse;
                })
                .handleError((listTagsForResourceRequest, exception, client, _model, context) -> handleError(exception, _model, context, logger))
                .progress();
    }

    public ProgressEvent<ResourceModel, CallbackContext> listTagsForResource(
            final AmazonWebServicesClientProxy proxy,
            final ProxyClient<ControlTowerClient> serviceClient,
            final ResourceModel resourceModel,
            final ResourceHandlerRequest<ResourceModel> handlerRequest,
            final CallbackContext callbackContext,
            final Logger logger,
            final boolean updatePreviousResourceTags) {
        logger.log(String.format("[INFO] Listing tags for resource: %s with AccountId: %s",
                resourceModel.TYPE_NAME, handlerRequest.getAwsAccountId()));

        return proxy.initiate("AWS-ControlTower-LandingZone::ListTags", serviceClient, resourceModel, callbackContext)
                .translateToServiceRequest(model -> Translator.listTagsForResourceRequest(model))
                .makeServiceCall((listTagsForResourceRequest, client) -> {
                    logger.log(String.format("[INFO] Invoking ListTagsForResource."));
                    ListTagsForResourceResponse listTagsForResourceResponse = client.injectCredentialsAndInvokeV2(listTagsForResourceRequest, client.client()::listTagsForResource);
                    logger.log(String.format("[INFO] ListTagsForResource invoked successfully."));

                    if (!listTagsForResourceResponse.tags().isEmpty()) {
                        if (!updatePreviousResourceTags) {
                            resourceModel.setTags(convertToTagObjects(convertTags(listTagsForResourceResponse.tags())));
                        } else {
                            ResourceModel previousModel = handlerRequest.getPreviousResourceState();
                            previousModel.setTags(convertToTagObjects(convertTags(listTagsForResourceResponse.tags())));
                            handlerRequest.setPreviousResourceState(previousModel);
                        }
                    }

                    return listTagsForResourceResponse;
                })
                .handleError((listTagsForResourceRequest, exception, client, _model, context) -> handleError(exception, _model, context, logger))
                .progress();
    }

    protected ProgressEvent<ResourceModel, CallbackContext> handleError(
            final Exception e,
            final ResourceModel resourceModel,
            final CallbackContext callbackContext,
            final Logger logger) {
        BaseHandlerException ex;
        logger.log(String.format("[Error] received for %s with error %s", resourceModel.getLandingZoneIdentifier(), e.getMessage()));

        if (e instanceof InternalServerException) {
            ex = new CfnInternalFailureException(e);
        } else if (e instanceof ValidationException) {
            ex = new CfnInvalidRequestException(e);
        } else if (e instanceof ResourceNotFoundException) {
            ex = new CfnNotFoundException(e);
        } else {
            ex = new CfnGeneralServiceException(e);
        }

        return ProgressEvent.failed(resourceModel, callbackContext, ex.getErrorCode(), ex.getMessage());
    }

    public static List<Map<String, String>> convertTags(final Map<String, String> tagMap) {
        return tagMap.entrySet()
            .stream()
            .map((entry) -> {
                return new HashMap<String, String>(){{
                    put("Key", entry.getKey());
                    put("Value", entry.getValue());
                }};
            })
            .collect(Collectors.toList());
    }

    public static Map<String, String> convertTags(final List<Map<String, String>> tagList) {
        return tagList.stream()
            .reduce(new HashMap<String, String>(),
                    (hashMap, tag) -> {
                        hashMap.put(tag.get("Key"), tag.get("Value"));
                        return hashMap;
                    });
    }

    public static List<Map<String, String>> convertTagObjects(final List<Tag> tagList) {
        return tagList.stream()
            .map((tag) -> {
                return new HashMap<String, String>(){{
                    put("Key", tag.getKey());
                    put("Value", tag.getValue());
                }};
            })
            .collect(Collectors.toList());
    }

    public static List<Tag> convertToTagObjects(final List<Map<String, String>> tagList) {
        return tagList.stream()
            .map((tag) -> Tag.builder().key(tag.get("Key")).value(tag.get("Value")).build())
            .collect(Collectors.toList());
    }

    public static void validateRequestDoesNotIncludeProhibitedTags(final ResourceHandlerRequest<ResourceModel> handlerRequest) {
        if (handlerRequest.getDesiredResourceTags() != null) {
            convertTags(handlerRequest.getDesiredResourceTags()).stream().forEach((tag) -> {
                if (tag.get("Key").startsWith(AWS_SYSTEM_TAG_PREFIX)) {
                    throw ValidationException.builder()
                        .message(String.format("Stack-level tag supplied with reserved prefix: %s",
                            AWS_SYSTEM_TAG_PREFIX))
                        .build();
                }
            });
        }

        if (handlerRequest.getDesiredResourceState().getTags() != null) {
            handlerRequest.getDesiredResourceState().getTags().stream().forEach((tag) -> {
                if (tag.getKey().startsWith(AWS_SYSTEM_TAG_PREFIX)) {
                    throw ValidationException.builder()
                        .message(String.format("Resource-level tag supplied with reserved prefix: %s",
                            AWS_SYSTEM_TAG_PREFIX))
                        .build();
                }
            });
        }

    }
}
