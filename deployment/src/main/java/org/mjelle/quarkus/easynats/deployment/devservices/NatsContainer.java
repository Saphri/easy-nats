package org.mjelle.quarkus.easynats.deployment.devservices;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

public class NatsContainer extends GenericContainer<NatsContainer> {

    public NatsContainer(String dockerImageName) {
        super(dockerImageName);
        withCommand("-js");
        withExposedPorts(4222, 8222);
        waitingFor(new HttpWaitStrategy().forPort(8222).forPath("/healthz").forStatusCode(200));
    }

    public String getNatsUrl() {
        return String.format("nats://%s:%d", getHost(), getMappedPort(4222));
    }
}
