package de.bytewright.sticker_classifier.domain.llm;

import java.util.Optional;
import java.util.UUID;

public sealed interface PromptRequest
    permits PromptRequestUnstructured, PromptRequestWithImage, PromptRetry {
  PromptType promptType();

  UUID requestParameter();

  String prompt();

  Optional<String> responseJsonFormat();
}
