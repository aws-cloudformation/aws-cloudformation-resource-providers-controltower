package software.amazon.controltower.landingzone;

import java.util.ArrayList;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.controltower.ControlTowerClient;
import software.amazon.awssdk.services.controltower.model.ControlTowerException;
import software.amazon.awssdk.services.controltower.model.InternalServerException;
import software.amazon.awssdk.services.controltower.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.controltower.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.controltower.model.ResourceNotFoundException;
import software.amazon.awssdk.services.controltower.model.TagResourceRequest;
import software.amazon.awssdk.services.controltower.model.TagResourceResponse;
import software.amazon.awssdk.services.controltower.model.UntagResourceRequest;
import software.amazon.awssdk.services.controltower.model.UntagResourceResponse;
import software.amazon.awssdk.services.controltower.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import software.amazon.controltower.landingzone.Tag;

@ExtendWith(MockitoExtension.class)
public class TagHelperTest extends AbstractTestBase {
    private static final List<Tag> NEW_TAGS = new ArrayList<Tag>(){{
        add(Tag.builder().key("key1").value("v1").build());
    }};
    private static final Map<String, String> NEW_TAG_MAP = new HashMap<String, String>() {{ put("key1", "v1"); }};
    private static final Set<String> OLD_TAG_KEYS = new HashSet<String>() {{ add("key2"); }};

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private ProxyClient<ControlTowerClient> proxyClient;

    @Mock
    private ControlTowerClient sdkClient;

    private final TagHelper tagHelper = new TagHelper();

    private final ResourceModel model = ResourceModel.builder()
            .version(VERSION)
            .manifest(MANIFEST)
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .tags(TAGS)
            .build();
    private final ResourceModel previousModel = ResourceModel.builder()
            .version(VERSION)
            .manifest(MANIFEST)
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .tags(new ArrayList<Tag>())
            .build();
    private final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceState(model)
            .previousResourceState(previousModel)
            .build();

    @BeforeEach
    public void setup() {
        proxy = new AmazonWebServicesClientProxy(logger, MOCK_CREDENTIALS, () -> Duration.ofSeconds(600).toMillis());
        sdkClient = mock(ControlTowerClient.class);
        proxyClient = MOCK_PROXY(proxy, sdkClient);
    }

    @Test
    public void test_generateTagsToAdd() {
        Map<String, String> tagsToAdd = tagHelper.generateTagsToAdd(TAG_MAP, NEW_TAG_MAP);
        assertThat(tagsToAdd).isEqualTo(NEW_TAG_MAP);
    }

    @Test
    public void test_generateTagsToRemove() {
        Set<String> tagsToRemove = tagHelper.generateTagsToRemove(TAG_MAP, NEW_TAG_MAP);
        assertThat(tagsToRemove).isEqualTo(OLD_TAG_KEYS);
    }

    @Test
    public void test_generateTagsToRemoveSystemTagIncluded() {
        final Map<String, String> existingTagMap = new HashMap<String, String>(){{
            put("key1", "v1");
            put("aws:reserved-tag", "v2");
            put("key2", "v3");
        }};

        final Map<String, String> desiredTagMap = new HashMap<String, String>(){{
            put("key2", "v3");
        }};

        final Set<String> expectedKeysToRemove = new HashSet<String>() {{
            add("key1");
        }};
        Set<String> tagsToRemove = tagHelper.generateTagsToRemove(existingTagMap, desiredTagMap);
        assertThat(tagsToRemove).isEqualTo(expectedKeysToRemove);
    }

