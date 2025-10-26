package org.mjelle.quarkus.easynats.test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mjelle.quarkus.easynats.NatsPublisher;
import org.mjelle.quarkus.easynats.NatsSubject;
import jakarta.enterprise.inject.spi.DeploymentException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.quarkus.test.QuarkusUnitTest;

public class EmptyNatsSubjectTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(EmptySubjectBean.class, NatsPublisher.class, NatsSubject.class))
            .assertException(t -> {
                assertInstanceOf(DeploymentException.class, t);
            });

    @Test
    void test() {
        // This test will not be executed as the deployment will fail.
    }

    @ApplicationScoped
    public static class EmptySubjectBean {
        @Inject
        @NatsSubject("")
        NatsPublisher<String> publisher;
    }
}
