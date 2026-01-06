package de.bytewright.sticker_classifier.domain.img;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Inflater;

/**
 * Utility class to detect and convert Apple CgBI PNG images to standard PNGs. CgBI PNGs have BGR
 * color order instead of RGB, causing color channel issues.
 */
public class CgBIPNGConverter {

  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
  };

  private static final byte[] CGBI_CHUNK = {0x43, 0x67, 0x42, 0x49}; // "CgBI"

  /** Detects if PNG byte array is in Apple's CgBI format */
  public static boolean isCgBIPNG(byte[] imageData) throws IOException {
    return isCgBIPNG(new ByteArrayInputStream(imageData));
  }

  /** Detects if PNG input stream is in Apple's CgBI format */
  public static boolean isCgBIPNG(InputStream is) throws IOException {
    byte[] header = new byte[32];
    int bytesRead = is.read(header);

    if (bytesRead < 32) {
      return false;
    }

    // Check PNG signature
    for (int i = 0; i < PNG_SIGNATURE.length; i++) {
      if (header[i] != PNG_SIGNATURE[i]) {
        return false;
      }
    }

    // Look for CgBI chunk (usually right after PNG signature)
    for (int i = 8; i < header.length - 4; i++) {
      if (header[i] == CGBI_CHUNK[0]
          && header[i + 1] == CGBI_CHUNK[1]
          && header[i + 2] == CGBI_CHUNK[2]
          && header[i + 3] == CGBI_CHUNK[3]) {
        return true;
      }
    }

    return false;
  }

  /** Converts CgBI PNG byte array to standard PNG by chunk manipulation */
  public static byte[] convertCgBIToStandardPNG(byte[] imageData) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    // Write PNG signature
    output.write(PNG_SIGNATURE);

    ByteBuffer buffer = ByteBuffer.wrap(imageData);
    buffer.order(ByteOrder.BIG_ENDIAN);

    // Skip original signature
    buffer.position(8);

    byte[] idatData = null;
    int width = 0, height = 0;
    byte bitDepth = 0, colorType = 0;

    // Read and process chunks
    label:
    while (buffer.remaining() >= 12) {
      int length = buffer.getInt();
      byte[] type = new byte[4];
      buffer.get(type);
      String chunkType = new String(type);

      byte[] data = new byte[length];
      buffer.get(data);
      int crc = buffer.getInt();

      switch (chunkType) {
        case "CgBI":
          // Skip CgBI chunk entirely
          continue;
        case "IHDR":
          // Parse IHDR
          ByteBuffer ihdr = ByteBuffer.wrap(data);
          ihdr.order(ByteOrder.BIG_ENDIAN);
          width = ihdr.getInt();
          height = ihdr.getInt();
          bitDepth = ihdr.get();
          colorType = ihdr.get();

          // Write corrected IHDR
          writeChunk(output, "IHDR", data);
          break;
        case "IDAT":
          // Collect IDAT data for later processing
          if (idatData == null) {
            idatData = data;
          } else {
            // Concatenate multiple IDAT chunks
            byte[] combined = new byte[idatData.length + data.length];
            System.arraycopy(idatData, 0, combined, 0, idatData.length);
            System.arraycopy(data, 0, combined, idatData.length, data.length);
            idatData = combined;
          }
          break;
        case "IEND":
          // Process and write IDAT before IEND
          if (idatData != null) {
            byte[] correctedIDAT = swapBGRtoRGB(idatData, width, height, colorType, bitDepth);
            writeChunk(output, "IDAT", correctedIDAT);
          }
          // Write IEND
          writeChunk(output, "IEND", new byte[0]);
          break label;
        default:
          // Copy other chunks as-is
          writeChunk(output, chunkType, data);
          break;
      }
    }

    return output.toByteArray();
  }

  /** Swap BGR to RGB in decompressed image data */
  private static byte[] swapBGRtoRGB(
      byte[] compressedData, int width, int height, byte colorType, byte bitDepth)
      throws IOException {
    // CgBI PNGs use raw deflate without zlib wrapper
    // We need to use Inflater with nowrap=true
    Inflater inflater = new Inflater(true); // true = nowrap mode (raw deflate)
    inflater.setInput(compressedData);
    ByteArrayOutputStream decompressed = new ByteArrayOutputStream();

    byte[] buffer = new byte[1024];
    try {
      while (!inflater.finished()) {
        int count = inflater.inflate(buffer);
        if (count == 0) {
          // Check if we need more input or if we're done
          if (inflater.needsInput()) {
            break;
          }
        }
        decompressed.write(buffer, 0, count);
      }
    } catch (Exception e) {
      throw new IOException("Failed to decompress image data: " + e.getMessage(), e);
    } finally {
      inflater.end();
    }

    byte[] rawData = decompressed.toByteArray();

    // Unfilter the PNG data before processing
    rawData = unfilterPNG(rawData, width, height, colorType);

    // Swap BGR to RGB and handle premultiplied alpha (for RGBA/RGB images)
    if (colorType == 6 || colorType == 2) { // RGBA or RGB
      int bytesPerPixel = (colorType == 6) ? 4 : 3;
      int stride = width * bytesPerPixel;

      for (int y = 0; y < height; y++) {
        int rowStart = y * stride;
        for (int x = 0; x < width; x++) {
          int pixelStart = rowStart + x * bytesPerPixel;
          if (pixelStart + 2 < rawData.length) {
            // Swap R and B (BGR -> RGB)
            byte temp = rawData[pixelStart];
            rawData[pixelStart] = rawData[pixelStart + 2];
            rawData[pixelStart + 2] = temp;

            // If RGBA, unpremultiply alpha
            if (colorType == 6 && pixelStart + 3 < rawData.length) {
              int alpha = rawData[pixelStart + 3] & 0xFF;
              if (alpha > 0 && alpha < 255) {
                for (int c = 0; c < 3; c++) {
                  int value = rawData[pixelStart + c] & 0xFF;
                  int unpremultiplied = (value * 255) / alpha;
                  rawData[pixelStart + c] = (byte) Math.min(255, unpremultiplied);
                }
              }
            }
          }
        }
      }
    }

    // Re-add filter bytes (using filter type 0 = None for simplicity)
    rawData = addFilterBytes(rawData, width, height, colorType);

    // Recompress with zlib format (standard PNG)
    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
    java.util.zip.Deflater deflater = new java.util.zip.Deflater();
    deflater.setInput(rawData);
    deflater.finish();

    byte[] buf = new byte[1024];
    while (!deflater.finished()) {
      int count = deflater.deflate(buf);
      compressed.write(buf, 0, count);
    }
    deflater.end();

    return compressed.toByteArray();
  }

  /** Write a PNG chunk with proper CRC */
  private static void writeChunk(OutputStream out, String type, byte[] data) throws IOException {
    ByteBuffer lengthBuf = ByteBuffer.allocate(4);
    lengthBuf.order(ByteOrder.BIG_ENDIAN);
    lengthBuf.putInt(data.length);
    out.write(lengthBuf.array());

    byte[] typeBytes = type.getBytes(StandardCharsets.UTF_8);
    out.write(typeBytes);
    out.write(data);

    // Calculate CRC
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    ByteBuffer crcBuf = ByteBuffer.allocate(4);
    crcBuf.order(ByteOrder.BIG_ENDIAN);
    crcBuf.putInt((int) crc.getValue());
    out.write(crcBuf.array());
  }

  /** Unfilter PNG scan lines */
  private static byte[] unfilterPNG(byte[] data, int width, int height, byte colorType) {
    int bytesPerPixel = (colorType == 6) ? 4 : (colorType == 2) ? 3 : 1;
    int stride = width * bytesPerPixel + 1; // +1 for filter byte
    byte[] unfiltered = new byte[height * width * bytesPerPixel];

    for (int y = 0; y < height; y++) {
      int filterType = data[y * stride] & 0xFF;
      int rowStart = y * stride + 1;
      int unfilteredRowStart = y * width * bytesPerPixel;

      for (int x = 0; x < width * bytesPerPixel; x++) {
        int raw = data[rowStart + x] & 0xFF;
        int left =
            (x >= bytesPerPixel) ? (unfiltered[unfilteredRowStart + x - bytesPerPixel] & 0xFF) : 0;
        int up = (y > 0) ? (unfiltered[(y - 1) * width * bytesPerPixel + x] & 0xFF) : 0;
        int upLeft =
            (y > 0 && x >= bytesPerPixel)
                ? (unfiltered[(y - 1) * width * bytesPerPixel + x - bytesPerPixel] & 0xFF)
                : 0;

        int reconstructed;
        switch (filterType) {
          case 0: // None
            reconstructed = raw;
            break;
          case 1: // Sub
            reconstructed = (raw + left) & 0xFF;
            break;
          case 2: // Up
            reconstructed = (raw + up) & 0xFF;
            break;
          case 3: // Average
            reconstructed = (raw + ((left + up) / 2)) & 0xFF;
            break;
          case 4: // Paeth
            reconstructed = (raw + paethPredictor(left, up, upLeft)) & 0xFF;
            break;
          default:
            reconstructed = raw;
            break;
        }
        unfiltered[unfilteredRowStart + x] = (byte) reconstructed;
      }
    }

    return unfiltered;
  }

  /** Paeth predictor for PNG filtering */
  private static int paethPredictor(int a, int b, int c) {
    int p = a + b - c;
    int pa = Math.abs(p - a);
    int pb = Math.abs(p - b);
    int pc = Math.abs(p - c);

    if (pa <= pb && pa <= pc) {
      return a;
    } else if (pb <= pc) {
      return b;
    } else {
      return c;
    }
  }

  /** Add filter bytes back to unfiltered data (using filter type 0 = None) */
  private static byte[] addFilterBytes(byte[] unfiltered, int width, int height, byte colorType) {
    int bytesPerPixel = (colorType == 6) ? 4 : (colorType == 2) ? 3 : 1;
    int stride = width * bytesPerPixel + 1;
    byte[] filtered = new byte[height * stride];

    for (int y = 0; y < height; y++) {
      filtered[y * stride] = 0; // Filter type 0 (None)
      System.arraycopy(
          unfiltered, y * width * bytesPerPixel, filtered, y * stride + 1, width * bytesPerPixel);
    }

    return filtered;
  }
}
