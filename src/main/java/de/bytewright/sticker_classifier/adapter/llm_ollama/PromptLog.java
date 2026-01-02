package de.bytewright.sticker_classifier.adapter.llm_ollama;

import de.bytewright.sticker_classifier.domain.llm.PromptRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PromptLog {

  private static final String BASE_LOG_DIR = "promtLog";
  private static final String REQUEST_SEPARATOR = "=== REQUEST ===";
  private static final String RESPONSE_SEPARATOR = "--- RESPONSE ---";
  private static final String CONTEXT_SEPARATOR = "--- CONTEXT ---";
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
  private static final DateTimeFormatter DETAILED_TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  /** Get the session timestamp used for this logging session. */
  @Getter private final String sessionTimestamp;

  private final Path sessionLogDir;
  private final Map<String, Path> logFiles = new ConcurrentHashMap<>();

  public PromptLog() {
    this.sessionTimestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    this.sessionLogDir = Paths.get(BASE_LOG_DIR, sessionTimestamp);
    initializeLogDirectory();
  }

  private void initializeLogDirectory() {
    try {
      Files.createDirectories(sessionLogDir);
      log.info("Created log directory: {}", sessionLogDir.toAbsolutePath());
    } catch (IOException e) {
      log.error("Failed to create log directory: {}", sessionLogDir.toAbsolutePath(), e);
      throw new RuntimeException("Could not initialize logging directory", e);
    }
  }

  public void logPrompt(PromptRequest prompt, String context) {
    try {
      Path logFile = getOrCreateLogFile(prompt);
      String timestamp = LocalDateTime.now().format(DETAILED_TIMESTAMP_FORMATTER);

      StringBuilder logEntry = new StringBuilder();
      logEntry.append(REQUEST_SEPARATOR).append("\n");
      logEntry.append("Timestamp: ").append(timestamp).append("\n");
      logEntry.append("Request ID: ").append(prompt.requestParameter()).append("\n");
      logEntry.append("Prompt Type: ").append(prompt.promptType()).append("\n");
      logEntry.append("Prompt: ").append(prompt.prompt()).append("\n");

      if (prompt.responseJsonFormat().isPresent()) {
        logEntry
            .append("Expected JSON Format: ")
            .append(prompt.responseJsonFormat().get())
            .append("\n");
      }

      if (context != null && !context.trim().isEmpty()) {
        logEntry.append(CONTEXT_SEPARATOR).append("\n");
        logEntry.append(context).append("\n");
      }

      logEntry.append("\n");

      Files.writeString(
          logFile, logEntry.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);

      log.debug("Logged request for {} with ID {}", prompt.promptType(), prompt.requestParameter());

    } catch (IOException e) {
      log.error(
          "Failed to log prompt for {} with ID {}",
          prompt.promptType(),
          prompt.requestParameter(),
          e);
    }
  }

  public void logResponse(PromptRequest prompt, String content) {
    try {
      Path logFile = getOrCreateLogFile(prompt);
      String timestamp = LocalDateTime.now().format(DETAILED_TIMESTAMP_FORMATTER);

        String logEntry = RESPONSE_SEPARATOR + "\n" +
                          "Response Timestamp: " + timestamp + "\n" +
                          "Content: " + content + "\n" +
                          "\n" + "=".repeat(80) + "\n\n";

      Files.writeString(
          logFile, logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

      log.debug(
          "Logged response for {} with ID {}", prompt.promptType(), prompt.requestParameter());

    } catch (IOException e) {
      log.error(
          "Failed to log response for {} with ID {}",
          prompt.promptType(),
          prompt.requestParameter(),
          e);
    }
  }

  private Path getOrCreateLogFile(PromptRequest prompt) {
    String fileKey = prompt.requestParameter().toString() + "_" + prompt.promptType().name();

    return logFiles.computeIfAbsent(
        fileKey,
        key -> {
          String fileName =
              String.format(
                  "%s_%s.txt", prompt.requestParameter().toString(), prompt.promptType().name());
          return sessionLogDir.resolve(fileName);
        });
  }
}
