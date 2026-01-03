package de.bytewright.sticker_classifier.adapter.llm_ollama;

import java.net.http.HttpClient;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class OllamaContextConfig {

  @Bean
  public OllamaApi ollamaApi(
      OllamaAdapterConfig ollamaAdapterConfig,
      @Qualifier("ollamaHttpRequestFactory") ClientHttpRequestFactory httpRequestFactory) {
    var restClientBuilder =
        RestClient.builder()
            .baseUrl(ollamaAdapterConfig.getBaseUrl())
            .requestFactory(httpRequestFactory);

    return OllamaApi.builder().restClientBuilder(restClientBuilder).build();
  }

  @Bean("ollamaHttpRequestFactory")
  public JdkClientHttpRequestFactory httpRequestFactory(OllamaAdapterConfig ollamaAdapterConfig) {
    var httpClientBuilder =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(HttpClient.Version.HTTP_1_1) // Force HTTP/1.1 for better compatibility
            .build();
    var jdkClientHttpRequestFactory = new JdkClientHttpRequestFactory(httpClientBuilder);
    Duration duration = Duration.ofSeconds(ollamaAdapterConfig.getTimeoutSeconds());
    jdkClientHttpRequestFactory.setReadTimeout(duration);
    return jdkClientHttpRequestFactory;
  }
}
