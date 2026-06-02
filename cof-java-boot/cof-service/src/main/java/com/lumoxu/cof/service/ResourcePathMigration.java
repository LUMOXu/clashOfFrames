package com.lumoxu.cof.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class ResourcePathMigration {

    private static final Pattern CARD_FILE = Pattern.compile("^(\\d+)([a-z])\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOT_FILE = Pattern.compile("^([a-z])\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);

    private final Path resourceRoot;

    public ResourcePathMigration(@Value("${cof.resource-root:../cof-resource}") String resourceRoot) {
        this.resourceRoot = Path.of(resourceRoot).toAbsolutePath().normalize();
    }

    public Path cardsRoot() {
        return resourceRoot.resolve("cards");
    }

    public Path deckBackPath(long deckId) {
        return cardsRoot().resolve("backs").resolve(deckId + ".jpg");
    }

    public static String backUrl(long deckId) {
        return "/cards/backs/" + deckId + ".jpg";
    }

    public static String cardImageUrl(String imageId, String ext) {
        return "/cards/" + imageId + "." + normalizeExt(ext);
    }

    public String storeCardImage(byte[] bytes, String ext) throws IOException {
        String imageId = UUID.randomUUID().toString();
        String normalizedExt = normalizeExt(ext);
        Path target = cardsRoot().resolve(imageId + "." + normalizedExt);
        Files.createDirectories(cardsRoot());
        Files.write(target, bytes);
        return cardImageUrl(imageId, normalizedExt);
    }

    public StoredCardImage storeCardFile(Path sourceFile) throws IOException {
        String fileName = sourceFile.getFileName().toString();
        String ext = extensionOf(fileName);
        String imageId = UUID.randomUUID().toString();
        Path target = cardsRoot().resolve(imageId + "." + ext);
        Files.createDirectories(cardsRoot());
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredCardImage(imageId, ext, fileName, cardImageUrl(imageId, ext));
    }

    /**
     * Imports card images from legacy deck folders into the flat {@code cards/} directory.
     */
    public List<LegacyImportedCard> importLegacyDeckImages(Path libraryDir, long deckId) throws IOException {
        List<LegacyImportedCard> imported = new ArrayList<>();
        Path legacyCardsDir = libraryDir.resolve("cards");
        if (Files.isDirectory(legacyCardsDir)) {
            try (Stream<Path> files = Files.list(legacyCardsDir)) {
                for (Path cardPath : files.filter(Files::isRegularFile).toList()) {
                    LegacyImportedCard row = importLegacyCardPath(cardPath, deckId);
                    if (row != null) {
                        imported.add(row);
                    }
                }
            }
        }
        try (Stream<Path> entries = Files.list(libraryDir)) {
            for (Path entry : entries.filter(Files::isDirectory).toList()) {
                String name = entry.getFileName().toString();
                if ("cards".equals(name)) {
                    continue;
                }
                try {
                    int matchId = Integer.parseInt(name);
                    try (Stream<Path> cardFiles = Files.list(entry)) {
                        for (Path cardFile : cardFiles.filter(Files::isRegularFile).toList()) {
                            LegacyImportedCard row = importLegacyNestedCard(cardFile, deckId, matchId);
                            if (row != null) {
                                imported.add(row);
                            }
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // skip non-pmv folders
                }
            }
        }
        Path numericDeckDir = cardsRoot().resolve(String.valueOf(deckId));
        if (Files.isDirectory(numericDeckDir)) {
            imported.addAll(importFlatDeckSubdirs(numericDeckDir, deckId));
        }
        return imported;
    }

    public void writeDeckBack(byte[] jpeg, long deckId) throws IOException {
        Path backPath = deckBackPath(deckId);
        Files.createDirectories(backPath.getParent());
        Files.write(backPath, jpeg);
    }

    /** Best-effort delete of a flat card image referenced by {@code /cards/{uuid}.jpg}. */
    public void deleteCardImageIfPresent(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        String path = imageUrl.startsWith("/") ? imageUrl.substring(1) : imageUrl;
        if (!path.startsWith("cards/")) {
            return;
        }
        Path file = resourceRoot.resolve(path).normalize();
        if (!file.startsWith(cardsRoot())) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // ignore
        }
    }

    public void copyDeckBack(Path sourceBack, long deckId) throws IOException {
        if (!Files.exists(sourceBack)) {
            return;
        }
        Path backPath = deckBackPath(deckId);
        Files.createDirectories(backPath.getParent());
        Files.copy(sourceBack, backPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private List<LegacyImportedCard> importFlatDeckSubdirs(Path deckDir, long deckId) throws IOException {
        List<LegacyImportedCard> imported = new ArrayList<>();
        try (Stream<Path> entries = Files.list(deckDir)) {
            for (Path entry : entries.filter(Files::isDirectory).toList()) {
                int matchId;
                try {
                    matchId = Integer.parseInt(entry.getFileName().toString());
                } catch (NumberFormatException ex) {
                    continue;
                }
                try (Stream<Path> cardFiles = Files.list(entry)) {
                    for (Path cardFile : cardFiles.filter(Files::isRegularFile).toList()) {
                        LegacyImportedCard row = importLegacyNestedCard(cardFile, deckId, matchId);
                        if (row != null) {
                            imported.add(row);
                        }
                    }
                }
            }
        }
        Path legacyCardsDir = deckDir.resolve("cards");
        if (Files.isDirectory(legacyCardsDir)) {
            try (Stream<Path> files = Files.list(legacyCardsDir)) {
                for (Path cardPath : files.filter(Files::isRegularFile).toList()) {
                    LegacyImportedCard row = importLegacyCardPath(cardPath, deckId);
                    if (row != null) {
                        imported.add(row);
                    }
                }
            }
        }
        return imported;
    }

    private LegacyImportedCard importLegacyCardPath(Path cardPath, long deckId) throws IOException {
        String fileName = cardPath.getFileName().toString();
        Matcher matcher = CARD_FILE.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        int matchId = Integer.parseInt(matcher.group(1));
        String shot = matcher.group(2).toLowerCase(Locale.ROOT);
        StoredCardImage stored = storeCardFile(cardPath);
        return new LegacyImportedCard(deckId, matchId, shot, stored.fileName(), stored.imageUrl());
    }

    private LegacyImportedCard importLegacyNestedCard(Path cardPath, long deckId, int matchId) throws IOException {
        String fileName = cardPath.getFileName().toString();
        Matcher shotMatcher = SHOT_FILE.matcher(fileName);
        Matcher legacyMatcher = CARD_FILE.matcher(fileName);
        String shot;
        if (shotMatcher.matches()) {
            shot = shotMatcher.group(1).toLowerCase(Locale.ROOT);
        } else if (legacyMatcher.matches()) {
            matchId = Integer.parseInt(legacyMatcher.group(1));
            shot = legacyMatcher.group(2).toLowerCase(Locale.ROOT);
        } else {
            return null;
        }
        StoredCardImage stored = storeCardFile(cardPath);
        return new LegacyImportedCard(deckId, matchId, shot, stored.fileName(), stored.imageUrl());
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "jpg";
        }
        return normalizeExt(fileName.substring(dot + 1));
    }

    private static String normalizeExt(String ext) {
        String normalized = ext.toLowerCase(Locale.ROOT);
        return "jpeg".equals(normalized) ? "jpg" : normalized;
    }

    public record StoredCardImage(String imageId, String ext, String fileName, String imageUrl) {
    }

    public record LegacyImportedCard(long deckId, int matchId, String shot, String fileName, String imageUrl) {
    }
}
