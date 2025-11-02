package org.mjelle.quarkus.easynats.deployment.devservices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CredentialExtractor helper class.
 * Tests credential extraction from environment variables and default handling.
 */
@DisplayName("CredentialExtractor Tests")
class CredentialExtractorTest {

    @Test
    @DisplayName("extract with NATS_USERNAME and NATS_PASSWORD returns correct credentials")
    void testExtractWithStandardEnvVars() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_USERNAME", "admin");
        env.put("NATS_PASSWORD", "secretpass");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.username()).isEqualTo("admin");
        assertThat(creds.password()).isEqualTo("secretpass");
        assertThat(creds.sslEnabled()).isFalse();
    }

    @Test
    @DisplayName("extract with NATS_USER (fallback) returns correct username")
    void testExtractWithNatsUserFallback() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_USER", "guest");
        env.put("NATS_PASSWORD", "guestpass");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.username()).isEqualTo("guest");
        assertThat(creds.password()).isEqualTo("guestpass");
    }

    @Test
    @DisplayName("extract prefers NATS_USERNAME over NATS_USER")
    void testExtractPreferNatsUsername() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_USERNAME", "admin");
        env.put("NATS_USER", "guest");
        env.put("NATS_PASSWORD", "adminpass");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.username()).isEqualTo("admin");
    }

    @Test
    @DisplayName("extract applies default username when not provided")
    void testExtractDefaultUsername() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_PASSWORD", "pass");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.username()).isEqualTo("nats");
        assertThat(creds.password()).isEqualTo("pass");
    }

    @Test
    @DisplayName("extract applies default password when not provided")
    void testExtractDefaultPassword() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_USERNAME", "user");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.username()).isEqualTo("user");
        assertThat(creds.password()).isEqualTo("nats");
    }

    @Test
    @DisplayName("extract applies default credentials for empty environment")
    void testExtractDefaultCredentials() {
        Map<String, String> env = new HashMap<>();

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.username()).isEqualTo("nats");
        assertThat(creds.password()).isEqualTo("nats");
        assertThat(creds.sslEnabled()).isFalse();
    }

    @Test
    @DisplayName("extract detects SSL from NATS_TLS_CERT")
    void testExtractSslFromTlsCert() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_USERNAME", "user");
        env.put("NATS_PASSWORD", "pass");
        env.put("NATS_TLS_CERT", "/etc/nats/certs/server-cert.pem");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.sslEnabled()).isTrue();
    }

    @Test
    @DisplayName("extract detects SSL from NATS_TLS_KEY")
    void testExtractSslFromTlsKey() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_USERNAME", "user");
        env.put("NATS_PASSWORD", "pass");
        env.put("NATS_TLS_KEY", "/etc/nats/certs/server-key.pem");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.sslEnabled()).isTrue();
    }

    @Test
    @DisplayName("extract detects SSL from NATS_TLS_CA")
    void testExtractSslFromTlsCa() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_USERNAME", "user");
        env.put("NATS_PASSWORD", "pass");
        env.put("NATS_TLS_CA", "/etc/nats/certs/ca.pem");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.sslEnabled()).isTrue();
    }

    @Test
    @DisplayName("extract detects SSL if ANY certificate env var is present")
    void testExtractSslMultipleCertVars() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_USERNAME", "user");
        env.put("NATS_PASSWORD", "pass");
        env.put("NATS_TLS_CERT", "/etc/nats/certs/server-cert.pem");
        env.put("NATS_TLS_KEY", "/etc/nats/certs/server-key.pem");
        env.put("NATS_TLS_CA", "/etc/nats/certs/ca.pem");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.sslEnabled()).isTrue();
    }

    @Test
    @DisplayName("extract with no SSL certificates returns SSL disabled")
    void testExtractNoSslCertificates() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_USERNAME", "user");
        env.put("NATS_PASSWORD", "pass");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.sslEnabled()).isFalse();
    }

    @Test
    @DisplayName("extract ignores unknown environment variables")
    void testExtractIgnoresUnknownVars() {
        Map<String, String> env = new HashMap<>();
        env.put("NATS_USERNAME", "user");
        env.put("NATS_PASSWORD", "pass");
        env.put("UNKNOWN_VAR", "value");
        env.put("NATS_UNKNOWN", "value");

        CredentialExtractor.Credentials creds = CredentialExtractor.extract(env);

        assertThat(creds.username()).isEqualTo("user");
        assertThat(creds.password()).isEqualTo("pass");
        assertThat(creds.sslEnabled()).isFalse();
    }
}
