package converters;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import converters.DocumentConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.core.document.Document;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;

@ExtendWith(MockitoExtension.class)
public class DocumentConverterTest {
    private static final String LOGGING_BUCKET = "loggingBucket";
    private static final String ACCESS_LOGGING_BUCKET = "accessLoggingBucket";
    private static final String ACCESS_MANAGEMENT = "accessManagement";
    private static final String ENABLED = "enabled";

    private static final String CENTRALIZED_LOGGING = "centralizedLogging";
    private static final String ACCOUNT_ID = "accountId";
    private static final String CONFIGURATIONS = "configurations";
    private static final String RETENTION_DAYS = "retentionDays";
    private static DocumentConverter converter;
    private static Map<String, Object> objectMap = new HashMap<>();
    private static Map<String, Document> documentMap = new HashMap<>();
    private static Document document;

    @BeforeAll
    public static void setup() {
        converter = new DocumentConverter();

        objectMap.put("key1", "value1");
        objectMap.put("key2", 42);
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("nested_key", "nested_value");
        objectMap.put("nested", nestedMap);

        documentMap.put("key1", Document.fromString("value1"));
        documentMap.put("key2", Document.fromNumber(42));
        Map<String, Document> nestedDocumentMap = new HashMap<>();
        nestedDocumentMap.put("nested_key", Document.fromString("nested_value"));
        documentMap.put("nested", Document.fromMap(nestedDocumentMap));
        document = Document.fromMap(documentMap);
    }

    @Test
    public void toDocument_success() {
        Document result = converter.toDocument(objectMap);

        assertNotNull(result);
        assertEquals(document, result);
    }

    @Test
    public void toDocument_throwsIllegalArgumentException() {
        Map<String, Object> invalidMap = new HashMap<>();
        invalidMap.put("key", new Object());

        assertThrows(IllegalArgumentException.class, () -> converter.toDocument(invalidMap));
    }

    @Test
    public void toMap_success() {
        Map<String, Object> result = converter.toMap(document);
        assertNotNull(result);
        assertEquals(objectMap.size(), result.size());
        assertEquals(objectMap.toString(), result.toString());
    }

