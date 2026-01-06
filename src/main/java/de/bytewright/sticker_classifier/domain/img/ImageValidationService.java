package de.bytewright.sticker_classifier.domain.img;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ImageValidationService {
  public Collection<Path> validateAndFixImages(Collection<Path> values) throws IOException {
    Set<Path> files = new HashSet<>();
    for (Path filePath : values) {
      validateAndFixImage(filePath).ifPresent(files::add);
    }
    return files;
  }

  private Optional<Path> validateAndFixImage(Path filePath) throws IOException {
    if (!Files.isRegularFile(filePath)) return Optional.empty();
    byte[] imgBytes = Files.readAllBytes(filePath);
    if (CgBIPNGConverter.isCgBIPNG(imgBytes)) {
      log.info("Detected Apple formated png, attempting fix for: {}", filePath);
      byte[] fixedImg = CgBIPNGConverter.convertCgBIToStandardPNG(imgBytes);
      String fileName = filePath.toFile().getName();
      Path savedOrgCopy = filePath.getParent().getParent().resolve("broken_imgs").resolve(fileName);
      Files.createDirectories(savedOrgCopy.getParent());
      Files.copy(filePath, savedOrgCopy);
      Files.write(filePath, fixedImg, StandardOpenOption.TRUNCATE_EXISTING);
    }
    return Optional.of(filePath);
  }
}
