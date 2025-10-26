package org.mjelle.quarkus.easynats.it;

import static org.assertj.core.api.Assertions.*;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mjelle.quarkus.easynats.NatsSubscriber;

/**
 * Tests verifying that the {@code @NatsSubscriber} annotation can be applied to methods.
 *
 * <p>
 * This test ensures the annotation contract is correctly defined and can be discovered via
 * reflection.
 * </p>
 */
@QuarkusTest
@QuarkusTestResource(NatsStreamTestResource.class)
class AnnotationContractTest {

    @Test
    void testAnnotationCanBeAppliedToMethods() throws Exception {
        // Verify the annotation is defined and can be applied
        Method method = TestSubscriber.class.getMethod("onMessage", String.class);
        NatsSubscriber annotation = method.getAnnotation(NatsSubscriber.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("test.annotation.subject");
    }

    @Test
    void testAnnotationRetentionIsRuntime() {
        // Verify the annotation has runtime retention
        Retention retention = NatsSubscriber.class.getAnnotation(Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    /**
     * Test bean with a subscriber method.
     */
    @ApplicationScoped
    public static class TestSubscriber {
        @NatsSubscriber("test.annotation.subject")
        public void onMessage(String message) {
            // Test method
        }
    }
}
