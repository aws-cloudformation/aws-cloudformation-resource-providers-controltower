package converters;

import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.protocols.json.internal.unmarshall.document.DocumentUnmarshaller;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;

public class DocumentConverter {
    private static final String LOGGING_BUCKET = "loggingBucket";
    private static final String ACCESS_LOGGING_BUCKET = "accessLoggingBucket";
    private static final String ACCESS_MANAGEMENT = "accessManagement";
    private static final String ENABLED = "enabled";

    private static final String CENTRALIZED_LOGGING = "centralizedLogging";
    private static final String CONFIGURATIONS = "configurations";
    private static final String RETENTION_DAYS = "retentionDays";
    private static final String TRUE = "true";
    private static final String FALSE = "false";


    private final ObjectMapper objectMapper = new ObjectMapper();

    public Document toDocument(final Map<String, Object> objectMap) {
        DocumentUnmarshaller documentUnmarshaller = new DocumentUnmarshaller();
        if (objectMap == null) {
            return null;
        }

        final ObjectMapper objectMapper = new ObjectMapper();
        final JsonNodeParser jsonNodeParser = JsonNodeParser.create();

        final Map<String, JsonNode> jsonNodeMap = objectMap.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    try {
                        return jsonNodeParser.parse(objectMapper.writeValueAsString(e.getValue()));
                    } catch (JsonProcessingException ex) {
                        throw new IllegalArgumentException(ex.getMessage(), ex.getCause());
                    }
                }));

        Map<String, Document> documentMap = documentUnmarshaller.visitObject(jsonNodeMap).asMap();
        return Document.fromMap(documentMap);
    }

    public Map<String, Object> toMap(final Document document) {
        final Map<String, Object> manifest = document.asMap()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().unwrap()));
        return manifest;
    }

    public void modifyTypesOfManifestParameters(Map<String, Object> manifest) {
        try {
            // convert accessManagement.enabled to boolean
            modifyTypeOfAccessManagementParameter(manifest);

            // convert centralizedLogging.enabled to boolean
            modifyTypeOfCentralizedLoggingEnabledParameter(manifest);

            // convert centralizedLogging.configurations.loggingBucket.retentionDays to Integer
            modifyTypeOfRetentionDays(manifest, LOGGING_BUCKET);

            // convert centralizedLogging.configurations.accessLoggingBucket.retentionDays to Integer
            modifyTypeOfRetentionDays(manifest, ACCESS_LOGGING_BUCKET);
        } catch (Exception e) {
            throw new CfnInvalidRequestException(e);
        }
    }

    private void modifyTypeOfAccessManagementParameter(Map<String, Object> manifest) {
        // convert accessManagement.enabled to boolean
        if (manifest.containsKey(ACCESS_MANAGEMENT)) {
            Map<String, Object> accessManagementMap = (Map<String, Object>) manifest.get(ACCESS_MANAGEMENT);
            if (accessManagementMap.containsKey(ENABLED)) {
                String enabledValue = accessManagementMap.get(ENABLED).toString().toLowerCase();
                if (TRUE.equals(enabledValue) || FALSE.equals(enabledValue)) {
                    accessManagementMap.put(ENABLED, Boolean.parseBoolean(enabledValue));
                } else {
                    throw new RuntimeException(String.format("Invalid value for accessManagement.enabled: \"%s\"", enabledValue));
                }
            }
        }
    }

    private void modifyTypeOfRetentionDays(Map<String, Object> manifest, String bucketName) {
        // convert centralizedLogging.configurations.<bucketName>.retentionDays to Integer
        if (manifest.containsKey(CENTRALIZED_LOGGING)) {
            Map<String, Object> centralizedLoggingMap = (Map<String, Object>) manifest.get(CENTRALIZED_LOGGING);

            if (centralizedLoggingMap.containsKey(CONFIGURATIONS)) {
                Map<String, Object> configurationMap = (Map<String, Object>) centralizedLoggingMap.get(CONFIGURATIONS);

                if (configurationMap.containsKey(bucketName) ) {
                    Map<String, Object> bucketMap = (Map<String, Object>) configurationMap.get(bucketName);

                    if (bucketMap.containsKey(RETENTION_DAYS)) {
                        bucketMap.put(RETENTION_DAYS, Integer.parseInt(bucketMap.get(RETENTION_DAYS).toString()));
                    }
                }
            }
        }
    }

    private void modifyTypeOfCentralizedLoggingEnabledParameter(Map<String, Object> manifest) {
        // convert centralizedLogging.configurations.<bucketName>.retentionDays to Integer
        if (manifest.containsKey(CENTRALIZED_LOGGING)) {
            Map<String, Object> centralizedLoggingMap = (Map<String, Object>) manifest.get(CENTRALIZED_LOGGING);

            if (centralizedLoggingMap.containsKey(ENABLED)) {
                String enabledValue = centralizedLoggingMap.get(ENABLED).toString().toLowerCase();
                if (TRUE.equals(enabledValue) || FALSE.equals(enabledValue)) {
                    centralizedLoggingMap.put(ENABLED, Boolean.parseBoolean(enabledValue));
                } else {
                    throw new RuntimeException(String.format("Invalid value for centralizedLogging.enabled: \"%s\"", enabledValue));
                }
            }
        }
    }
}
