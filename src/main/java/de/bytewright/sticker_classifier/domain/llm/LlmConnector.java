package de.bytewright.sticker_classifier.domain.llm;


import java.util.Optional;

public interface LlmConnector {

  Optional<PromptResult> processRequest(PromptRequest promptRequest);
}
