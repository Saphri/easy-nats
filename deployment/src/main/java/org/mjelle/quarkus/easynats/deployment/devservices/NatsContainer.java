package org.mjelle.quarkus.easynats.deployment.devservices;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class NatsContainer extends GenericContainer<NatsContainer> {

    public NatsContainer(String dockerImageName) {
        super(dockerImageName);
        withCommand("-js");
        withExposedPorts(4222, 8222);
        waitingFor(Wait.forLogMessage(".*Server is ready.*\\n", 1));
    }

    public String getNatsUrl() {
        return String.format("nats://%s:%d", getHost(), getMappedPort(4222));
    }
}
