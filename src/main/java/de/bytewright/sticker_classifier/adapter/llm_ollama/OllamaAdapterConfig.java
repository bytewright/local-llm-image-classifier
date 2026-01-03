package de.bytewright.sticker_classifier.adapter.llm_ollama;

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
@ConfigurationProperties("app.adapter.ollama")
public class OllamaAdapterConfig {
  private String multiModalModel;
  private String textModel;
  private String baseUrl;
  private int timeoutSeconds = 240;
  private int minContextSize = 8_192;
  private int maxContextSize = 65_536;
}
