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

/** Tests that NatsPublisher with array type parameters are rejected at build time. */
public class InvalidPublisherArrayTypeTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () -> ShrinkWrap.create(JavaArchive.class).addClasses(InvalidBean.class))
          .assertException(
              t -> {
                assertThat(t)
                    .isInstanceOf(DeploymentException.class)
                    .hasMessageContaining("Array type")
                    .hasMessageContaining("not supported");
              });

  @Test
  void test() {
    // This test will not be executed as the deployment will fail at build time.
  }

  @ApplicationScoped
  public static class InvalidBean {
    private final NatsPublisher<String[]> publisher;

    InvalidBean(NatsPublisher<String[]> publisher) {
      this.publisher = publisher;
    }
  }
}