    @ParameterizedTest
    @MethodSource("provideValidBooleanValues")
    public void test_modifyTypesOfManifestParameters_withAccessManagementParameter_givenValidInputs(Object isEnabled) {
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(isEnabled, generateCentralizedLoggingObjectMap(100));
        converter.modifyTypesOfManifestParameters(inputToDocumentConverter);
        Object accessManagementEnabled = getNestedValue(inputToDocumentConverter, ACCESS_MANAGEMENT, ENABLED);
        assertTrue(accessManagementEnabled instanceof Boolean);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidBooleanValues")
    public void test_modifyTypesOfManifestParameters_withAccessManagementParameter_givenInvalidInputs(Object isEnabled) {
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(isEnabled, generateCentralizedLoggingObjectMap(100));
        assertThrows(CfnInvalidRequestException.class, () -> converter.modifyTypesOfManifestParameters(inputToDocumentConverter));
    }

    @Test
    public void test_modifyTypesOfManifestParameters_withAccessManagementParameter_givenPartialInputs() {
        Map<String, Object> accessManagedEnabled = new HashMap<>();
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(accessManagedEnabled, generateCentralizedLoggingObjectMap(100));
        converter.modifyTypesOfManifestParameters(inputToDocumentConverter);
        Object accessManagementEnabled = getNestedValue(inputToDocumentConverter, ACCESS_MANAGEMENT, ENABLED);
        assertNull(accessManagementEnabled);
    }

    @ParameterizedTest
    @MethodSource("provideValidBooleanValues")
    public void test_modifyTypesOfManifestParameters_withCentralizedLoggingEnabled_givenValidInputs(Object isCentralizedLoggingEnabled) {
        Map<String, Object> accessManagedEnabled = new HashMap<String, Object>() {{
            put(ENABLED, true);
        }};
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(accessManagedEnabled, isCentralizedLoggingEnabled);
        converter.modifyTypesOfManifestParameters(inputToDocumentConverter);
        Object centralizedLoggingEnabledOutput = getNestedValue(inputToDocumentConverter, CENTRALIZED_LOGGING, ENABLED);
        assertTrue(centralizedLoggingEnabledOutput instanceof Boolean);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidBooleanValues")
    public void test_modifyTypesOfManifestParameters_withCentralizedLoggingEnabled_givenInvalidInputs(Object isCentralizedLoggingEnabled) {
        Map<String, Object> accessManagedEnabled = new HashMap<String, Object>() {{
            put(ENABLED, true);
        }};
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(accessManagedEnabled, isCentralizedLoggingEnabled);
        assertThrows(CfnInvalidRequestException.class, () -> converter.modifyTypesOfManifestParameters(inputToDocumentConverter));
    }

    @Test
    public void test_modifyTypesOfManifestParameters_withCentralizedLoggingEnabled_givenPartialInputs() {
        Map<String, Object> accessManagedEnabled = new HashMap<String, Object>() {{
            put(ENABLED, true);
        }};
        Map<String, Object> centralizedLoggingEnabled = new HashMap<>();
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(accessManagedEnabled, centralizedLoggingEnabled);
        converter.modifyTypesOfManifestParameters(inputToDocumentConverter);
        Object centralizedLoggingEnabledOutput = getNestedValue(inputToDocumentConverter, CENTRALIZED_LOGGING, ENABLED);
        assertNull(centralizedLoggingEnabledOutput);
    }

    @ParameterizedTest
    @MethodSource("provideRetentionDayWithValidInputs")
    public void test_modifyTypesOfManifestParameters_withRetentionDay_givenValidInputs(Object centralizedLoggingObject) {
        Map<String, Object> accessManagedEnabled = new HashMap<String, Object>() {{
            put(ENABLED, true);
        }};
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(accessManagedEnabled, centralizedLoggingObject);
        converter.modifyTypesOfManifestParameters(inputToDocumentConverter);
        Object loggingBucketRetentionDays = getNestedValue(inputToDocumentConverter, CENTRALIZED_LOGGING, CONFIGURATIONS, LOGGING_BUCKET, RETENTION_DAYS);
        Object accessLoggingBucketRetentionDays = getNestedValue(inputToDocumentConverter, CENTRALIZED_LOGGING, CONFIGURATIONS, ACCESS_LOGGING_BUCKET, RETENTION_DAYS);
        assertTrue(loggingBucketRetentionDays instanceof Integer);
        assertTrue(accessLoggingBucketRetentionDays instanceof Integer);
    }

    @ParameterizedTest
    @MethodSource("provideRetentionDayWithInvalidInputs")
    public void test_modifyTypesOfManifestParameters_withRetentionDay_givenInValidInputs(Object centralizedLoggingObject) {
        Map<String, Object> accessManagedEnabled = new HashMap<String, Object>() {{
            put(ENABLED, true);
        }};
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(accessManagedEnabled, centralizedLoggingObject);
        assertThrows(CfnInvalidRequestException.class, () -> converter.modifyTypesOfManifestParameters(inputToDocumentConverter));
    }

    @Test
    public void test_modifyTypesOfManifestParameters_withRetentionDay_givenInValidInputs_givenPartialInputsTillConfiguration() {
        Map<String, Object> accessManagedEnabled = new HashMap<String, Object>() {{
            put(ENABLED, true);
        }};
        Map<String, Object> centralizedLoggingObjectMap = new HashMap<>();
        centralizedLoggingObjectMap.put(CONFIGURATIONS, new HashMap<String, Object>());
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(accessManagedEnabled, centralizedLoggingObjectMap);
        converter.modifyTypesOfManifestParameters(inputToDocumentConverter);
        Object validateObject = getNestedValue(inputToDocumentConverter, CENTRALIZED_LOGGING, CONFIGURATIONS, LOGGING_BUCKET);
        assertNull(validateObject);
    }

    @Test
    public void test_modifyTypesOfManifestParameters_withRetentionDay_givenInValidInputs_givenPartialInputsTillLoggingBucket() {
        Map<String, Object> accessManagedEnabled = new HashMap<String, Object>() {{
            put(ENABLED, true);
        }};
        Map<String, Object> centralizedLoggingObjectMap = new HashMap<>();
        centralizedLoggingObjectMap.put(CONFIGURATIONS, new HashMap<String, Object>() {{
            put(LOGGING_BUCKET, new HashMap<String, Object>());
        }});
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(accessManagedEnabled, centralizedLoggingObjectMap);
        converter.modifyTypesOfManifestParameters(inputToDocumentConverter);
        Object validateObject = getNestedValue(inputToDocumentConverter, CENTRALIZED_LOGGING, CONFIGURATIONS, LOGGING_BUCKET, RETENTION_DAYS);
        assertNull(validateObject);
    }

    @Test
    public void test_modifyTypesOfManifestParameters_withRetentionDay_givenInValidInputs_givenPartialInputsTillAccessLoggingBucket() {
        Map<String, Object> accessManagedEnabled = new HashMap<String, Object>() {{
            put(ENABLED, true);
        }};
        Map<String, Object> centralizedLoggingObjectMap = new HashMap<>();
        centralizedLoggingObjectMap.put(CONFIGURATIONS, new HashMap<String, Object>() {{
            put(ACCESS_LOGGING_BUCKET, new HashMap<String, Object>());
        }});
        Map<String, Object> inputToDocumentConverter = generateManifestObjectMap(accessManagedEnabled, centralizedLoggingObjectMap);
        converter.modifyTypesOfManifestParameters(inputToDocumentConverter);
        Object validateObject = getNestedValue(inputToDocumentConverter, CENTRALIZED_LOGGING, CONFIGURATIONS, ACCESS_LOGGING_BUCKET, RETENTION_DAYS);
        assertNull(validateObject);
    }

    private static Stream<Arguments> provideValidBooleanValues() {
        return Stream.of(
                Arguments.of(new HashMap<String, Object>() {{
                    put(ENABLED, "trUe");
                }}),
                Arguments.of(new HashMap<String, Object>() {{
                    put(ENABLED, "FalSe");
                }}),
                Arguments.of(new HashMap<String, Object>() {{
                    put(ENABLED, true);
                }}),
                Arguments.of(new HashMap<String, Object>() {{
                    put(ENABLED, false);
                }})
        );
    }

    private static Stream<Arguments> provideInvalidBooleanValues() {
        return Stream.of(
                Arguments.of(new HashMap<String, Object>() {{
                    put(ENABLED, "");
                }}),
                Arguments.of(new HashMap<String, Object>() {{
                    put(ENABLED, "randomValue");
                }}),
                Arguments.of(new HashMap<String, Object>() {{
                    put(ENABLED, new HashMap<String, Object>());
                }}),
                Arguments.of(new HashMap<String, Object>() {{
                    put(ENABLED, null);
                }})
        );
    }

    private static Stream<Arguments> provideRetentionDayWithValidInputs() {
        return Stream.of(
                Arguments.of(generateCentralizedLoggingObjectMap("100")),
                Arguments.of(generateCentralizedLoggingObjectMap("-1")),
                Arguments.of(generateCentralizedLoggingObjectMap(15020))
        );
    }

    private static Stream<Arguments> provideRetentionDayWithInvalidInputs() {
        return Stream.of(
                Arguments.of(generateCentralizedLoggingObjectMap("3.14")),
                Arguments.of(generateCentralizedLoggingObjectMap(8.23)),
                Arguments.of(generateCentralizedLoggingObjectMap("RetainForever")),
                Arguments.of(generateCentralizedLoggingObjectMap("")),
                Arguments.of(new HashMap<String, Object>() {{
                    put(CONFIGURATIONS, new HashMap<String, Object>() {{
                        put(LOGGING_BUCKET, new HashMap<String, Object>() {{
                            put(RETENTION_DAYS, new HashMap<String, Object>());
                        }});
                    }});
                }})
        );
    }

    private Map<String, Object> generateManifestObjectMap(Object accessManagementObjectMap, Object centralizedLoggingObjectMap) {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put("governedRegions", new ArrayList<>(Arrays.asList("us-east-1", "us-west-2")));
        objectMap.put("organizationStructure", new HashMap<String, Object>() {{
            put("security", "someValue");
        }});
        objectMap.put("securityRoles", new HashMap<String, Object>() {{
            put(ACCOUNT_ID, "600372172862");
        }});
        objectMap.put(ACCESS_MANAGEMENT, accessManagementObjectMap);
        objectMap.put(CENTRALIZED_LOGGING, centralizedLoggingObjectMap);
        return objectMap;
    }

    private static Map<String, Object> generateCentralizedLoggingObjectMap(Object retentionDays) {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put(ACCOUNT_ID, "600372172863");
        objectMap.put(CONFIGURATIONS, new HashMap<String, Object>() {{
            put(LOGGING_BUCKET, new HashMap<String, Object>() {{
                put(RETENTION_DAYS, retentionDays);
            }});
            put(ACCESS_LOGGING_BUCKET, new HashMap<String, Object>() {{
                put(RETENTION_DAYS, retentionDays);
            }});
        }});
        return objectMap;
    }

    private Object getNestedValue(Map<String, Object> objectMap, String... keys) {
        Object value = objectMap;
        for (String key : keys) {
            value = ((Map<String, Object>) value).get(key);
        }
        return value;
    }
}
