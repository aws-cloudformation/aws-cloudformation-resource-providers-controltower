package software.amazon.controltower.landingzone;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import converters.DocumentConverter;
import software.amazon.awssdk.services.controltower.model.CreateLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.DeleteLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneOperationRequest;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneRequest;
import software.amazon.awssdk.services.controltower.model.GetLandingZoneResponse;
import software.amazon.awssdk.services.controltower.model.LandingZoneDetail;
import software.amazon.awssdk.services.controltower.model.ListLandingZonesRequest;
import software.amazon.awssdk.services.controltower.model.ListLandingZonesResponse;
import software.amazon.awssdk.services.controltower.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.controltower.model.TagResourceRequest;
import software.amazon.awssdk.services.controltower.model.UntagResourceRequest;
import software.amazon.awssdk.services.controltower.model.UpdateLandingZoneRequest;

/**
 * This class is a centralized placeholder for
 * - api request construction
 * - object translation to/from aws sdk
 * - resource model construction for read/list handlers
 */
public class Translator {
    private static final Integer LIST_LANDING_ZONE_MAX_RESULTS = 1;
    private static final DocumentConverter converter = new DocumentConverter();

    /**
     * Request to create a resource
     *
     * @param model resource model
     * @return CreateLandingZoneRequest the aws service request to create a resource
     */
    static Map<String, Object> translateToCreateRequest(final ResourceModel model, Map<String, String> tags) {
        /**
         * There is currently an issue wherein the serializer used by Uluru cannot serialize
         * Smithy-defined Document types. To get around this, we create a generic map that
         * Uluru can successfully pass around and add into the StdCallbackContext's call
         * graph. Note that this works because Manifest can only be a map; for APIs that
         * realize the Document type as other things, swap the below out with appropriate
         * serialization logic.
         */
        converter.modifyTypesOfManifestParameters(model.getManifest());
        final Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("Version", model.getVersion());
        requestMap.put("Manifest", model.getManifest());
        requestMap.put("Tags", tags);
        return requestMap;
    }

    static CreateLandingZoneRequest translateToCreateRequest(final Map<String, Object> requestMap) {
        return CreateLandingZoneRequest.builder()
            .version((String) requestMap.get("Version"))
            .manifest(converter.toDocument((HashMap<String, Object>) requestMap.get("Manifest")))
            .tags((Map<String, String>) requestMap.get("Tags"))
            .build();
    }

    /**
     * Request to read a resource
     *
     * @param model resource model
     * @return GetLandingZoneRequest the aws service request to describe a resource
     */
    static GetLandingZoneRequest translateToReadRequest(final ResourceModel model) {
        return GetLandingZoneRequest.builder()
                .landingZoneIdentifier(model.getLandingZoneIdentifier())
                .build();
    }

    /**
     * Request to read a resource
     *
     * @param string operationIdentifier
     * @return GetLandingZoneOperationRequest the aws service request to describe a resource
     */
    static GetLandingZoneOperationRequest translateToGetLandingZoneOperationReadRequest(final String operationIdentifier) {
        return GetLandingZoneOperationRequest.builder()
                .operationIdentifier(operationIdentifier)
                .build();
    }

    /**
     * Translates resource object from sdk into a resource model
     *
     * @param awsResponse the aws service describe resource response
     * @return model resource model
     */
    static ResourceModel translateFromReadResponse(final GetLandingZoneResponse getLandingZoneResponse, final ResourceModel model) {
        LandingZoneDetail landingZoneDetail = getLandingZoneResponse.landingZone();
        return ResourceModel.builder()
                .landingZoneIdentifier(landingZoneDetail.arn())
                .manifest(converter.toMap(landingZoneDetail.manifest()))
                .arn(landingZoneDetail.arn())
                .version(landingZoneDetail.latestAvailableVersion())
                .status(landingZoneDetail.statusAsString())
                .latestAvailableVersion(landingZoneDetail.latestAvailableVersion())
                .driftStatus(landingZoneDetail.driftStatus().statusAsString())
                .tags(model.getTags())
                .build();
    }

