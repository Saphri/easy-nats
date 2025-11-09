package org.mjelle.quarkus.easynats.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.DeploymentException;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mjelle.quarkus.easynats.NatsPublisher;

import io.quarkus.test.QuarkusUnitTest;

/**
 * Tests that NatsPublisher with type parameters lacking a no-arg constructor are rejected at build
 * time.
 */
public class InvalidPublisherNoArgConstructorTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(InvalidBean.class, TypeWithoutNoArgConstructor.class))
          .assertException(
              t -> {
                assertThat(t)
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining("no-arg constructor");
              });

  @Test
  void test() {
    // This test will not be executed as the deployment will fail at build time.
  }

  @ApplicationScoped
  public static class InvalidBean {
    private final NatsPublisher<TypeWithoutNoArgConstructor> publisher;

    InvalidBean(NatsPublisher<TypeWithoutNoArgConstructor> publisher) {
      this.publisher = publisher;
    }
  }

  public static class TypeWithoutNoArgConstructor {
    private final String value;

    // Only parameterized constructor - no no-arg constructor
    public TypeWithoutNoArgConstructor(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }
}
