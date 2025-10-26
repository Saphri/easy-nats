package org.mjelle.quarkus.easynats;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI qualifier to specify the default NATS subject for an injected {@link NatsPublisher} instance.
 * <p>
 * Example:
 * <pre>{@code
 * @Inject
 * @NatsSubject("my-subject")
 * NatsPublisher<String> publisher;
 * }</pre>
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
public @interface NatsSubject {
    /**
     * The default NATS subject.
     *
     * @return the subject
     */
    @Nonbinding
    String value();
}
