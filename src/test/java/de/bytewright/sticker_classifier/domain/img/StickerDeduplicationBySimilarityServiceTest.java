package de.bytewright.sticker_classifier.domain.img;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class StickerDeduplicationBySimilarityServiceTest {
  @Test
  void runClusteringAndRename() throws IOException {
    var testee = new StickerDeduplicationBySimilarityService();
    Path path = Path.of("D:\\_TO SORT\\Line sticker\\pack-classified\\sally");
    Map<String, List<Path>> stringListMap = testee.processAndRenameFiles(path, false);
    assertThat(stringListMap).isNotEmpty();
  }

  @Test
  void name2() throws IOException {
    Path path = Path.of("D:\\_TO SORT\\Line sticker\\pack-classified\\cony");
    Map<String, Set<Path>> filesByPrefix = new HashMap<>();
    try (Stream<Path> stream = Files.list(path)) {
      stream
          .filter(Files::isRegularFile)
          .forEach(
              path1 -> {
                String fileName = path1.toFile().getName();
                String prefix = fileName.split("_")[0];
                filesByPrefix.computeIfAbsent(prefix, s -> new HashSet<>()).add(path1);
              });
    } catch (IOException e) {
      log.error("Error reading directory: {}", path, e);
    }
    for (Map.Entry<String, Set<Path>> entry : filesByPrefix.entrySet()) {
      Path prefixDir = path.resolve(entry.getKey());
      Files.createDirectories(prefixDir);
      for (Path path1 : entry.getValue()) {
        Path destPath = prefixDir.resolve(path1.toFile().getName());
        Files.move(path1, destPath, StandardCopyOption.ATOMIC_MOVE);
      }
    }
  }

  @Test
  void name3() throws IOException {
    Path path = Path.of("D:\\_TO SORT\\Line sticker\\pack-classified\\cony");
    for (Path subDir : Files.list(path).filter(Files::isDirectory).toList()) {
      for (Path filePath : Files.list(subDir).filter(Files::isRegularFile).toList()) {
        String name = filePath.toFile().getName();
        Path targetPath = path.resolve(name);
        Files.move(filePath, targetPath);
      }
    }
  }

  /** Finds all PNG files in directory (non-recursive) */
  private List<Path> findPngFiles(Path path) {
    if (Files.isRegularFile(path)) {
      if (path.toString().toLowerCase().endsWith(".png")) {
        return Collections.singletonList(path);
      }
      return Collections.emptyList();
    }

    try (Stream<Path> stream = Files.list(path)) {
      return stream
          .map(p -> Files.isDirectory(p) ? findPngFiles(p) : List.of(p))
          .flatMap(Collection::stream)
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().toLowerCase().endsWith(".png"))
          .toList();
    } catch (IOException e) {
      log.error("Error reading directory: {}", path, e);
      return Collections.emptyList();
    }
  }
}
