package de.bytewright.sticker_classifier.domain.llm;

import java.util.UUID;

public interface PromptResultConsumer {
  /**
   * @return false if result should be passed on to other consumers
   */
  boolean processPromtResult(PromptType promtType, PromptResult promptResult);
}