    @Test
    public void listTagsForResource_SimpleSuccess() {
        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponse();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = tagHelper.listTagsForResource(proxy, proxyClient, model, request, new CallbackContext(), logger, false);

        assertSuccess(response);
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void listTagsForResource_withPreviousStateTagUpdateFalse_SimpleSuccess() {
        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponse();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = tagHelper.listTagsForResource(proxy, proxyClient, model, request, new CallbackContext(), logger, false);

        assertSuccess(response);
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void listTagsForResource_withPreviousStateTagUpdateTrue_SimpleSuccess() {
        ListTagsForResourceResponse listTagsForResourceResponse = buildListTagsForResourceResponse();
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(listTagsForResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = tagHelper.listTagsForResource(proxy, proxyClient, model, request, new CallbackContext(), logger, true);

        assertSuccess(response);
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @Test
    public void tagResource_SimpleSuccess() {
        TagResourceResponse tagResourceResponse = buildtagResourceResponse();
        when(proxyClient.client().tagResource(any(TagResourceRequest.class))).thenReturn(tagResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = tagHelper.tagResource(proxy, proxyClient, model, request, new CallbackContext(), TAG_MAP, logger);

        assertSuccess(response);
        verify(sdkClient, atLeastOnce()).tagResource(any(TagResourceRequest.class));
    }

    @Test
    public void tagResource_NoTagsToAdd() {
        final ProgressEvent<ResourceModel, CallbackContext> response = tagHelper.tagResource(proxy, proxyClient, model, request, new CallbackContext(), new HashMap<>(), logger);

        assertSuccess(response);
        verify(sdkClient, never()).tagResource(any(TagResourceRequest.class));
    }

    @Test
    public void untagResource_SimpleSuccess() {
        UntagResourceResponse untagResourceResponse = buildUntagResourceRequest();
        when(proxyClient.client().untagResource(any(UntagResourceRequest.class))).thenReturn(untagResourceResponse);

        final ProgressEvent<ResourceModel, CallbackContext> response = tagHelper.untagResource(proxy, proxyClient, model, request, new CallbackContext(), TAG_MAP.keySet(), logger);

        assertSuccess(response);
        verify(sdkClient, atLeastOnce()).untagResource(any(UntagResourceRequest.class));
    }

    @Test
    public void untagResource_NoTagsToRemove() {
        final ProgressEvent<ResourceModel, CallbackContext> response = tagHelper.untagResource(proxy, proxyClient, model, request, new CallbackContext(), new HashSet<>(), logger);

        assertSuccess(response);
        verify(sdkClient, never()).untagResource(any(UntagResourceRequest.class));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw")
    public void listTagsForResource_throwsException(Class<Exception> expectedException) {
        when(proxyClient.client().listTagsForResource(any(ListTagsForResourceRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = tagHelper.listTagsForResource(proxy, proxyClient, model, request, new CallbackContext(), logger, false);

        assertFailed(response);
        verify(sdkClient, atLeastOnce()).listTagsForResource(any(ListTagsForResourceRequest.class));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw")
    public void tagResource_throwsException(Class<Exception> expectedException) {
        when(proxyClient.client().tagResource(any(TagResourceRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = tagHelper.tagResource(proxy, proxyClient, model, request, new CallbackContext(), TAG_MAP, logger);

        assertFailed(response);
        verify(sdkClient, atLeastOnce()).tagResource(any(TagResourceRequest.class));
    }

    @ParameterizedTest
    @MethodSource("exception_to_throw")
    public void untagResource_throwsException(Class<Exception> expectedException) {
        when(proxyClient.client().untagResource(any(UntagResourceRequest.class))).thenThrow(expectedException);

        final ProgressEvent<ResourceModel, CallbackContext> response = tagHelper.untagResource(proxy, proxyClient, model, request, new CallbackContext(), TAG_MAP.keySet(), logger);

        assertFailed(response);
        verify(sdkClient, atLeastOnce()).untagResource(any(UntagResourceRequest.class));
    }

    @Test
    public void convertTags_convertList() {
        final List<Map<String, String>> listOfMaps = new ArrayList<Map<String, String>>() {{
            add(new HashMap<String, String>(){{
                put("Key", "k1");
                put("Value", "v1");
            }});
            add(new HashMap<String, String>(){{
                put("Key", "k2");
                put("Value", "v2");
            }});
        }};

        final Map<String, String> expectedMap = new HashMap<String, String>() {{
            put("k1", "v1");
            put("k2", "v2");
        }};

        assertThat(TagHelper.convertTags(listOfMaps)).isEqualTo(expectedMap);
    }

    @Test
    public void convertTags_convertEmptyList() {
        final List<Map<String, String>> listOfMaps = new ArrayList<Map<String, String>>();

        final Map<String, String> expectedMap = new HashMap<String, String>();

        assertThat(TagHelper.convertTags(listOfMaps)).isEqualTo(expectedMap);
    }

    @Test
    public void convertTags_convertMap() {
        final Map<String, String> map = new HashMap<String, String>() {{
            put("k1", "v1");
            put("k2", "v2");
        }};

        final List<Map<String, String>> expectedListOfMaps = new ArrayList<Map<String, String>>() {{
            add(new HashMap<String, String>(){{
                put("Key", "k1");
                put("Value", "v1");
            }});
            add(new HashMap<String, String>(){{
                put("Key", "k2");
                put("Value", "v2");
            }});
        }};

        assertThat(TagHelper.convertTags(map)).isEqualTo(expectedListOfMaps);
    }

    @Test
    public void convertTags_convertEmptyMap() {
        final Map<String, String> map = new HashMap<String, String>();

        final List<Map<String, String>> expectedListOfMaps = new ArrayList<Map<String, String>>();

        assertThat(TagHelper.convertTags(map)).isEqualTo(expectedListOfMaps);
    }

    @Test
    public void convertTagObjects_convertToList() {
        final List<Tag> tagList = new ArrayList<Tag>() {{
            add(Tag.builder().key("k1").value("v1").build());
            add(Tag.builder().key("k2").value("v2").build());
        }};

        final List<Map<String, String>> expectedListOfMaps = new ArrayList<Map<String, String>>() {{
            add(new HashMap<String, String>(){{
                put("Key", "k1");
                put("Value", "v1");
            }});
            add(new HashMap<String, String>(){{
                put("Key", "k2");
                put("Value", "v2");
            }});
        }};

        assertThat(TagHelper.convertTagObjects(tagList)).isEqualTo(expectedListOfMaps);
    }

    @Test
    public void convertTagObjects_emptyTagList() {
        final List<Tag> tagList = new ArrayList<Tag>();

        final List<Map<String, String>> expectedListOfMaps = new ArrayList<Map<String, String>>();

        assertThat(TagHelper.convertTagObjects(tagList)).isEqualTo(expectedListOfMaps);
    }

    @Test
    public void convertToTagObjects_convertList() {
        final List<Map<String, String>> listOfMaps = new ArrayList<Map<String, String>>() {{
            add(new HashMap<String, String>(){{
                put("Key", "k1");
                put("Value", "v1");
            }});
            add(new HashMap<String, String>(){{
                put("Key", "k2");
                put("Value", "v2");
            }});
        }};

        final List<Tag> expectedTagList = new ArrayList<Tag>() {{
            add(Tag.builder().key("k1").value("v1").build());
            add(Tag.builder().key("k2").value("v2").build());
        }};

        assertThat(TagHelper.convertToTagObjects(listOfMaps)).isEqualTo(expectedTagList);
    }

    @Test
    public void validateRequestDoesNotIncludeProhibitedTags_TagsAreValid() {
        final List<Tag> resourceTags = new ArrayList<Tag>();
        resourceTags.add(Tag.builder().key("key1").value("value2").build());
        resourceTags.add(Tag.builder().key("key2").value("value2").build());

        final Map<String, String> stackLevelTags = new HashMap<String, String>();
        stackLevelTags.put("key3", "value3");
        stackLevelTags.put("key4", "value4");

        final Map<String, String> systemTags = new HashMap<String, String>();
        systemTags.put("key5", "value5");
        systemTags.put("key6", "value6");

        final ResourceModel model = ResourceModel.builder()
            .manifest(MANIFEST)
            .arn(LANDING_ZONE_IDENTIFIER)
            .version(VERSION)
            .status(LANDING_ZONE_STATUS)
            .latestAvailableVersion(VERSION)
            .driftStatus(DRIFT_STATUS)
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .tags(resourceTags)
            .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceTags(stackLevelTags)
            .desiredResourceState(model)
            .systemTags(systemTags)
            .build();

        TagHelper.validateRequestDoesNotIncludeProhibitedTags(request);
    }

    @Test
    public void validateRequestDoesNotIncludeProhibitedTags_systemTagsIncludeReservedPrefix() {
        final List<Tag> resourceTags = new ArrayList<Tag>();
        resourceTags.add(Tag.builder().key("key1").value("value2").build());
        resourceTags.add(Tag.builder().key("key2").value("value2").build());

        final Map<String, String> stackLevelTags = new HashMap<String, String>();
        stackLevelTags.put("key3", "value3");
        stackLevelTags.put("key4", "value4");

        final Map<String, String> systemTags = new HashMap<String, String>();
        systemTags.put("aws:foo", "value5");
        systemTags.put("aws:bar", "value6");

        final ResourceModel model = ResourceModel.builder()
            .manifest(MANIFEST)
            .arn(LANDING_ZONE_IDENTIFIER)
            .version(VERSION)
            .status(LANDING_ZONE_STATUS)
            .latestAvailableVersion(VERSION)
            .driftStatus(DRIFT_STATUS)
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .tags(resourceTags)
            .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceTags(stackLevelTags)
            .desiredResourceState(model)
            .systemTags(systemTags)
            .build();

        TagHelper.validateRequestDoesNotIncludeProhibitedTags(request);
    }

    @Test
    public void validateRequestDoesNotIncludeProhibitedTags_prohibitedStackTags() {
        final List<Tag> resourceTags = new ArrayList<Tag>();
        resourceTags.add(Tag.builder().key("key1").value("value2").build());
        resourceTags.add(Tag.builder().key("key2").value("value2").build());

        final Map<String, String> stackLevelTags = new HashMap<String, String>();
        stackLevelTags.put("aws:foo", "value3");
        stackLevelTags.put("aws:bar", "value4");

        final Map<String, String> systemTags = new HashMap<String, String>();
        systemTags.put("key5", "value5");
        systemTags.put("key6", "value6");

        final ResourceModel model = ResourceModel.builder()
            .manifest(MANIFEST)
            .arn(LANDING_ZONE_IDENTIFIER)
            .version(VERSION)
            .status(LANDING_ZONE_STATUS)
            .latestAvailableVersion(VERSION)
            .driftStatus(DRIFT_STATUS)
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .tags(resourceTags)
            .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceTags(stackLevelTags)
            .desiredResourceState(model)
            .systemTags(systemTags)
            .build();

        assertThatThrownBy(() -> TagHelper.validateRequestDoesNotIncludeProhibitedTags(request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    public void validateRequestDoesNotIncludeProhibitedTags_prohibitedResourceTags() {
        final List<Tag> resourceTags = new ArrayList<Tag>();
        resourceTags.add(Tag.builder().key("aws:foo").value("value2").build());
        resourceTags.add(Tag.builder().key("aws:bar").value("value2").build());

        final Map<String, String> stackLevelTags = new HashMap<String, String>();
        stackLevelTags.put("key3", "value3");
        stackLevelTags.put("key4", "value4");

        final Map<String, String> systemTags = new HashMap<String, String>();
        systemTags.put("key5", "value5");
        systemTags.put("key6", "value6");

        final ResourceModel model = ResourceModel.builder()
            .manifest(MANIFEST)
            .arn(LANDING_ZONE_IDENTIFIER)
            .version(VERSION)
            .status(LANDING_ZONE_STATUS)
            .latestAvailableVersion(VERSION)
            .driftStatus(DRIFT_STATUS)
            .landingZoneIdentifier(LANDING_ZONE_IDENTIFIER)
            .tags(resourceTags)
            .build();
        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
            .desiredResourceTags(stackLevelTags)
            .desiredResourceState(model)
            .systemTags(systemTags)
            .build();

        assertThatThrownBy(() -> TagHelper.validateRequestDoesNotIncludeProhibitedTags(request))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    public void convertToTagObjects_emptyMapList() {
        final List<Map<String, String>> listOfMaps = new ArrayList<Map<String, String>>();

        final List<Tag> expectedTagList = new ArrayList<Tag>();

        assertThat(TagHelper.convertToTagObjects(listOfMaps)).isEqualTo(expectedTagList);
    }

    private static Stream<Arguments> exception_to_throw() {
        return Stream.of(
                Arguments.of(ValidationException.class),
                Arguments.of(InternalServerException.class),
                Arguments.of(ResourceNotFoundException.class),
                Arguments.of(ControlTowerException.class)
        );
    }

    private ListTagsForResourceResponse buildListTagsForResourceResponse() {
        return ListTagsForResourceResponse.builder()
                .tags(TAG_MAP)
                .build();
    }

    private UntagResourceResponse buildUntagResourceRequest() {
        return UntagResourceResponse.builder().build();
    }

    private TagResourceResponse buildtagResourceResponse() {
        return TagResourceResponse.builder().build();
    }

    private void assertSuccess(ProgressEvent<ResourceModel, CallbackContext> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.IN_PROGRESS);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel()).isEqualTo(request.getDesiredResourceState());
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    private void assertFailed(ProgressEvent<ResourceModel, CallbackContext> response) {
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getErrorCode()).isNotNull();
    }
}
