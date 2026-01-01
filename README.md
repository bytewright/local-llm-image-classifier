# Sticker Classifier & Deduplicator

A Spring Boot application for automated classification and deduplication of image collections using local LLM inference via Ollama.

## Overview

This application processes directories containing large collections of image files (e.g., chat stickers), automatically classifying them into user-defined categories using AI-powered image analysis and removing duplicates based on file hash comparison.

## Goals

- **Automated Classification**: Leverage local LLM capabilities to categorize images based on visual content
- **Deduplication**: Identify and remove duplicate files using hash-based comparison
- **User-Defined Categories**: Support flexible classification schemes defined via configuration
- **Progress Monitoring**: Provide real-time feedback on processing status through a simple web interface
- **Zero Persistence**: In-memory operation without database requirements

## Architecture

The application follows a **hexagonal (ports and adapters) architecture** to ensure clean separation of concerns and testability:

```
├── Domain Layer (Core)
│   ├── Classification logic
│   ├── Deduplication logic
│   └── Business rules
│
├── Application Layer
│   ├── Use cases / Services
│   └── Orchestration
│
└── Adapters
    ├── Input Adapters
    │   ├── REST Controllers (Web UI)
    │   └── Configuration readers
    │
    └── Output Adapters
        ├── Ollama LLM integration
        ├── File system operations
        └── Hash calculation
```

## Key Features

### 1. Classification
- Load images from a specified working directory
- Send images to Ollama with classification prompts
- Categorize images based on LLM analysis
- Support for multiple user-defined classification categories

### 2. Deduplication
- Calculate file hashes (MD5/SHA-256) for each image
- Identify duplicate files
- Keep one representative file per unique hash
- Report deduplicated files

### 3. Web Interface
- Server-side rendered pages (Thymeleaf)
- Real-time progress indicators
- Display of:
    - Total files processed
    - Current processing queue
    - Classification results
    - Links to images by category
- Simple, functional design

## Configuration

The application is configured via a YAML file:

```yaml
workDirectory: /path/to/sticker/collection
classifications:
  - name: category_a
    description: "Prose description of visual characteristics for this category"
  - name: category_b
    description: "Prose description of visual characteristics for this category"
  - name: others
    description: "Fallback category for unmatched content"
```

### Configuration Parameters

- **workDirectory**: Path to the directory containing images to process
- **classifications**: List of category definitions
    - **name**: Category identifier
    - **description**: Natural language description provided to the LLM for classification

## Technical Approach

### Processing Pipeline

1. **Discovery Phase**
    - Scan work directory for image files (PNG format)
    - Calculate file hashes
    - Identify duplicates

2. **Classification Phase**
    - For each unique image:
        - Load image data
        - Construct prompt with classification descriptions
        - Query Ollama LLM with image and prompt
        - Parse classification response
        - Store result in memory

3. **Output Phase**
    - Group files by classification
    - Provide web interface for browsing results
    - Generate classification reports

### LLM Integration

The application integrates with **Ollama**, a local LLM inference engine:

- Supports vision-capable models (e.g., LLaVA, Bakllava)
- Sends image and classification prompt
- Parses structured response
- Handles timeouts and retries

### In-Memory Storage

All processing state is maintained in memory:

- File metadata (path, hash, size)
- Classification results
- Processing queue status
- No database or persistent storage required

## Technology Stack

- **Framework**: Spring Boot 3.x
- **View Layer**: Thymeleaf (server-side rendering)
- **LLM Integration**: Ollama REST API
- **Configuration**: Spring Boot YAML configuration
- **Architecture**: Hexagonal/Clean Architecture

## Running the Application

1. Ensure Ollama is running locally with a vision-capable model
2. Configure `application.yml` with work directory and classifications
3. Start the Spring Boot application
4. Access web interface at `http://localhost:8080`
5. Monitor progress and browse classified images

## Future Enhancements

- Batch processing optimizations
- Support for additional image formats
- Export classification results
- Manual reclassification interface
- Confidence scoring for classifications
- Parallel processing support

## License

[Specify license here]