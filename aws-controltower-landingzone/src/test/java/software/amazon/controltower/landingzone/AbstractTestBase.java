package software.amazon.controltower.landingzone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.controltower.ControlTowerClient;
import software.amazon.awssdk.services.controltower.model.AccessDeniedException;
import software.amazon.awssdk.services.controltower.model.ConflictException;
import software.amazon.awssdk.services.controltower.model.InternalServerException;
import software.amazon.awssdk.services.controltower.model.ResourceNotFoundException;
import software.amazon.awssdk.services.controltower.model.ThrottlingException;
import software.amazon.awssdk.services.controltower.model.ValidationException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Credentials;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.LoggerProxy;
import software.amazon.cloudformation.proxy.ProxyClient;

import software.amazon.controltower.landingzone.Tag;


public class AbstractTestBase {
    protected static final String VERSION = "3.2";
    protected static final Map<String, Object> MANIFEST = new HashMap<>();
    protected static final String LANDING_ZONE_IDENTIFIER = "arn:aws:controltower:us-west-2:123456789012:landingzone/1A2B3C4D5E6F7G8H";
    protected static final String OPERATION_IDENTIFIER = UUID.randomUUID().toString();
    protected static final String NEXT_TOKEN = "1234567890";
    protected static final Credentials MOCK_CREDENTIALS;
    protected static final LoggerProxy logger;
    protected static final Map<Class<? extends Exception>, HandlerErrorCode> EXCEPTION_TO_ERROR_CODE_MAP = new HashMap<>();
    protected static final List TAGS = new ArrayList<>();
    protected static final Map<String, String> TAG_MAP = new HashMap<>();
    protected static final String LANDING_ZONE_STATUS = "MOCK_STATUS";
    protected static final String DRIFT_STATUS = "MOCK_STATUS";


    static {
        MOCK_CREDENTIALS = new Credentials("accessKey", "secretKey", "token");
        logger = new LoggerProxy();
        EXCEPTION_TO_ERROR_CODE_MAP.put(AccessDeniedException.class, HandlerErrorCode.AccessDenied);
        EXCEPTION_TO_ERROR_CODE_MAP.put(ThrottlingException.class, HandlerErrorCode.Throttling);
        EXCEPTION_TO_ERROR_CODE_MAP.put(ValidationException.class, HandlerErrorCode.InvalidRequest);
        EXCEPTION_TO_ERROR_CODE_MAP.put(InternalServerException.class, HandlerErrorCode.InternalFailure);
        EXCEPTION_TO_ERROR_CODE_MAP.put(ResourceNotFoundException.class, HandlerErrorCode.NotFound);
        EXCEPTION_TO_ERROR_CODE_MAP.put(ConflictException.class, HandlerErrorCode.ResourceConflict);
        MANIFEST.put("dummyKey", "dummyValue");
     TAGS.add(Tag.builder().key("key1").value("value2").build());
     TAGS.add(Tag.builder().key("key2").value("value2").build());
     TAG_MAP.put("key1", "value2");
     TAG_MAP.put("key2", "value2");
    }

    static ProxyClient<ControlTowerClient> MOCK_PROXY(
            final AmazonWebServicesClientProxy proxy,
            final ControlTowerClient sdkClient) {
        return new ProxyClient<ControlTowerClient>() {
            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseT
            injectCredentialsAndInvokeV2(RequestT request, Function<RequestT, ResponseT> requestFunction) {
                return proxy.injectCredentialsAndInvokeV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse>
            CompletableFuture<ResponseT>
            injectCredentialsAndInvokeV2Async(RequestT request, Function<RequestT, CompletableFuture<ResponseT>> requestFunction) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse, IterableT extends SdkIterable<ResponseT>>
            IterableT
            injectCredentialsAndInvokeIterableV2(RequestT request, Function<RequestT, IterableT> requestFunction) {
                return proxy.injectCredentialsAndInvokeIterableV2(request, requestFunction);
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseInputStream<ResponseT>
            injectCredentialsAndInvokeV2InputStream(RequestT requestT, Function<RequestT, ResponseInputStream<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <RequestT extends AwsRequest, ResponseT extends AwsResponse> ResponseBytes<ResponseT>
            injectCredentialsAndInvokeV2Bytes(RequestT requestT, Function<RequestT, ResponseBytes<ResponseT>> function) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ControlTowerClient client() {
                return sdkClient;
            }
        };
    }
}
