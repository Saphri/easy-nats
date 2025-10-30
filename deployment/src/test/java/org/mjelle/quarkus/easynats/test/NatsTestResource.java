package org.mjelle.quarkus.easynats.test;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StreamConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Collections;
import java.util.Map;

public class NatsTestResource implements QuarkusTestResourceLifecycleManager {

    private Connection nc;

    @Override
    public Map<String, String> start() {
        try {
            Options options = new Options.Builder()
                    .server("nats://localhost:4222")
                    .userInfo("guest", "guest")
                    .build();
            nc = Nats.connect(options);
            JetStreamManagement jsm = nc.jetStreamManagement();
            try {
                jsm.getStreamInfo("test-stream");
            } catch (JetStreamApiException e) {
                if (e.getErrorCode() == 404) {
                    StreamConfiguration streamConfig = StreamConfiguration.builder()
                            .name("test-stream")
                            .subjects("test.subject")
                            .build();
                    jsm.addStream(streamConfig);
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        if (nc != null) {
            try {
                JetStreamManagement jsm = nc.jetStreamManagement();
                jsm.deleteStream("test-stream");
                nc.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
