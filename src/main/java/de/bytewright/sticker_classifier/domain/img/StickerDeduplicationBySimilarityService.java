package de.bytewright.sticker_classifier.domain.img;

import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StickerDeduplicationBySimilarityService {
  private static final double EXACT_COPY_THRESHOLD = 0.01;
  private static final double SIMILARITY_THRESHOLD = 0.16; // 0.1 = 90% similar
  private static final int HASH_PRECISION = 64;
  private static final Pattern GROUP_PREFIX_PRESENT = Pattern.compile("^[0-9]{5}_.*");

  /**
   * Processes a directory: finds PNG files, clusters by visual similarity, and renames them with
   * group ID prefix.
   *
   * @param path Path to directory or single file
   * @return Map of group IDs to list of renamed files
   */
  public Map<String, List<Path>> processAndRenameFiles(Path path, boolean dryRun)
      throws IOException {
    if (!Files.exists(path)) {
      throw new IllegalArgumentException("Path does not exist: " + path.toAbsolutePath());
    }

    List<Path> pngFiles = findPngFiles(path);
    log.info("Found {} PNG files to process", pngFiles.size());

    if (pngFiles.isEmpty()) {
      log.warn("No PNG files found at: {}", path.toAbsolutePath());
      return Collections.emptyMap();
    }

    List<ClusteredFiles> clusters = clusterByContent(pngFiles);
    log.info("Created {} clusters from {} files", clusters.size(), pngFiles.size());
    Comparator<ClusteredFiles> comparator =
        Comparator.comparing(clusteredFiles -> clusteredFiles.filePaths().size());
    clusters =
        autoDeleteFullMatches(clusters, dryRun).stream().sorted(comparator.reversed()).toList();
    Map<String, List<Path>> renamedFiles = renameFilesWithGroupId(clusters, dryRun);
    log.info(
        "Successfully renamed {} files across {} groups",
        renamedFiles.values().stream().mapToInt(List::size).sum(),
        renamedFiles.size());

    return renamedFiles;
  }

  private List<ClusteredFiles> autoDeleteFullMatches(List<ClusteredFiles> clusters, boolean dryRun)
      throws IOException {
    List<ClusteredFiles> resultList = new ArrayList<>(clusters.size());
    for (ClusteredFiles cluster : clusters) {
      if (cluster.avgScore() <= EXACT_COPY_THRESHOLD && cluster.filePaths().size() > 1) {
        log.info(
            "Detected full match (score:{}), removing {} images...",
            cluster.avgScore(),
            cluster.filePaths().size() - 1);
        Path toKeep =
            cluster.filePaths().stream()
                .min(
                    Comparator.comparing(
                        path -> {
                          try {
                            return Files.size(path);
                          } catch (IOException e) {
                            throw new RuntimeException(e);
                          }
                        }))
                .orElseThrow();

        if (!dryRun) {
          for (Path filePath : cluster.filePaths()) {
            if (toKeep.equals(filePath)) continue;
            Files.delete(filePath);
          }
          resultList.add(new ClusteredFiles(0.999, List.of(toKeep)));
        } else {
          List<Path> newPaths = new ArrayList<>();
          newPaths.add(toKeep);
          for (Path filePath : cluster.filePaths()) {
            if (toKeep.equals(filePath)) continue;
            String renamed = filePath.toFile().getName().split(".png")[0] + "_DELETE-ME.png";
            Path path = filePath.getParent().resolve(renamed);
            Files.move(filePath, path);
            newPaths.add(path);
          }
          resultList.add(new ClusteredFiles(cluster.avgScore(), newPaths));
        }
      } else resultList.add(cluster);
    }
    return resultList;
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

  /** Clusters files by perceptual hash similarity */
  private List<ClusteredFiles> clusterByContent(List<Path> files) throws IOException {
    PerceptiveHash pHash = new PerceptiveHash(HASH_PRECISION);
    pHash.setOpaqueHandling(Color.BLACK, 253);
    Map<Path, Hash> fileHashes = new HashMap<>();

    // Calculate hashes for all files
    for (Path file : files) {
      BufferedImage img = ImageIO.read(file.toFile());
      if (img != null) {
        Hash hash = pHash.hash(img);
        fileHashes.put(file, hash);
      } else {
        log.warn("Could not read image: {}", file);
      }
    }
    log.info("Created {} hashes", fileHashes.size());
    // Cluster files by similarity
    Set<SimilarityScore> scored = new HashSet<>();
    Set<ComputedPairs> computedPairs = new HashSet<>();
    int i = 0;
    for (Map.Entry<Path, Hash> entry1 : fileHashes.entrySet()) {
      for (Map.Entry<Path, Hash> entry2 : fileHashes.entrySet()) {
        if (entry1.getKey().equals(entry2.getKey())) {
          continue;
        }
        ComputedPairs pairs = new ComputedPairs(entry1.getKey(), entry2.getKey());
        if (computedPairs.contains(pairs)) {
          continue;
        }
        double similarity = entry1.getValue().normalizedHammingDistance(entry2.getValue());
        computedPairs.add(pairs);
        if (similarity <= SIMILARITY_THRESHOLD) {
          log.info(
              "{} - {} - {} vs {}",
              "%.4f".formatted(similarity),
              "%04d/%04d".formatted(i, fileHashes.size()),
              entry1.getKey().getFileName(),
              entry2.getKey().getFileName());
          scored.add(
              new SimilarityScore(
                  similarity,
                  entry1.getKey(),
                  entry1.getValue(),
                  entry2.getKey(),
                  entry2.getValue()));
        }
      }
      i++;
    }
    List<ClusteredFiles> clusters = createClusters(scored);
    return reAddUnique(files, clusters);
  }

  private List<ClusteredFiles> reAddUnique(List<Path> allFiles, List<ClusteredFiles> clusters) {
    Set<Path> clusteredFiles =
        clusters.stream()
            .map(ClusteredFiles::filePaths)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    List<ClusteredFiles> uniques = new ArrayList<>();
    for (Path filePath : allFiles) {
      if (!clusteredFiles.contains(filePath))
        uniques.add(new ClusteredFiles(0.999, List.of(filePath)));
    }
    return Stream.concat(clusters.stream(), uniques.stream())
        .sorted(Comparator.comparing(ClusteredFiles::avgScore))
        .toList();
  }

  private List<ClusteredFiles> createClusters(Set<SimilarityScore> scored) {
    if (scored.isEmpty()) {
      return Collections.emptyList();
    }

    // Build union-find structure to group connected files
    Map<Path, Path> parent = new HashMap<>();
    Map<Path, Integer> rank = new HashMap<>();

    // Initialize: each file is its own parent
    for (SimilarityScore score : scored) {
      parent.putIfAbsent(score.p1(), score.p1());
      parent.putIfAbsent(score.p2(), score.p2());
      rank.putIfAbsent(score.p1(), 0);
      rank.putIfAbsent(score.p2(), 0);
    }

    // Union operation: merge sets for similar files
    for (SimilarityScore score : scored) {
      union(parent, rank, score.p1(), score.p2());
    }

    // Group files by their root parent (cluster representative)
    Map<Path, List<Path>> clusters = new HashMap<>();
    Map<Path, List<Double>> clusterScores = new HashMap<>();

    for (SimilarityScore score : scored) {
      Path root = find(parent, score.p1());

      clusters.computeIfAbsent(root, k -> new ArrayList<>()).add(score.p1());
      clusters.computeIfAbsent(root, k -> new ArrayList<>()).add(score.p2());

      clusterScores.computeIfAbsent(root, k -> new ArrayList<>()).add(score.score());
    }

    // Convert to ClusteredFiles with unique file paths and average scores
    List<ClusteredFiles> result = new ArrayList<>();
    for (Map.Entry<Path, List<Path>> entry : clusters.entrySet()) {
      List<Path> uniquePaths = entry.getValue().stream().distinct().toList();
      List<Double> scores = clusterScores.get(entry.getKey());
      double avgScore = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

      result.add(new ClusteredFiles(avgScore, uniquePaths));
    }

    return result.stream().sorted(Comparator.comparing(ClusteredFiles::avgScore)).toList();
  }

  private Path find(Map<Path, Path> parent, Path path) {
    if (!parent.get(path).equals(path)) {
      parent.put(path, find(parent, parent.get(path))); // Path compression
    }
    return parent.get(path);
  }

  private void union(Map<Path, Path> parent, Map<Path, Integer> rank, Path p1, Path p2) {
    Path root1 = find(parent, p1);
    Path root2 = find(parent, p2);

    if (root1.equals(root2)) {
      return; // Already in same set
    }

    // Union by rank: attach smaller tree under larger tree
    if (rank.get(root1) < rank.get(root2)) {
      parent.put(root1, root2);
    } else if (rank.get(root1) > rank.get(root2)) {
      parent.put(root2, root1);
    } else {
      parent.put(root2, root1);
      rank.put(root1, rank.get(root1) + 1);
    }
  }

  /** Renames files with 5-digit zero-padded group ID prefix */
  private Map<String, List<Path>> renameFilesWithGroupId(
      List<ClusteredFiles> clusters, boolean dryRun) {
    Map<String, List<Path>> result = new HashMap<>();
    for (int i = 0; i < clusters.size(); i++) {
      ClusteredFiles clusteredFiles = clusters.get(i);
      String groupIdStr = String.format("%05d", i);
      if (clusteredFiles.avgScore() < 0.9) {
        log.info(
            "Creating group {} with avgScore {} from {} files",
            groupIdStr,
            "%04.4f".formatted(clusteredFiles.avgScore()),
            clusteredFiles.filePaths().size());
      }
      List<Path> renamedPaths = new ArrayList<>();

      for (Path originalPath : clusteredFiles.filePaths()) {
        try {
          String originalFileName = originalPath.getFileName().toString();
          String newFileName = groupIdStr + "_" + originalFileName;
          if (GROUP_PREFIX_PRESENT.matcher(originalFileName).matches()) {
            newFileName = "%s_%s".formatted(groupIdStr, originalFileName.substring(6));
          }
          Path newPath = originalPath.getParent().resolve(newFileName.replace("__", "_"));

          // Handle collision - if file already exists with that name
          if (Files.exists(newPath) && !newPath.equals(originalPath)) {
            log.warn("File already exists: {}, skipping rename for {}", newPath, originalPath);
            renamedPaths.add(originalPath); // Keep original
            continue;
          }

          // Skip if already has correct prefix
          if (originalFileName.startsWith(groupIdStr + "_")) {
            log.debug("File already has correct prefix: {}", originalPath);
            renamedPaths.add(originalPath);
            continue;
          }
          if (!dryRun) {
            Files.move(originalPath, newPath, StandardCopyOption.ATOMIC_MOVE);
          }
          log.debug("Renamed: {} -> {}", originalPath.getFileName(), newFileName);
          renamedPaths.add(newPath);

        } catch (IOException e) {
          log.error("Error renaming file: {}", originalPath, e);
          renamedPaths.add(originalPath); // Keep original on error
        }
      }

      result.put(groupIdStr, renamedPaths);
    }

    return result;
  }

  record SimilarityScore(double score, Path p1, Hash h1, Path p2, Hash h2) {}

  record ClusteredFiles(double avgScore, List<Path> filePaths) {}

  private record ComputedPairs(Path p1, Path p2) {
    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      ComputedPairs that = (ComputedPairs) o;
      return Objects.equals(p1, that.p1) && Objects.equals(p2, that.p2)
          || Objects.equals(p1, that.p2) && Objects.equals(p2, that.p1);
    }
  }
}
