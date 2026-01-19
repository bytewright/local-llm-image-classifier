package de.bytewright.sticker_classifier.domain.img;

import static org.assertj.core.api.Assertions.assertThat;

import de.bytewright.sticker_classifier.domain.img.StickerDeduplicationBySimilarityService.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class StickerDeduplicationBySimilarityServiceTest {
  @Test
  void runClusteringBySimilarityAndRename() throws IOException {
    var testee = new StickerDeduplicationBySimilarityService();
    Path path = Path.of("D:\\_TO SORT\\Line sticker\\pack-classified\\brown");
    var config =
        Config.builder().dryRun(false).exactCopyThreshold(0.0001).similarityThreshold(0.25).build();
    Map<String, List<Path>> stringListMap = testee.processAndRenameFiles(path, config);
    assertThat(stringListMap).isNotEmpty();
  }

  @Test
  void runClusteringBySimilarityEverywhere() throws IOException {
    var testee = new StickerDeduplicationBySimilarityService();
    Path rootPath = Path.of("D:\\_TO SORT\\Line sticker\\pack-classified");
    for (Path path : Files.list(rootPath).filter(Files::isDirectory).toList()) {
      var config =
          Config.builder()
              .dryRun(false)
              .exactCopyThreshold(0.0000)
              .similarityThreshold(0.2)
              .build();
      Map<String, List<Path>> stringListMap = testee.processAndRenameFiles(path, config);
      assertThat(stringListMap).isNotEmpty();
    }
  }
}
