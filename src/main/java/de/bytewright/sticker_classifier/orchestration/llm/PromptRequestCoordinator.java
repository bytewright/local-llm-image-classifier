package de.bytewright.sticker_classifier.orchestration.llm;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.bytewright.sticker_classifier.domain.llm.PromptRequest;
import de.bytewright.sticker_classifier.domain.llm.PromptRetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
// todo include actuator
public class PromptRequestCoordinator /*implements HealthIndicator*/ {
  private final Queue<PromptRequest> requestQueue = new ConcurrentLinkedQueue<>();

  public void schedule(PromptRequest request) {
    log.debug(
        "Queueing new request of type {}",
        request.promptType());
    requestQueue.add(request);
  }

  void reschedule(PromptRequest promptRequest) {
    log.warn("Rescheduling prompt request after exec failed: {}", promptRequest.promptType());
    if (promptRequest instanceof PromptRetry(int counter, PromptRequest delegate)) {
      if (counter < 3) {
        requestQueue.add(new PromptRetry(counter + 1, delegate));
      } else {
        throw new IllegalArgumentException(
            "Failed to get usable result from promptRequest in %d tries! %s"
                .formatted(counter, delegate));
      }
    }
    requestQueue.add(new PromptRetry(1, promptRequest));
  }

  /**
   * Takes the next request from the queue. This method is used by worker threads to get the next
   * request to process.
   *
   * @return The next request or Optional#empty if queue is empty
   */
  public Optional<PromptRequest> takeNextRequest() {
    if (!requestQueue.isEmpty()) {
      Optional<PromptRequest> promptRequest = Optional.ofNullable(requestQueue.poll());
      log.info(
          "Fetched PromptRequest from queue, queue size now {}. Prompt Type: {}",
          requestQueue.size(),
          promptRequest.map(PromptRequest::promptType).map(Enum::name).orElse("UNKNOWN_TYPE"));
      return promptRequest;
    }
    return Optional.empty();
  }
}
