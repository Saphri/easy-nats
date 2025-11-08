package org.mjelle.quarkus.easynats;

import java.time.Instant;
import java.util.UUID;

import org.eclipse.microprofile.config.ConfigProvider;

public class CloudEventsMetadataGenerator {

  public static final String SPEC_VERSION = "1.0";
  public static final String CONTENT_TYPE_JSON = "application/json";

  public CloudEventsMetadata generate(
      Class<?> payloadClass,
      String ceTypeOverride,
      String ceSourceOverride,
      String dataContentType) {
    String ceType = ceTypeOverride != null ? ceTypeOverride : generateType(payloadClass);
    String ceSource = ceSourceOverride != null ? ceSourceOverride : generateSource();
    String contentType = dataContentType != null ? dataContentType : CONTENT_TYPE_JSON;
    return new CloudEventsMetadata(
        SPEC_VERSION, ceType, ceSource, generateId(), generateTime(), contentType);
  }

  private String generateId() {
    return UUID.randomUUID().toString();
  }

  private String generateTime() {
    return Instant.now().toString();
  }

  private String generateType(Class<?> payloadClass) {
    return payloadClass.getCanonicalName();
  }

  private String generateSource() {
    try {
      String appName =
          ConfigProvider.getConfig()
              .getOptionalValue("quarkus.application.name", String.class)
              .orElse(null);
      if (appName != null && !appName.isEmpty()) {
        return appName;
      }
    } catch (Exception e) {
      // Config not available, continue to fallback
    }
    return "unknown";
  }
}
