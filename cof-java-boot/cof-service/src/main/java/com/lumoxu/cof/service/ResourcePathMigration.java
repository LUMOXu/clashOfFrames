package com.lumoxu.cof.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class ResourcePathMigration {

    private static final Pattern CARD_FILE = Pattern.compile("^(\\d+)([a-z])\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);

    public List<MigratedCard> migrateLegacyCards(Path sourceDeckDir, Path targetDeckDir, long deckId) throws IOException {
        Path legacyCardsDir = sourceDeckDir.resolve("cards");
        if (!Files.isDirectory(legacyCardsDir)) {
            return List.of();
        }
        Files.createDirectories(targetDeckDir);
        List<MigratedCard> migrated = new ArrayList<>();
        try (Stream<Path> files = Files.list(legacyCardsDir)) {
            for (Path cardPath : files.filter(Files::isRegularFile).toList()) {
                String fileName = cardPath.getFileName().toString();
                Matcher matcher = CARD_FILE.matcher(fileName);
                if (!matcher.matches()) {
                    continue;
                }
                int pmvId = Integer.parseInt(matcher.group(1));
                String shot = matcher.group(2).toLowerCase(Locale.ROOT);
                String ext = normalizeExt(matcher.group(3));
                Path targetDir = targetDeckDir.resolve(String.valueOf(pmvId));
                Files.createDirectories(targetDir);
                Path target = targetDir.resolve(shot + "." + ext);
                copyOrMove(cardPath, target);
                String imageUrl = cardUrl(deckId, pmvId, shot, ext);
                migrated.add(new MigratedCard(pmvId, shot, fileName, imageUrl));
            }
        }
        return migrated;
    }

    public void syncCanonicalDeckDir(Path sourceDeckDir, Path targetDeckDir, long deckId) throws IOException {
        Files.createDirectories(targetDeckDir);
        Path back = sourceDeckDir.resolve("back.png");
        if (Files.exists(back)) {
            Files.copy(back, targetDeckDir.resolve("back.png"), StandardCopyOption.REPLACE_EXISTING);
        }
        try (Stream<Path> entries = Files.list(sourceDeckDir)) {
            for (Path entry : entries.filter(Files::isDirectory).toList()) {
                String name = entry.getFileName().toString();
                if ("cards".equals(name)) {
                    continue;
                }
                try {
                    int pmvId = Integer.parseInt(name);
                    Path targetPmvDir = targetDeckDir.resolve(String.valueOf(pmvId));
                    Files.createDirectories(targetPmvDir);
                    try (Stream<Path> cardFiles = Files.list(entry)) {
                        for (Path cardFile : cardFiles.filter(Files::isRegularFile).toList()) {
                            Path target = targetPmvDir.resolve(cardFile.getFileName());
                            copyOrMove(cardFile, target);
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // skip non-pmv folders
                }
            }
        }
    }

    public static String cardUrl(long deckId, int pmvId, String shot, String ext) {
        return "/cards/" + deckId + "/" + pmvId + "/" + shot + "." + normalizeExt(ext);
    }

    public static String backUrl(long deckId) {
        return "/cards/" + deckId + "/back.png";
    }

    private static void copyOrMove(Path source, Path target) throws IOException {
        if (!Files.exists(target)) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String normalizeExt(String ext) {
        String normalized = ext.toLowerCase(Locale.ROOT);
        return "jpeg".equals(normalized) ? "jpg" : normalized;
    }

    public record MigratedCard(int pmvId, String cardId, String fileName, String imageUrl) {
    }
}
