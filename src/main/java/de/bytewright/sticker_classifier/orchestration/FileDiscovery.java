package de.bytewright.sticker_classifier.orchestration;

import de.bytewright.sticker_classifier.domain.AppOrchestrationConfig;
import de.bytewright.sticker_classifier.domain.img.ImageValidationService;
import de.bytewright.sticker_classifier.domain.storage.SessionStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDiscovery {
  private final SessionStorage sessionStorage;
  private final ImageValidationService imageService;
  private final AppOrchestrationConfig appOrchestrationConfig;

  public Collection<Path> discoverUniqueFiles(UUID sessionId) throws IOException {
    Path workDir = sessionStorage.getWorkDir(sessionId);
    log.info("Phase 1: Discovering files in {}", workDir);

    List<Path> allFiles = getAllImageFiles(workDir);
    int totalFileCount = allFiles.size();
    log.info("Found {} PNG files", totalFileCount);
    Set<DuplicateMetaInfo> duplicates = new HashSet<>();
    Map<String, ClassifyStickers.FileMetadata> filesByHash = new ConcurrentHashMap<>();
    // Calculate hashes and deduplicate
    for (Path filePath : allFiles) {
      try {
        String hash = calculateFileHash(filePath);
        long size = Files.size(filePath);

        if (filesByHash.containsKey(hash)) {
          ClassifyStickers.FileMetadata existing = filesByHash.get(hash);
          log.debug("Duplicate found: {} (original: {})", filePath, existing.originalPath());
          duplicates.add(new DuplicateMetaInfo(existing.originalPath(), filePath));
        } else {
          ClassifyStickers.FileMetadata metadata =
              new ClassifyStickers.FileMetadata(filePath, hash, size);
          filesByHash.put(hash, metadata);
        }
      } catch (Exception e) {
        log.error("Error processing file {}", filePath, e);
      }
    }

    int uniqueFiles = filesByHash.size();
    int duplicateFiles = totalFileCount - uniqueFiles;
    log.info("Deduplication complete: {} unique files, {} duplicates", uniqueFiles, duplicateFiles);
    if (appOrchestrationConfig.getClassification().isRemoveDuplicates()) {
      try {
        for (DuplicateMetaInfo duplicate : duplicates) {
          String orgFileName = duplicate.original().toFile().getName();
          Path orgPath = duplicate.duplicate();
          String orgDirName = orgPath.getParent().toFile().getName();
          Path processingFinishedDir =
              orgPath.getParent().getParent().resolve(orgDirName + "_duplicates");
          Files.createDirectories(processingFinishedDir);
          Path moveTarget =
              processingFinishedDir.resolve(
                  "%s_%s".formatted(orgFileName, orgPath.toFile().getName()));
          Files.move(orgPath, moveTarget);
        }
      } catch (IOException e) {
        log.error("Error while removing duplicates", e);
      }
    }
    Collection<Path> paths =
        imageService.validateAndFixImages(
            filesByHash.values().stream()
                .map(ClassifyStickers.FileMetadata::originalPath)
                .toList());
    return paths;
  }

  private List<Path> getAllImageFiles(Path path) throws IOException {
    try (Stream<Path> paths = Files.walk(path)) {
      return paths.filter(Files::isRegularFile).filter(this::isPngFile).toList();
    }
  }

  private boolean isPngFile(Path path) {
    String fileName = path.getFileName().toString().toLowerCase();
    return fileName.endsWith(".png");
  }

  private String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] fileBytes = Files.readAllBytes(file);
    byte[] hashBytes = digest.digest(fileBytes);

    StringBuilder hexString = new StringBuilder();
    for (byte b : hashBytes) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) hexString.append('0');
      hexString.append(hex);
    }
    return hexString.toString();
  }

  private record DuplicateMetaInfo(Path original, Path duplicate) {}
}
