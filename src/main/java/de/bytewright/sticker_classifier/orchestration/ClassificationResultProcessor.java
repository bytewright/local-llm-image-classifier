package de.bytewright.sticker_classifier.orchestration;

import de.bytewright.sticker_classifier.domain.AppOrchestrationConfig;
import de.bytewright.sticker_classifier.domain.llm.*;
import de.bytewright.sticker_classifier.domain.llm.utils.LanguageCodeCleanerService;
import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import de.bytewright.sticker_classifier.domain.model.ClassificationResult;
import de.bytewright.sticker_classifier.domain.model.CompoundClassificationCategory;
import de.bytewright.sticker_classifier.domain.storage.SessionStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassificationResultProcessor implements PromptResultConsumer {
  private final SessionStorage sessionStorage;
  private final LanguageCodeCleanerService languageCodeCleanerService;
  private final AppOrchestrationConfig appOrchestrationConfig;

  @Override
  public boolean processPromtResult(PromptType promtType, PromptResult promptResult) {
    return switch (promptResult) {
      case ClassificationPromptResult classificationPromptResult ->
          processClassificationResult(classificationPromptResult);
      case StringPromptResult stringPromptResult -> false;
      case ErrorPromptResult errorPromptResult -> false;
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
    String name = request.imagePath().getFileName().toFile().getName();
    String targetFileName =
        "%s_%s_%s_%s"
            .formatted(
                languageCodeCleanerService.cleanLanguageResponse(result.getTextLanguageGuess()),
                result.getEmoji(),
                languageCodeCleanerService.sanitizeForFilename(result.getKeyword()),
                name);
    Set<String> categoryNamesFromTags = getCategoryNamesFromTags(request, result);
    for (String categoryNameFromTag : categoryNamesFromTags) {
      Path dir = resultRootDir.resolve(categoryNameFromTag);
      Path outPath = dir.resolve(targetFileName);
      try {
        log.debug("Moving '{}' to: {}", name, outPath);
        copyFile(orgPath, outPath);
      } catch (IOException e) {
        log.error("Error while copying {}", orgPath, e);
      }
    }
    if (appOrchestrationConfig.getClassification().isRemoveOriginalFile()) {
      try {
        String orgDirName = orgPath.getParent().toFile().getName();
        Path processingFinishedDir =
            orgPath.getParent().getParent().resolve(orgDirName + "_processed");
        Files.createDirectories(processingFinishedDir);
        Path moveTarget = processingFinishedDir.resolve(orgPath.toFile().getName());
        Files.move(orgPath, moveTarget);
      } catch (IOException e) {
        log.error("Error while removing {}", orgPath, e);
      }
    }
    return true;
  }

  private void copyFile(Path orgPath, Path outPath) throws IOException {
    Files.createDirectories(outPath.getParent());
    if (!Files.isRegularFile(outPath)) {
      Files.copy(orgPath, outPath);
    } else {
      log.info("Skipping copy of file because it already exists! {}", outPath);
    }
  }

  private Set<String> getCategoryNamesFromTags(
      PromptRequestWithImage request, ClassificationResult result) {
    Set<String> nameStrings = new HashSet<>();
    List<ClassificationCategory> classificationCategories =
        sessionStorage.getClassificationCategories(request.requestParameter()).stream()
            .sorted(Comparator.comparing(ClassificationCategory::priority))
            .toList();
    Set<ClassificationCategory> foundCategories = new HashSet<>();
    for (ClassificationCategory category : classificationCategories) {
      if (result.getDetectedTags().contains(category.name().toLowerCase())) {
        foundCategories.add(category);
        nameStrings.add(category.name());
      }
    }
    List<CompoundClassificationCategory> compoundCategories =
        sessionStorage.getCompoundCategories(request.requestParameter());
    compoundCategories.stream()
        .filter(ccc -> ccc.categorySet().equals(foundCategories))
        .map(CompoundClassificationCategory::name)
        .forEach(nameStrings::add);
    if (nameStrings.isEmpty()) {
      nameStrings.add("other");
    }
    return nameStrings;
  }
}
