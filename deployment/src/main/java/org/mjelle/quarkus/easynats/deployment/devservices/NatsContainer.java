package org.mjelle.quarkus.easynats.deployment.devservices;

import java.time.Duration;
import java.util.OptionalInt;

import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.builditem.Startable;
import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.runtime.LaunchMode;

import static org.mjelle.quarkus.easynats.deployment.devservices.NatsDevServicesProcessor.CONTAINER_LABEL;
import static io.quarkus.devservices.common.ConfigureUtil.configureSharedServiceLabel;

public class NatsContainer extends GenericContainer<NatsContainer> implements Startable {

    private static final Logger logger = Logger.getLogger(NatsContainer.class);

    static final Integer NATS_PORT = 4222;
    static final Integer NATS_HTTP_PORT = 8222;

    private final OptionalInt fixedExposedPort;
    private final boolean useSharedNetwork;
    private final String hostName;

    public NatsContainer(DockerImageName imageName, String username, String password) {
        this(imageName, username, password, OptionalInt.empty(), false, null);
    }

    public NatsContainer(DockerImageName imageName, String username, String password, OptionalInt fixedExposedPort) {
        this(imageName, username, password, fixedExposedPort, false, null);
    }

    public NatsContainer(DockerImageName imageName, String username, String password, OptionalInt fixedExposedPort,
                        boolean useSharedNetwork, String defaultNetworkId) {
        super(imageName);

        this.fixedExposedPort = fixedExposedPort;
        this.useSharedNetwork = useSharedNetwork;

        super.withNetworkAliases("nats");
        super.waitingFor(Wait.forHttp("/healthz").forPort(NATS_HTTP_PORT));
        super.withStartupTimeout(Duration.ofSeconds(180L));

        if (fixedExposedPort.isPresent()) {
            super.addFixedExposedPort(fixedExposedPort.getAsInt(), NATS_PORT);
        } else {
            addExposedPort(NATS_PORT);
        }
        addExposedPort(NATS_HTTP_PORT);
        super.withCommand("--jetstream", "--user", username, "--pass", password, "--http_port", NATS_HTTP_PORT.toString());
        super.withLogConsumer(outputFrame -> logger.info(outputFrame.getUtf8String().replace("\n", "")));

        this.hostName = ConfigureUtil.configureNetwork(this, defaultNetworkId, useSharedNetwork, "nats");
    }

    public NatsContainer withSharedServiceLabel(LaunchMode launchMode, String serviceName) {
        return configureSharedServiceLabel(this, launchMode, CONTAINER_LABEL, serviceName);
    }

    @Override
    public String getHost() {
        return useSharedNetwork ? hostName : super.getHost();
    }

    public int getPort() {
        if (useSharedNetwork) {
            return NATS_PORT;
        }
        if (fixedExposedPort.isPresent()) {
            return fixedExposedPort.getAsInt();
        }
        return super.getFirstMappedPort();
    }

    public void close() {
        stop();
    }

    @Override
    public String getConnectionInfo() {
        return getHost() + ":" + getPort();
    }
}
