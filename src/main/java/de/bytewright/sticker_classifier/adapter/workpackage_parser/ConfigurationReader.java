package de.bytewright.sticker_classifier.adapter.workpackage_parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.bytewright.sticker_classifier.adapter.workpackage_parser.model.StickerClassificationConfig;
import de.bytewright.sticker_classifier.domain.event.ConfigurationLoadedEvent;
import de.bytewright.sticker_classifier.domain.input.ConfigParser;
import de.bytewright.sticker_classifier.domain.model.ClassificationCategory;
import de.bytewright.sticker_classifier.domain.model.CompoundClassificationCategory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigurationReader implements ConfigParser {

  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  public void handleConfigurationLoaded(ContextRefreshedEvent event) {
    log.info("ContextRefreshedEvent event received!");
    loadAndPublishConfiguration("line-sticker.yaml");
  }

  public void loadAndPublishConfiguration(String configPath) {
    ConfigurationLoadedEvent event = loadConfiguration(Path.of(configPath));
    eventPublisher.publishEvent(event);
  }

  public ConfigurationLoadedEvent loadConfiguration(Path configPath) {
    if (!Files.isRegularFile(configPath)) {
      log.error("Not a regular file: {}", configPath.toAbsolutePath());
      return null;
    }
    log.info("Loading configuration from: {}", configPath);
    try {
      StickerClassificationConfig config = loadYamlConfiguration(configPath);
      validateConfiguration(config);

      Path workDir = Paths.get(config.getWorkDirectory());
      Path outDir = Paths.get(config.getOutputDirectory());
      List<ClassificationCategory> categories =
          config.getClassifications().stream()
              .filter(classification -> classification.getCompoundCategory().isEmpty())
              .map(
                  c -> new ClassificationCategory(c.getName(), c.getPriority(), c.getDescription()))
              .toList();

      List<CompoundClassificationCategory> compoundClassificationCategoryList =
          config.getClassifications().stream()
              .filter(classification -> !classification.getCompoundCategory().isEmpty())
              .map(classification -> createCompound(categories, classification))
              .toList();
      ConfigurationLoadedEvent event =
          new ConfigurationLoadedEvent(
              workDir, outDir, categories, compoundClassificationCategoryList);
      log.info(
          "Configuration loaded successfully. Work directory: {}, Classifications: {}",
          workDir,
          categories.size());
      return event;
    } catch (IOException e) {
      log.error("Failed to load configuration from: {}", configPath, e);
      throw new ConfigurationLoadException("Could not load configuration file: " + configPath, e);
    } catch (IllegalArgumentException e) {
      log.error("Invalid configuration: {}", e.getMessage());
      throw new ConfigurationLoadException("Invalid configuration: " + e.getMessage(), e);
    }
  }

  private CompoundClassificationCategory createCompound(
      List<ClassificationCategory> categories,
      StickerClassificationConfig.Classification classification) {
    Set<ClassificationCategory> categorySet = new HashSet<>();
    for (String name : classification.getCompoundCategory()) {
      ClassificationCategory foundCategory =
          categories.stream()
              .filter(classificationCategory -> classificationCategory.name().equals(name))
              .findAny()
              .orElseThrow();
      categorySet.add(foundCategory);
    }

    return new CompoundClassificationCategory(
        classification.getName(), categorySet, classification.getDescription());
  }

  private StickerClassificationConfig loadYamlConfiguration(Path configPath) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    try (InputStream inputStream = new FileInputStream(configPath.toFile())) {
      return objectMapper.readValue(inputStream, StickerClassificationConfig.class);
    }
  }

  private void validateConfiguration(StickerClassificationConfig config) {
    if (config.getWorkDirectory() == null || config.getWorkDirectory().isBlank()) {
      throw new IllegalArgumentException("Work directory must be specified");
    }

    Path workDir = Paths.get(config.getWorkDirectory());
    if (!Files.exists(workDir)) {
      throw new IllegalArgumentException("Work directory does not exist: " + workDir);
    }

    if (!Files.isDirectory(workDir)) {
      throw new IllegalArgumentException("Work directory path is not a directory: " + workDir);
    }

    if (config.getClassifications() == null || config.getClassifications().isEmpty()) {
      throw new IllegalArgumentException("At least one classification must be defined");
    }

    for (StickerClassificationConfig.Classification classification : config.getClassifications()) {
      if (classification.getName() == null || classification.getName().isBlank()) {
        throw new IllegalArgumentException("Classification name cannot be empty");
      }
      if (classification.getDescription() == null || classification.getDescription().isBlank()) {
        throw new IllegalArgumentException(
            "Classification description cannot be empty for: " + classification.getName());
      }
    }
  }

  public static class ConfigurationLoadException extends RuntimeException {
    public ConfigurationLoadException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
