package org.mjelle.quarkus.easynats.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mjelle.quarkus.easynats.NatsSubject;

import io.quarkus.test.QuarkusUnitTest;

public class InvalidNatsSubjectTypeTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(InvalidBean.class, NatsSubject.class))
          .assertException(t -> assertThat(t).isInstanceOf(DeploymentException.class));

  @Test
  void test() {
    // This test will not be executed as the deployment will fail.
  }

  @ApplicationScoped
  public static class InvalidBean {
    @Inject
    @NatsSubject("invalid-subject")
    String notANatsPublisher;
  }
}
