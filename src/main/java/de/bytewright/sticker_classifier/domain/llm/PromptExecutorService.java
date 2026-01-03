package de.bytewright.sticker_classifier.domain.llm;

import java.util.concurrent.ExecutorService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PromptExecutorService {
  private final ExecutorService executorService;

  public void submit(Runnable processingLoop) {
    executorService.submit(processingLoop);
  }

  public void shutdownNow() {
    executorService.shutdownNow();
  }
}
