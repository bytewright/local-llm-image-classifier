package de.bytewright.sticker_classifier.domain.llm;

public interface PromptResultConsumer {
  /**
   * @return false if result should be passed on to other consumers
   */
  boolean processPromtResult(PromptType promtType, PromptResult promptResult);

  default int consumerPriority() {
    return 100_000;
  }
}
