package software.amazon.controltower.enabledcontrol;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.handlers.RequestHandler2;

public class CloudformationUserAgentRequestHandler extends RequestHandler2 {
    private static final String CLOUDFORMATION_USER_AGENT =  "ct-cfn-enabled-control";

    @Override
    public AmazonWebServiceRequest beforeExecution(AmazonWebServiceRequest request) {
        request.putCustomRequestHeader("User-Agent", CLOUDFORMATION_USER_AGENT);
        return request;
    }

}
