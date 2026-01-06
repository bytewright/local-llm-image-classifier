package de.bytewright.sticker_classifier.domain.img;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ImageValidationServiceTest {

  @Test
  void validateAndFixImages() throws IOException {
    // GIVEN
    ImageValidationService validationService = new ImageValidationService();
    Path testImg = Path.of("D:\\_TO SORT\\Line sticker\\pack_error\\33068@2x.png");

    // WHEN
    Collection<Path> paths = validationService.validateAndFixImages(Set.of(testImg));

    // THEN
    assertThat(paths).hasSize(1);
  }
}
