package com.lumoxu.cof.service;

import com.lumoxu.cof.common.api.CofException;
import com.lumoxu.cof.common.api.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(FontSubsetService.class);

    private final Path resourceRoot;
    private final UserStatsService userStatsService;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public FontSubsetService(
            @Value("${cof.resource-root:../cof-resource}") String resourceRoot,
            UserStatsService userStatsService) {
        this.resourceRoot = Path.of(resourceRoot).toAbsolutePath().normalize();
        this.userStatsService = userStatsService;
        log.debug("Font subset service initialized: resourceRoot={}", this.resourceRoot);
    }

    public Path godSubset() {
        log.debug("GOD font subset requested");
        return subsetFor("GOD");
    }

    public Path playerSubset(String statsId) {
        log.debug("GOD Slayer font subset requested: statsId={}", statsId);
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
            log.debug("Font subset cache hit: key={}, path={}", key, output);
            return output;
        }
        ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
        lock.lock();
        try {
            if (Files.isRegularFile(output)) {
                log.debug("Font subset cache hit after lock: key={}, path={}", key, output);
                return output;
            }
            Files.createDirectories(output.getParent());
            Path temporary = output.resolveSibling(key + "." + ProcessHandle.current().pid() + ".tmp.woff2");
            try {
                log.info("Generating font subset: key={}, codePoints={}, source={}",
                        key, normalized.codePointCount(0, normalized.length()), source);
                runFontTools(source, temporary, normalized);
                if (!Files.isRegularFile(temporary) || Files.size(temporary) == 0) {
                    throw new IOException("FontTools did not create a font file");
                }
                try {
                    Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("Font subset generated: key={}, path={}, bytes={}", key, output, Files.size(output));
            } finally {
                Files.deleteIfExists(temporary);
            }
            return output;
        } catch (IOException ex) {
            log.error("Font subset generation failed: key={}, source={}", key, source, ex);
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
            log.debug("Using legacy Source Han Serif font: {}", legacy);
            return legacy;
        }
        Path bundled = resourceRoot.resolve("assets/fonts/SourceHanSerifSC-VF.otf.woff2");
        if (Files.isRegularFile(bundled)) {
            log.debug("Using bundled Source Han Serif font: {}", bundled);
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
            python = Files.isExecutable(Path.of("/usr/bin/python3")) ? "python3" : "python";
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
        log.debug("FontTools process started: python={}, source={}, output={}, codePoints={}",
                python, source, output, text.codePointCount(0, text.length()));
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
