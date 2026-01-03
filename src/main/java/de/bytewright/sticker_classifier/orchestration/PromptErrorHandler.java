package de.bytewright.sticker_classifier.orchestration;

import de.bytewright.sticker_classifier.domain.event.ImagePromptRequestFailedEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptErrorHandler {
  @EventListener
  public void handleImagePromptRequestFailedEvent(ImagePromptRequestFailedEvent event) {
    Path orgPath = event.imagePath();
    try {
      String orgDirName = orgPath.getParent().toFile().getName();
      Path errorDir = orgPath.getParent().getParent().resolve(orgDirName + "_error");
      Files.createDirectories(errorDir);
      Path moveTarget = errorDir.resolve(orgPath.toFile().getName());
      Files.move(orgPath, moveTarget);
    } catch (IOException e) {
      log.error("Error while removing {}", orgPath, e);
    }
  }
}
