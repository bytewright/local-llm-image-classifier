package de.bytewright.sticker_classifier.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter(AccessLevel.PACKAGE)
@Configuration
@RequiredArgsConstructor
@ConfigurationProperties("app.orchestration")
public class AppOrchestrationConfig {
  private ClassificationConfig classification;

  @Getter
  @Setter(AccessLevel.PACKAGE)
  public static class ClassificationConfig {
    private boolean removeOriginalFile;
  }
}
