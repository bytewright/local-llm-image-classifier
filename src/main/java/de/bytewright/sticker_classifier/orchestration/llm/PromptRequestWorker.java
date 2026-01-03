package de.bytewright.sticker_classifier.orchestration.llm;

import de.bytewright.sticker_classifier.domain.llm.LlmConnector;
import de.bytewright.sticker_classifier.domain.llm.PromptRequest;
import de.bytewright.sticker_classifier.domain.llm.PromptResult;
import de.bytewright.sticker_classifier.domain.llm.PromptResultProcessor;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PromptRequestWorker {
  private final String workerName;
  private final LlmConnector llmConnector;
  private final PromptRequestCoordinator coordinator;
  private final PromptResultProcessor resultConsumer;

  void processingLoop() {
    log.info("Worker thread started with name: {}", workerName);
    try {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          // Get next request from coordinator
          Optional<PromptRequest> request = coordinator.takeNextRequest();

          if (request.isPresent()) {
            PromptRequest promptRequest = request.get();
            Optional<PromptResult> promptResult = llmConnector.processRequest(promptRequest);
            if (promptResult.isPresent()) {
              resultConsumer.processResult(promptResult.get());
            } else {
              coordinator.reschedule(promptRequest);
            }
          } else {
            // No request available, sleep to avoid busy waiting
            Thread.sleep(1000);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Worker interrupted, shutting down");
          break;
        } catch (Exception e) {
          log.error("Error processing request", e);
        }
      }
    } finally {
      log.info("Worker '{}' thread exiting", workerName);
    }
  }
}
