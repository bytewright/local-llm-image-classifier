package de.bytewright.sticker_classifier.domain.storage;

import de.bytewright.sticker_classifier.domain.llm.PromptRequest;
import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import de.bytewright.sticker_classifier.domain.session.ProcessingState;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public interface SessionStorage {
  UUID createSession(
      List<ClassificationCategory> classifications, Path workDirectory, Path outputDirectory);

  void updateState(UUID sessionId, ProcessingState processingState);

  Path getWorkDir(UUID sessionId);

  Path getResultRootDir(UUID sessionId);

  List<ClassificationCategory> getClassificationCategories(UUID sessionId);

  void storeResult(UUID sessionId, ClassificationResult result);

  void addRequest(UUID sessionId, PromptRequest request);
}
