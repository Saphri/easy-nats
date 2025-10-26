package org.mjelle.quarkus.easynats.it;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mjelle.quarkus.easynats.it.NatsTestUtils.purgeStream;

@QuarkusTest
public class NatsSubjectTest {

    private static final String SUBJECT = "test.nats_subject";

    @Inject
    @NatsSubject(SUBJECT)
    NatsPublisher<String> publisher;

    private Connection nc;
    private JetStreamSubscription sub;

    @BeforeEach
    void setUp() throws Exception {
        nc = NatsTestUtils.getConnection();
        purgeStream();
        JetStream js = nc.jetStream();
        sub = js.subscribe(SUBJECT);
    }

    @Test
    void testPublish() throws Exception {
        assertNotNull(publisher);

        publisher.publish("Hello NATS!");

        await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(() -> {
                Message msg = sub.nextMessage(Duration.ofMillis(500));
                assertThat(msg).isNotNull();
                assertThat(new String(msg.getData())).isEqualTo("Hello NATS!");
                msg.ack();
            });
    }
}
