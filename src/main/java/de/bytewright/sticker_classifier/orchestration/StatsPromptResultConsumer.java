package de.bytewright.sticker_classifier.orchestration;

import de.bytewright.sticker_classifier.domain.llm.*;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StatsPromptResultConsumer implements PromptResultConsumer {
  private static final int WINDOW_SIZE_SECONDS = 60;

  private final AtomicLong totalSuccessCount = new AtomicLong(0);
  private final AtomicLong totalErrorCount = new AtomicLong(0);
  private final ConcurrentLinkedDeque<Instant> recentRequests = new ConcurrentLinkedDeque<>();

  @Override
  public int consumerPriority() {
    return 0;
  }

  @Override
  public boolean processPromtResult(PromptType promtType, PromptResult promptResult) {
    switch (promptResult) {
      case ErrorPromptResult errorPromptResult -> countError(errorPromptResult);
      default -> countSuccess(promptResult);
    }
    double throughput = calculateThroughput();
    log.info(
        "Current throughput: {}/min (total success: {}, total errors: {})",
        String.format("%.2f", throughput),
        totalSuccessCount.get(),
        totalErrorCount.get());
    return false;
  }

  private void countSuccess(PromptResult promptResult) {
    totalSuccessCount.incrementAndGet();
    recordRequest();
  }

  private void countError(ErrorPromptResult errorPromptResult) {
    totalErrorCount.incrementAndGet();
    recordRequest();
  }

  private void recordRequest() {
    Instant now = Instant.now();
    recentRequests.addLast(now);
    cleanupOldRequests(now);
  }

  private void cleanupOldRequests(Instant now) {
    Instant cutoff = now.minusSeconds(WINDOW_SIZE_SECONDS);
    while (!recentRequests.isEmpty() && recentRequests.peekFirst().isBefore(cutoff)) {
      recentRequests.pollFirst();
    }
  }

  private double calculateThroughput() {
    Instant now = Instant.now();
    cleanupOldRequests(now);
    return recentRequests.size();
  }

  public long getTotalSuccessCount() {
    return totalSuccessCount.get();
  }

  public long getTotalErrorCount() {
    return totalErrorCount.get();
  }

  public double getCurrentThroughput() {
    return calculateThroughput();
  }
}
