package org.gradle.wrapper;

import java.io.InputStream;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Tiny transparent bootstrap for environments where the Gradle wrapper JAR isn't bundled. */
public final class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(GradleWrapperMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path project = jar.getParent().getParent().getParent();
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(jar.getParent().resolve("gradle-wrapper.properties"))) {
            props.load(in);
        }
        String url = props.getProperty("distributionUrl");
        String sha = props.getProperty("distributionSha256Sum");
        if (!"https://services.gradle.org/distributions/gradle-8.7-bin.zip".equals(url)
                || !"544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d".equals(sha)) {
            throw new IllegalStateException("Unexpected Gradle distribution or checksum");
        }
        String home = System.getenv("GRADLE_USER_HOME");
        if (home == null || home.isBlank()) home = System.getProperty("user.home") + "/.gradle";
        Path cache = Path.of(home, "wrapper", "dists", "gradle-8.7-bin");
        Path bin = cache.resolve("gradle-8.7").resolve("bin")
                .resolve(System.getProperty("os.name").toLowerCase().contains("win") ? "gradle.bat" : "gradle");
        Files.createDirectories(cache);
        try (FileChannel channel = FileChannel.open(cache.resolve("install.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
            if (!Files.exists(bin)) {
                Path archive = Files.createTempFile(cache, "gradle-", ".zip");
                try {
                    var connection = new URL(url).openConnection();
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(120000);
                    try (InputStream in = connection.getInputStream()) {
                        Files.copy(in, archive, StandardCopyOption.REPLACE_EXISTING);
                    }
                    MessageDigest hasher = MessageDigest.getInstance("SHA-256");
                    try (DigestInputStream stream = new DigestInputStream(Files.newInputStream(archive), hasher)) {
                        byte[] buffer = new byte[65536];
                        while (stream.read(buffer) != -1) { /* stream digest */ }
                    }
                    byte[] digest = hasher.digest();
                    StringBuilder sum = new StringBuilder();
                    for (byte value : digest) sum.append(String.format("%02x", value & 0xff));
                    if (!sha.equals(sum.toString())) throw new IllegalStateException("Gradle download checksum mismatch");
                    Path unpack = Files.createTempDirectory(cache, "unpack-");
                    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                        ZipEntry entry;
                        while ((entry = zip.getNextEntry()) != null) {
                            Path target = unpack.resolve(entry.getName()).normalize();
                            if (!target.startsWith(unpack)) throw new IllegalStateException("Invalid ZIP path");
                            if (entry.isDirectory()) Files.createDirectories(target);
                            else {
                                Files.createDirectories(target.getParent());
                                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                            zip.closeEntry();
                        }
                    }
                    Path extracted = unpack.resolve("gradle-8.7");
                    if (!Files.isDirectory(extracted)) throw new IllegalStateException("Invalid Gradle archive");
                    Path old = cache.resolve("gradle-8.7");
                    if (Files.exists(old)) try (var paths = Files.walk(old)) {
                        paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                            try { Files.delete(path); }
                            catch (java.io.IOException e) { throw new RuntimeException(e); }
                        });
                    }
                    Files.move(extracted, cache.resolve("gradle-8.7"), StandardCopyOption.REPLACE_EXISTING);
                    bin.toFile().setExecutable(true);
                } finally {
                    Files.deleteIfExists(archive);
                }
            }
        }
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(bin.toAbsolutePath().toString());
        java.util.Collections.addAll(command, args);
        Process process = new ProcessBuilder(command).directory(project.toFile()).inheritIO().start();
        System.exit(process.waitFor());
    }
}
