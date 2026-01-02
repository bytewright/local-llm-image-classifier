package de.bytewright.sticker_classifier.orchestration;

import de.bytewright.sticker_classifier.domain.llm.*;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import de.bytewright.sticker_classifier.domain.storage.SessionStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationResultProcessor implements PromptResultConsumer {
  private final SessionStorage sessionStorage;

  @Override
  public boolean processPromtResult(PromptType promtType, PromptResult promptResult) {
    return switch (promptResult) {
      case ClassificationPromptResult classificationPromptResult ->
          processClassificationResult(classificationPromptResult);
      case StringPromptResult stringPromptResult -> false;
    };
  }

  private boolean processClassificationResult(
      ClassificationPromptResult classificationPromptResult) {
    PromptRequestWithImage request = (PromptRequestWithImage) classificationPromptResult.request();
    ClassificationResult result = classificationPromptResult.getClassificationResult();
    sessionStorage.storeResult(request.requestParameter(), result);
    if (result != null) {
      return actOnClassification(request, result);
    }
    return false;
  }

  private boolean actOnClassification(PromptRequestWithImage request, ClassificationResult result) {
    Path orgPath = request.imagePath().toAbsolutePath();
    log.info("Got classificationResult for path: {}\nClassified as: {}", orgPath, result);
    Path resultRootDir = sessionStorage.getResultRootDir(request.requestParameter());
    Path dir = resultRootDir.resolve(result.getCategoryName());
    String name = request.imagePath().getFileName().toFile().getName();
    if (result.isHasText()) {
      name = result.getTextLanguageGuess() + "_" + name;
    }
    String targetFileName =
        "%s_%s_%s"
            .formatted(
                result.isHasText() ? result.getTextLanguageGuess().toLowerCase() : "int",
                result.getKeyword(),
                name);
    Path outPath = dir.resolve(targetFileName);
    try {
      log.info("Moving '{}' to: {}", name, outPath);
      Files.copy(orgPath, outPath);
    } catch (IOException e) {
      log.error("Error while moving {}", orgPath, e);
      return false;
    }
    return true;
  }
}
