package de.bytewright.sticker_classifier.domain.llm;

import java.util.Optional;
import java.util.UUID;

public record PromptRetry(int counter, PromptRequest delegate) implements PromptRequest {

  @Override
  public PromptType promptType() {
    return delegate.promptType();
  }

  @Override
  public UUID requestParameter() {
    return delegate.requestParameter();
  }

  @Override
  public String prompt() {
    return delegate.prompt();
  }

  @Override
  public Optional<String> responseJsonFormat() {
    return delegate.responseJsonFormat();
  }
}
