package de.bytewright.sticker_classifier.orchestration.llm;

import de.bytewright.sticker_classifier.domain.llm.PromptResultConsumer;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PromptRequestAppContextListener {
  private final Set<PromptResultConsumer> resultConsumers;
  private final WorkerFactory workerFactory;

  @EventListener
  public void onApplicationEvent(ContextRefreshedEvent event) {
    log.info("Initializing storyteller worker on application startup");
    List<PromptResultConsumer> sortedConsumers =
        resultConsumers.stream()
            .sorted(Comparator.comparing(PromptResultConsumer::consumerPriority))
            .toList();
    workerFactory.getResultConsumers().addAll(sortedConsumers);
    workerFactory.initialize();
  }

  @EventListener
  public void onApplicationEvent(ContextClosedEvent event) {
    log.info("Shutting down storyteller worker on application shutdown");
    workerFactory.shutdown();
  }
}
