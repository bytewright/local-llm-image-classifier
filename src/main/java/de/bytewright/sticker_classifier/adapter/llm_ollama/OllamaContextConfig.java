package de.bytewright.sticker_classifier.adapter.llm_ollama;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class OllamaContextConfig {

  static final String OLLAMA_BASE_URL = "http://localhost:11434";
  static final String DEFAULT_TEXT_MODEL = "gemma3:12b";
  static final String DEFAULT_MULTIMODAL_MODEL = "qwen3-vl:8b";
  static final Duration OLLAMA_TIMEOUT = Duration.ofMinutes(5);

  @Bean
  public OllamaApi ollamaApi() {
    var jdkClientHttpRequestFactory = new JdkClientHttpRequestFactory();
    jdkClientHttpRequestFactory.setReadTimeout(OLLAMA_TIMEOUT);

    var restClientBuilder =
        RestClient.builder().baseUrl(OLLAMA_BASE_URL).requestFactory(jdkClientHttpRequestFactory);

    return OllamaApi.builder().restClientBuilder(restClientBuilder).build();
  }
}
