package de.bytewright.sticker_classifier.domain.img;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImageDeduplicationService {

  private static final Set<String> IMAGE_EXTENSIONS =
      Set.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".tiff", ".tif");

  /**
   * Deduplicates images in subfolders of the root directory. For duplicates found across multiple
   * directories, creates a new directory named after all source directories and moves one copy
   * there.
   *
   * @param rootPath the root directory containing subfolders with images
   * @param b
   * @throws IOException if file operations fail
   */
  public void deduplicateImages(Path rootPath, boolean dryRun) throws IOException {
    if (!Files.isDirectory(rootPath)) {
      throw new IllegalArgumentException("Path must be a directory: " + rootPath);
    }
    // Discover all image files and calculate hashes
    List<FileWithHash> files = discoverAndHashImages(rootPath);
    log.info("Found {} unique files", files.size());
    // Process duplicates
    processDuplicates(rootPath, files, dryRun);
  }

  private List<FileWithHash> discoverAndHashImages(Path rootPath) throws IOException {
    try (Stream<Path> paths = Files.walk(rootPath)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(this::isImageFile)
          .map(this::toFileWithHashMutable)
          .flatMap(Optional::stream)
          .collect(
              Collectors.toMap(
                  FileWithHash::hash,
                  List::of,
                  (objects, objects2) ->
                      Stream.of(objects, objects2).flatMap(Collection::stream).toList()))
          .values()
          .stream()
          .map(fileWithHashes -> fileWithHashes.stream().reduce(FileWithHash::merge))
          .flatMap(Optional::stream)
          .toList();
    }
  }

  private Optional<FileWithHash> toFileWithHashMutable(Path path) {
    try {
      String hash = calculateHash(path);
      return Optional.of(new FileWithHash(hash, new ArrayList<>(List.of(path))));
    } catch (IOException | NoSuchAlgorithmException e) {
      System.err.println("Error hashing file " + path + ": " + e.getMessage());
    }
    return Optional.empty();
  }

  private void processDuplicates(Path rootPath, List<FileWithHash> fileWithHashes, boolean dryRun)
      throws IOException {
    for (FileWithHash entry : fileWithHashes) {
      List<Path> duplicates = entry.paths();

      if (duplicates.size() > 1) {
        // Get unique parent directory names
        Set<String> parentDirs = new TreeSet<>();
        for (Path file : duplicates) {
          Path parent = file.getParent();
          if (parent != null) {
            parentDirs.add(parent.getFileName().toString());
          }
        }

        // Create new directory name from all parent directories
        String newDirName = String.join("_", parentDirs);
        Path newDir = rootPath.resolve(newDirName);

        // Create the new directory if it doesn't exist
        if (!dryRun && !Files.exists(newDir)) {
          Files.createDirectory(newDir);
        }

        // Keep the first file, move it to new directory
        Path firstFile = duplicates.get(0);
        Path targetPath = newDir.resolve(firstFile.getFileName());

        // Handle filename conflicts
        targetPath = getUniqueFilePath(targetPath);

        // Copy first file to new directory
        if (!dryRun) {
          Files.copy(firstFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

          // Delete all duplicate files (including the first one)
          for (Path duplicate : duplicates) {
            try {
              Files.delete(duplicate);
            } catch (IOException e) {
              System.err.println("Error deleting file " + duplicate + ": " + e.getMessage());
            }
          }
        } else {
          log.info("Would move: {} to {}", firstFile, targetPath);
        }
      }
    }
  }

  private boolean isImageFile(Path file) {
    String fileName = file.getFileName().toString().toLowerCase();
    return IMAGE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
  }

  private String calculateHash(Path file) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] fileBytes = Files.readAllBytes(file);
    byte[] hashBytes = digest.digest(fileBytes);

    // Convert to hex string
    StringBuilder sb = new StringBuilder();
    for (byte b : hashBytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private Path getUniqueFilePath(Path path) {
    if (!Files.exists(path)) {
      return path;
    }

    String fileName = path.getFileName().toString();
    int dotIndex = fileName.lastIndexOf('.');
    String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";

    int counter = 1;
    Path newPath;
    do {
      newPath = path.getParent().resolve(baseName + "_" + counter + extension);
      counter++;
    } while (Files.exists(newPath));

    return newPath;
  }

  record FileWithHash(String hash, List<Path> paths) {
    FileWithHash merge(FileWithHash other) {
      paths.addAll(other.paths());
      return this;
    }
  }
}
