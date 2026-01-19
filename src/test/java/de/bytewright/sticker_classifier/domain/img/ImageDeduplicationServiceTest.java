package de.bytewright.sticker_classifier.domain.img;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ImageDeduplicationServiceTest {
  @Test
  void runDeduplicateByHash() throws IOException {
    var testee = new ImageDeduplicationService();
    Path path = Path.of("D:\\_TO SORT\\Line sticker\\pack-classified\\");
    testee.deduplicateImages(path, false);
  }

  @Test
  void name() throws IOException {
    Pattern GROUP_PREFIX_PRESENT = Pattern.compile("^[0-9]{5}_.*");
    Pattern STICKER_ID_FINDER = Pattern.compile("^(.*)_([0-9]{5,8})(@2x)?.png$");
    Path path = Path.of("D:\\_TO SORT\\Line sticker\\pack-classified\\");
    Map<Path, Path> renames = new HashMap<>();
    for (Path image : discoverImages(path)) {
      String fileName = image.getFileName().toString();
      Matcher matcherPrefix = GROUP_PREFIX_PRESENT.matcher(fileName);
      if (matcherPrefix.matches()) {
        fileName = fileName.substring(6);
      }
      Matcher matcherId = STICKER_ID_FINDER.matcher(fileName);
      if (matcherId.matches()) {
        String stickerName = matcherId.group(1);
        String stickerId = matcherId.group(2);
        fileName = "%08d_%s.png".formatted(Integer.parseInt(stickerId), stickerName);
      }
      Path newPath = image.getParent().resolve(fileName);
      if (!newPath.equals(image)) {
        renames.put(image, newPath);
      }
    }
    for (Map.Entry<Path, Path> entry : renames.entrySet()) {
      Files.move(entry.getKey(), entry.getValue());
    }
  }

  private List<Path> discoverImages(Path rootPath) throws IOException {
    try (Stream<Path> paths = Files.walk(rootPath)) {
      return paths
          .filter(Files::isRegularFile)
          .flatMap(
              path -> {
                try {
                  return Files.isRegularFile(path)
                      ? Stream.of(path)
                      : Files.isDirectory(path) ? discoverImages(path).stream() : Stream.of();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              })
          .toList();
    }
  }
}
