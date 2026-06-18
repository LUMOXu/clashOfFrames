package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class FontSubsetService {

    private static final int MAX_CHARS = 64;

    private final Path resourceRoot;
    private final UserStatsService userStatsService;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public FontSubsetService(
            @Value("${cof.resource-root:../cof-resource}") String resourceRoot,
            UserStatsService userStatsService) {
        this.resourceRoot = Path.of(resourceRoot).toAbsolutePath().normalize();
        this.userStatsService = userStatsService;
    }

    public Path godSubset() {
        return subsetFor("GOD");
    }

    public Path playerSubset(String statsId) {
        return subsetFor(userStatsService.godSlayerUsername(statsId));
    }

    public Path subsetFor(String text) {
        String normalized = normalizeText(text);
        if (normalized.isEmpty()) {
            throw new CofException(ErrorCode.BAD_REQUEST, "字体子集文本不能为空。");
        }
        Path source = sourceFont();
        String key = cacheKey(source, normalized);
        Path output = resourceRoot.resolve("assets/fonts/name-subsets").resolve(key + ".woff2");
        if (Files.isRegularFile(output)) {
            return output;
        }
        ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
        lock.lock();
        try {
            if (Files.isRegularFile(output)) {
                return output;
            }
            Files.createDirectories(output.getParent());
            Path temporary = output.resolveSibling(key + "." + ProcessHandle.current().pid() + ".tmp.woff2");
            try {
                runFontTools(source, temporary, normalized);
                if (!Files.isRegularFile(temporary) || Files.size(temporary) == 0) {
                    throw new IOException("FontTools did not create a font file");
                }
                try {
                    Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return output;
        } catch (IOException ex) {
            throw new CofException(ErrorCode.INTERNAL, "思源宋体子集生成失败：" + ex.getMessage());
        } finally {
            lock.unlock();
            locks.remove(key, lock);
        }
    }

    static String normalizeText(String value) {
        LinkedHashSet<Integer> codePoints = new LinkedHashSet<>();
        Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC)
                .codePoints()
                .limit(MAX_CHARS)
                .forEach(codePoints::add);
        StringBuilder result = new StringBuilder();
        codePoints.stream().limit(MAX_CHARS).forEach(result::appendCodePoint);
        return result.toString();
    }

    private Path sourceFont() {
        Path legacy = resourceRoot.getParent().resolve("old/SourceHanSerifSC-VF.otf.woff2").normalize();
        if (Files.isRegularFile(legacy)) {
            return legacy;
        }
        Path bundled = resourceRoot.resolve("assets/fonts/SourceHanSerifSC-VF.otf.woff2");
        if (Files.isRegularFile(bundled)) {
            return bundled;
        }
        throw new CofException(ErrorCode.NOT_FOUND, "未找到思源宋体源文件。");
    }

    private static String cacheKey(Path source, String text) {
        try {
            String input = source + "|" + Files.size(source) + "|" + Files.getLastModifiedTime(source).toMillis() + "|" + text;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算字体缓存键", ex);
        }
    }

    private static void runFontTools(Path source, Path output, String text) throws IOException {
        String python = System.getenv("PYTHON");
        if (python == null || python.isBlank()) {
            python = "python";
        }
        Process process = new ProcessBuilder(
                python,
                "-m", "fontTools.subset",
                source.toString(),
                "--text=" + text,
                "--flavor=woff2",
                "--layout-features=*",
                "--output-file=" + output)
                .redirectErrorStream(true)
                .start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("FontTools exit " + exit + ": " + log.strip());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("字体子集生成被中断", ex);
        }
    }
}
