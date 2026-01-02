package de.bytewright.sticker_classifier.orchestration.llm;

import de.bytewright.sticker_classifier.domain.llm.LlmConnector;
import de.bytewright.sticker_classifier.domain.llm.PromptResult;
import de.bytewright.sticker_classifier.domain.llm.PromptResultConsumer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerFactory {
  private final ExecutorService executorService = Executors.newFixedThreadPool(1);
  @Getter private final Set<PromptResultConsumer> resultConsumers = ConcurrentHashMap.newKeySet();
  private final PromptRequestCoordinator coordinator;
  private final LlmConnector llmConnector;

  public void initialize() {
    // Start the worker thread
    var worker = new PromptRequestWorker(llmConnector, coordinator, this::notifyConsumers);
    executorService.submit(worker::processingLoop);
    log.info("Storyteller worker initialized with pool size 1");
  }

  void notifyConsumers(PromptResult result) {
    for (PromptResultConsumer consumer : resultConsumers) {
      try {
        boolean isResultConsumed = consumer.processPromtResult(result.type(), result);
        if (isResultConsumed) {
          log.info(
              "{} consumed LLM result: {}", consumer.getClass().getSimpleName(), result.type());
          break;
        }
      } catch (Exception e) {
        log.error("Error notifying consumer for result of type {}", result.type(), e);
      }
    }
  }

  public void shutdown() {
    executorService.shutdownNow();
    log.info("Storyteller worker shutdown initiated");
  }
}
