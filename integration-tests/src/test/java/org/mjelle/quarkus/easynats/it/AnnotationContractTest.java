package org.mjelle.quarkus.easynats.it;

import static org.assertj.core.api.Assertions.*;

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
 * reflection. Does not require Quarkus runtime since it only uses reflection.
 * </p>
 */
class AnnotationContractTest {

    @Test
    void testAnnotationCanBeAppliedToMethods() throws Exception {
        // Verify the annotation is defined and can be applied
        Method method = TestSubscriber.class.getMethod("onMessage", String.class);
        NatsSubscriber annotation = method.getAnnotation(NatsSubscriber.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.subject()).isEqualTo("test.annotation.subject");
    }

    @Test
    void testAnnotationRetentionIsRuntime() {
        // Verify the annotation has runtime retention
        Retention retention = NatsSubscriber.class.getAnnotation(Retention.class);
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    /**
     * Test class with a subscriber method.
     * Not a CDI bean - just for annotation contract testing via reflection.
     */
    public static class TestSubscriber {
        @NatsSubscriber(subject = "test.annotation.subject")
        public void onMessage(String message) {
            // Test method
        }
    }
}
