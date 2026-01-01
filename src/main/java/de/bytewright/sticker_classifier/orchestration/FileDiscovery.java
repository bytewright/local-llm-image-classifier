package de.bytewright.sticker_classifier.orchestration;

import de.bytewright.sticker_classifier.domain.storage.SessionStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDiscovery {
  private final SessionStorage sessionStorage;

  public Collection<ClassifyStickers.FileMetadata> discoverUniqueFiles(UUID sessionId) throws IOException {
    Path workDir = sessionStorage.getWorkDir(sessionId);
    log.info("Phase 1: Discovering files in {}", workDir);

    List<Path> allFiles = getAllImageFiles(workDir);
    int totalFileCount = allFiles.size();
    log.info("Found {} PNG files", totalFileCount);

    Map<String, ClassifyStickers.FileMetadata> filesByHash = new ConcurrentHashMap<>();
    // Calculate hashes and deduplicate
    for (Path filePath : allFiles) {
      try {
        String hash = calculateFileHash(filePath);
        long size = Files.size(filePath);

        if (filesByHash.containsKey(hash)) {
          ClassifyStickers.FileMetadata existing = filesByHash.get(hash);
          log.debug("Duplicate found: {} (original: {})", filePath, existing.originalPath());
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
    return filesByHash.values();
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
}
