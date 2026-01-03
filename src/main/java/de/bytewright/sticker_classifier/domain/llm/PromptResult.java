package de.bytewright.sticker_classifier.domain.llm;

public sealed interface PromptResult
    permits ClassificationPromptResult, ErrorPromptResult, StringPromptResult {
  PromptRequest request();

  PromptType type();
}
