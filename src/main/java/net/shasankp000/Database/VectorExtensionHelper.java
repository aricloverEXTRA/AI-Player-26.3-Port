package net.shasankp000.Database;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.Function;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public class VectorExtensionHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorExtensionHelper.class);

    // === SQLITE-VEC ===
    private static final String WINDOWS_VEC_URL = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.6/sqlite-vec-0.1.6-loadable-windows-x86_64.tar.gz";
    private static final String LINUX_VEC_URL = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.6/sqlite-vec-0.1.6-loadable-linux-x86_64.tar.gz";
    private static final String MACOS_VEC_URL = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.6/sqlite-vec-0.1.6-loadable-macos-x86_64.tar.gz";

    private static final String VECTOR_FILENAME_WINDOWS = "vec0.dll";
    private static final String VECTOR_FILENAME_LINUX = "vec0.so";
    private static final String VECTOR_FILENAME_MACOS = "vec0.dylib";

    // === SQLITE-VSS ===
    private static final String VSS_LINUX_URL = "https://github.com/asg017/sqlite-vss/releases/download/v0.1.2/sqlite-vss-v0.1.2-loadable-linux-x86_64.tar.gz";
    private static final String VSS_MACOS_URL = "https://github.com/asg017/sqlite-vss/releases/download/v0.1.2/sqlite-vss-v0.1.2-loadable-macos-x86_64.tar.gz";

    private static final String VSS_FILENAME_LINUX = "vss0.so";
    private static final String VSS_FILENAME_MACOS = "vss0.dylib";

    // === HELPER METHODS ===

    /**
     * Clean up old incorrectly-named extension files from buggy versions.
     */
    private static void cleanupOldVecFiles(Path dir, String correctName) {
        try {
            // List of old incorrect names that might exist
            String[] oldNames = {"vector0.dll", "vector0.so", "vector0.dylib"};
            for (String oldName : oldNames) {
                if (!oldName.equals(correctName)) {
                    Path oldFile = dir.resolve(oldName);
                    if (Files.exists(oldFile)) {
                        Files.delete(oldFile);
                        LOGGER.info("🧹 Cleaned up old incorrectly-named file: {}", oldFile);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("⚠️ Failed to clean up old files: {}", e.getMessage());
        }
    }

    // === SQLITE-VEC ===

    public static Path ensureSqliteVecPresent() throws IOException {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String downloadUrl;
        String targetFileName;

        if (osName.contains("win")) {
            downloadUrl = WINDOWS_VEC_URL;
            targetFileName = VECTOR_FILENAME_WINDOWS;
        } else if (osName.contains("nux") || osName.contains("nix")) {
            downloadUrl = LINUX_VEC_URL;
            targetFileName = VECTOR_FILENAME_LINUX;
        } else if (osName.contains("mac")) {
            downloadUrl = MACOS_VEC_URL;
            targetFileName = VECTOR_FILENAME_MACOS;
        } else {
            throw new UnsupportedOperationException("Unsupported OS for SQLite-Vec");
        }

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path vecDir = configDir.resolve("sqlite_vector/sqlite-vec");
        if (!Files.exists(vecDir)) {
            Files.createDirectories(vecDir);
        }

        Path outputPath = vecDir.resolve(targetFileName);

        // Clean up old incorrectly-named files (from previous buggy versions)
        cleanupOldVecFiles(vecDir, targetFileName);

        if (Files.exists(outputPath)) {
            LOGGER.info("✅ sqlite-vec already present at: {}", outputPath);
            return outputPath;
        }

        LOGGER.info("⬇️ Downloading sqlite-vec from {}", downloadUrl);
        Path gzPath = vecDir.resolve("sqlite-vec.tar.gz");
        Path tarPath = vecDir.resolve("sqlite-vec.tar");

        try (InputStream in = new URL(downloadUrl).openStream()) {
            Files.copy(in, gzPath, StandardCopyOption.REPLACE_EXISTING);
        }

        try (GZIPInputStream gzipIn = new GZIPInputStream(Files.newInputStream(gzPath));
             OutputStream out = Files.newOutputStream(tarPath)) {
            gzipIn.transferTo(out);
        }

        try (InputStream tarIn = Files.newInputStream(tarPath)) {
            boolean found = safeExtractTar(tarIn, targetFileName, outputPath);
            if (!found) throw new IOException("❌ sqlite-vec extraction failed!");
        }

        LOGGER.info("✅ sqlite-vec ready at: {}", outputPath);
        return outputPath;
    }

    // === SQLITE-VSS ===

    public static Path ensureSqliteVssPresent() throws IOException {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String downloadUrl;
        String targetFileName;

        if (osName.contains("nux") || osName.contains("nix")) {
            downloadUrl = VSS_LINUX_URL;
            targetFileName = VSS_FILENAME_LINUX;
        } else if (osName.contains("mac")) {
            downloadUrl = VSS_MACOS_URL;
            targetFileName = VSS_FILENAME_MACOS;
        } else {
            throw new UnsupportedOperationException("sqlite-vss is not supported on this OS");
        }

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path vssDir = configDir.resolve("sqlite_vector/sqlite-vss");
        if (!Files.exists(vssDir)) {
            Files.createDirectories(vssDir);
        }

        Path outputPath = vssDir.resolve(targetFileName);

        // Clean up old files and any vector0.* files that shouldn't be here
        try {
            String[] unwantedFiles = {"vector0.so", "vector0.dylib"};
            for (String unwanted : unwantedFiles) {
                Path unwantedFile = vssDir.resolve(unwanted);
                if (Files.exists(unwantedFile)) {
                    Files.delete(unwantedFile);
                    LOGGER.info("🧹 Cleaned up unwanted file from vss directory: {}", unwantedFile);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("⚠️ Failed to clean up unwanted files: {}", e.getMessage());
        }

        if (Files.exists(outputPath)) {
            LOGGER.info("✅ sqlite-vss already present at: {}", outputPath);
            return outputPath;
        }

        LOGGER.info("⬇️ Downloading sqlite-vss from {}", downloadUrl);
        Path gzPath = vssDir.resolve("sqlite-vss.tar.gz");
        Path tarPath = vssDir.resolve("sqlite-vss.tar");

        try (InputStream in = new URL(downloadUrl).openStream()) {
            Files.copy(in, gzPath, StandardCopyOption.REPLACE_EXISTING);
        }

        try (GZIPInputStream gzipIn = new GZIPInputStream(Files.newInputStream(gzPath));
             OutputStream out = Files.newOutputStream(tarPath)) {
            gzipIn.transferTo(out);
        }

        try (InputStream tarIn = Files.newInputStream(tarPath)) {
            boolean found = safeExtractTar(tarIn, targetFileName, outputPath);
            if (!found) throw new IOException("❌ sqlite-vss extraction failed!");
        }

        LOGGER.info("✅ sqlite-vss ready at: {}", outputPath);
        return outputPath;
    }

    /**
     * Shared TAR extractor - extracts only the specified file from a TAR archive.
     */
    private static boolean safeExtractTar(InputStream tarInputStream, String targetFileName, Path outputPath) throws IOException {
        byte[] header = new byte[512];
        boolean found = false;

        while (true) {
            int read = tarInputStream.read(header);
            if (read < 512) break;

            String name = new String(header, 0, 100).trim();
            if (name.isEmpty()) break;

            long size = Long.parseLong(new String(header, 124, 12).trim(), 8);

            LOGGER.info("🔍 TAR entry: {} ({} bytes)", name, size);

            // Only extract the exact file we're looking for (handle both bare names and paths)
            boolean isMatch = name.equals(targetFileName) || name.endsWith("/" + targetFileName);

            if (isMatch) {
                LOGGER.info("✅ Found target file '{}' in archive, extracting to: {}", targetFileName, outputPath);
                try (OutputStream out = Files.newOutputStream(outputPath)) {
                    byte[] buffer = new byte[4096];
                    long remaining = size;
                    while (remaining > 0) {
                        int len = tarInputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (len == -1) break;
                        out.write(buffer, 0, len);
                        remaining -= len;
                    }
                }
                LOGGER.info("✅ Successfully extracted: {}", outputPath);
                found = true;
                // Continue to skip remaining entries
            }

            // Skip this entry's data (including padding to 512-byte boundary)
            long skip = size + (512 - (size % 512)) % 512;
            if (!isMatch) {
                while (skip > 0) {
                    long skipped = tarInputStream.skip(skip);
                    if (skipped <= 0) break;
                    skip -= skipped;
                }
            }
        }

        return found;
    }

    public static void loadSqliteVecExtension(Connection conn, Path vecPath) throws SQLException, IOException {
        // Remove extension from path as SQLite will add it automatically
        String path = vecPath.toAbsolutePath().toString();
        // Strip extension (.dll, .so, .dylib) from the end
        path = path.replaceAll("\\.(dll|so|dylib)$", "");
        path = path.replace("\\", "\\\\");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT load_extension('" + path + "', 'sqlite3_vec_init');");
            LOGGER.info("✅ Loaded sqlite-vec extension");
            ResultSet rs = stmt.executeQuery("SELECT vec_version();");
            if (rs.next()) LOGGER.info("✅ sqlite-vec version: {}", rs.getString(1));
        }
    }

    public static void loadSqliteVssExtension(Connection conn, Path vssPath) throws SQLException, IOException {
        // Remove extension from path as SQLite will add it automatically
        String path = vssPath.toAbsolutePath().toString();
        // Strip extension (.dll, .so, .dylib) from the end
        path = path.replaceAll("\\.(dll|so|dylib)$", "");
        path = path.replace("\\", "\\\\");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT load_extension('" + path + "', 'sqlite3_vss_init');");
            LOGGER.info("✅ Loaded sqlite-vss extension");
            ResultSet rs = stmt.executeQuery("SELECT vss_version();");
            if (rs.next()) LOGGER.info("✅ sqlite-vss version: {}", rs.getString(1));
        }
    }


    public static void registerCosineDistanceIfNeeded(Connection conn) throws SQLException {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (!osName.contains("win")) {
            return; // Only needed for Windows fallback
        }

        try {
            Function.create(conn, "cosine_distance", new Function() {
                @Override
                protected void xFunc() throws SQLException {
                    if (args() != 2) {
                        throw new SQLException("cosine_distance() requires exactly 2 arguments");
                    }

                    String v1Str = value_text(0);
                    String v2Str = value_text(1);

                    double[] v1 = parseVectorLiteral(v1Str);
                    double[] v2 = parseVectorLiteral(v2Str);

                    if (v1.length != v2.length) {
                        throw new SQLException("Vector dimensions do not match");
                    }

                    double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
                    for (int i = 0; i < v1.length; i++) {
                        dot += v1[i] * v2[i];
                        norm1 += v1[i] * v1[i];
                        norm2 += v2[i] * v2[i];
                    }

                    double sim = dot / (Math.sqrt(norm1) * Math.sqrt(norm2) + 1e-10);
                    result(1.0 - sim);
                }

                private double[] parseVectorLiteral(String literal) {
                    // Remove brackets and split
                    String cleaned = literal.replaceAll("[\\[\\]]", "");
                    String[] parts = cleaned.split(",");
                    double[] vec = new double[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        vec[i] = Double.parseDouble(parts[i].trim());
                    }
                    return vec;
                }
            });

            LOGGER.info("✅ Registered fallback cosine_distance for Windows (TEXT VECTOR)");
        } catch (SQLException e) {
            LOGGER.error("❌ Failed to register cosine_distance UDF: {}", e.getMessage(), e);
            throw e;
        }
    }



    private static double[] deserializeVector(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int len = bytes.length / Double.BYTES;
        double[] vec = new double[len];
        for (int i = 0; i < len; i++) vec[i] = buffer.getDouble();
        return vec;
    }
}
