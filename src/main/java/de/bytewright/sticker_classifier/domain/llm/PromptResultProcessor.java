package de.bytewright.sticker_classifier.domain.llm;


@FunctionalInterface
public interface PromptResultProcessor {
  void processResult(PromptResult promtResult);
}
