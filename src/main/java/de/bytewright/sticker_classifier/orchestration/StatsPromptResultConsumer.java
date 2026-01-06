package de.bytewright.sticker_classifier.orchestration;

import de.bytewright.sticker_classifier.domain.llm.*;
import de.bytewright.sticker_classifier.orchestration.llm.PromptRequestCoordinator;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsPromptResultConsumer implements PromptResultConsumer {
  private static final Duration MAX_RESULT_RETENTION = Duration.ofHours(2);
  private final AtomicLong totalSuccessCount = new AtomicLong(0);
  private final AtomicLong totalErrorCount = new AtomicLong(0);
  private final ConcurrentLinkedDeque<Instant> recentRequests = new ConcurrentLinkedDeque<>();
  private final PromptRequestCoordinator coordinator;

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

    double avgPerMin = getAvgThroughputPerMinute(5);
    double throughputPerHour = getAvgThroughputPerMinute(60);

    log.info(
        "Throughput: {}/min (5min avg), {}/hour | Total: {} success, {} errors | Queue: {}, ETA: {}",
        String.format("%.2f", avgPerMin),
        String.format("%.0f", throughputPerHour),
        totalSuccessCount.get(),
        totalErrorCount.get(),
        coordinator.getQueueSize(),
        formatETA(calculateETA()));
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
    Instant cutoff = now.minus(MAX_RESULT_RETENTION);
    while (!recentRequests.isEmpty() && recentRequests.peekFirst().isBefore(cutoff)) {
      recentRequests.pollFirst();
    }
  }

  /**
   * Gets the throughput count for a specific timeframe in minutes.
   *
   * @param minutes The timeframe in minutes to calculate throughput for
   * @return The number of requests in the given timeframe
   */
  public double getThroughputForTimeframe(int minutes) {
    Instant now = Instant.now();
    cleanupOldRequests(now);
    Instant cutoff = now.minusSeconds(minutes * 60L);

    return recentRequests.stream().filter(instant -> instant.isAfter(cutoff)).count();
  }

  /**
   * Calculates average throughput per minute over a given timeframe.
   *
   * @param minutes The timeframe in minutes to calculate average over
   * @return Average requests per minute
   */
  public double getAvgThroughputPerMinute(int minutes) {
    double count = getThroughputForTimeframe(minutes);
    return count / minutes;
  }

  /**
   * Calculates estimated time to complete remaining queue based on recent throughput.
   *
   * @return Duration until queue completion, or null if cannot be estimated
   */
  public Duration calculateETA() {
    int queueSize = coordinator.getQueueSize();
    if (queueSize == 0) {
      return Duration.ZERO;
    }

    double throughputPerMinute = getAvgThroughputPerMinute(5);
    if (throughputPerMinute <= 0.01) {
      return null; // Not enough data or no throughput
    }

    double minutesRemaining = queueSize / throughputPerMinute;
    long secondsRemaining = (long) (minutesRemaining * 60);
    return Duration.ofSeconds(secondsRemaining);
  }

  private String formatETA(Duration eta) {
    if (eta == null) {
      return "unknown";
    }
    if (eta.isZero()) {
      return "done";
    }

    long hours = eta.toHours();
    long minutes = eta.toMinutesPart();
    long seconds = eta.toSecondsPart();

    if (hours > 0) {
      return String.format("%dh %dm", hours, minutes);
    } else if (minutes > 0) {
      return String.format("%dm %ds", minutes, seconds);
    } else {
      return String.format("%ds", seconds);
    }
  }

  public long getTotalSuccessCount() {
    return totalSuccessCount.get();
  }

  public long getTotalErrorCount() {
    return totalErrorCount.get();
  }

  public double getCurrentThroughput() {
    return getAvgThroughputPerMinute(5);
  }
}
