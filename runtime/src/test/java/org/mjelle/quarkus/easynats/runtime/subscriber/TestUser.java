package org.mjelle.quarkus.easynats.runtime.subscriber;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Test POJO for deserialization testing.
 */
public class TestUser {

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("email")
    private String email;

    public TestUser() {
        // No-arg constructor for Jackson
    }

    public TestUser(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
