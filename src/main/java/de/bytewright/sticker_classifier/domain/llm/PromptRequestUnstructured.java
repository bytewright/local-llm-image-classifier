package de.bytewright.sticker_classifier.domain.llm;

import lombok.Builder;
import lombok.Singular;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Processes a request immediately and returns the result This is a synchronous call, so it will
 * block until the LLM responds
 *
 * @param promptDataList The data context for the request
 * @param prompt The prompt to send to the LLM
 */
@Builder
public record PromptRequestUnstructured(
    @Singular("promtData") List<PromtContextData> promptDataList,
    String prompt,
    PromptType promptType,
    UUID requestParameter)
    implements PromptRequest {
  @Override
  public Optional<String> responseJsonFormat() {
    return Optional.empty();
  }
}
