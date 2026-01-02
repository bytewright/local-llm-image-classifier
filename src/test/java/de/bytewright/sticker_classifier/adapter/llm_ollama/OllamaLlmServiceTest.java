package de.bytewright.sticker_classifier.adapter.llm_ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import org.junit.jupiter.api.Test;

class OllamaLlmServiceTest {

  @Test
  void getSchema() {
    OllamaLlmService ollamaLlmService = new OllamaLlmService(mock(), mock(), mock(), mock());
    Object schema = ollamaLlmService.getSchema(ClassificationResult.class);
    assertThat(schema)
        .hasToString(
            "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\",\"properties\":{\"categoryName\":{\"type\":\"string\"},\"emoji\":{\"type\":\"string\"},\"hasText\":{\"type\":\"boolean\"},\"keyword\":{\"type\":\"string\"},\"textLanguageGuess\":{\"type\":\"string\"}},\"required\":[\"categoryName\",\"hasText\"]}");
  }
}