    /**
     * Request to delete a resource
     *
     * @param model resource model
     * @return DeleteLandingZoneRequest the aws service request to delete a resource
     */
    static DeleteLandingZoneRequest translateToDeleteRequest(final ResourceModel model) {
        return DeleteLandingZoneRequest.builder()
                .landingZoneIdentifier(model.getLandingZoneIdentifier())
                .build();
    }

    /**
     * Request to update properties of a previously created resource
     *
     * @param model resource model
     * @return awsRequest the aws service request to modify a resource
     */
    static Map<String, Object> translateToUpdateRequest(final ResourceModel model) {
         /**
         * There is currently an issue wherein the serializer used by Uluru cannot serialize
         * Smithy-defined Document types. To get around this, we create a generic map that
         * Uluru can successfully pass around and add into the StdCallbackContext's call
         * graph. Note that this works because Manifest can only be a map; for APIs that
         * realize the Document type as other things, swap the below out with appropriate
         * serialization logic.
         */
        converter.modifyTypesOfManifestParameters(model.getManifest());
        final Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("Version", model.getVersion());
        requestMap.put("Manifest", model.getManifest());
        requestMap.put("LandingZoneIdentifier", model.getLandingZoneIdentifier());
        return requestMap;
    }

    static UpdateLandingZoneRequest translateToUpdateRequest(final Map<String, Object> requestMap) {
        return UpdateLandingZoneRequest.builder()
            .version((String) requestMap.get("Version"))
            .manifest(converter.toDocument((HashMap<String, Object>) requestMap.get("Manifest")))
            .landingZoneIdentifier((String) requestMap.get("LandingZoneIdentifier"))
            .build();
    }

    /**
     * Request to list resources
     *
     * @param nextToken token passed to the aws service list resources request
     * @return ListLandingZonesRequest the aws service request to list resources within aws account
     */
    static ListLandingZonesRequest translateToListRequest(final String nextToken) {
        return ListLandingZonesRequest.builder()
                .maxResults(LIST_LANDING_ZONE_MAX_RESULTS)
                .nextToken(nextToken)
                .build();
    }

    /**
     * Translates resource objects from sdk into a resource model (primary identifier only)
     *
     * @param awsResponse the aws service describe resource response
     * @return list of resource models
     */
    static List<ResourceModel> translateFromListRequest(final ListLandingZonesResponse listLandingZonesResponse) {
        return streamOfOrEmpty(listLandingZonesResponse.landingZones())
                .map(landingZone -> ResourceModel.builder()
                        // include only primary identifier
                        .landingZoneIdentifier(landingZone.arn())
                        .build())
                .collect(Collectors.toList());
    }

    private static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
        return Optional.ofNullable(collection)
                .map(Collection::stream)
                .orElseGet(Stream::empty);
    }

    /**
     * Request to add tags to a resource
     *
     * @param model resource model
     * @return awsRequest the aws service request to create a resource
     */
    static TagResourceRequest tagResourceRequest(final ResourceModel model, final Map<String, String> addedTags) {
        return TagResourceRequest.builder()
                .resourceArn(model.getLandingZoneIdentifier())
                .tags(addedTags)
                .build();
    }

    /**
     * Request to add tags to a resource
     *
     * @param model resource model
     * @return awsRequest the aws service request to create a resource
     */
    static UntagResourceRequest untagResourceRequest(final ResourceModel model, final Set<String> removedTags) {
        return UntagResourceRequest.builder()
                .resourceArn(model.getLandingZoneIdentifier())
                .tagKeys(removedTags)
                .build();
    }

    static ListTagsForResourceRequest listTagsForResourceRequest(final ResourceModel model) {
        return ListTagsForResourceRequest.builder()
                .resourceArn(model.getLandingZoneIdentifier())
                .build();
    }
}
