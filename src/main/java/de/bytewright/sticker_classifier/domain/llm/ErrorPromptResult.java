package de.bytewright.sticker_classifier.domain.llm;

public record ErrorPromptResult(PromptRequest request) implements PromptResult {

  @Override
  public PromptType type() {
    return request.promptType();
  }
}
