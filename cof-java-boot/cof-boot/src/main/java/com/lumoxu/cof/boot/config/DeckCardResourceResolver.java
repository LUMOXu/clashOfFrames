package com.lumoxu.cof.boot.config;

import com.lumoxu.cof.domain.entity.CofDeck;
import com.lumoxu.cof.domain.mapper.CofDeckMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves legacy nested card URLs ({@code /cards/1/5/a.jpg}) and folder-based layouts.
 * Flat UUID card files ({@code /cards/{uuid}.jpg}) and deck backs ({@code /cards/backs/{id}.jpg})
 * are served directly from {@code cof-resource/cards/}.
 */
public class DeckCardResourceResolver extends PathResourceResolver {

    private static final Pattern LEGACY_CARD = Pattern.compile("^[^/]+/cards/(\\d+)([a-z])\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CANONICAL = Pattern.compile("^(\\d+|[^/]+)/(\\d+)/([a-z])\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_BACK = Pattern.compile("^[^/]+/back\\.png$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_DECK_BACK = Pattern.compile("^(\\d+)/back\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);

    private final Path cardsRoot;
    private final Map<Long, String> deckFolderById = new HashMap<>();

    public DeckCardResourceResolver(Path resourceRoot, CofDeckMapper deckMapper) {
        cardsRoot = resourceRoot.resolve("cards").normalize();
        List<CofDeck> decks = deckMapper.listEnabledDecks();
        if (decks != null) {
            for (CofDeck deck : decks) {
                if (deck.id != null && deck.folderName != null && !deck.folderName.isBlank()) {
                    deckFolderById.put(deck.id, deck.folderName);
                }
            }
        }
    }

    @Override
    protected Resource resolveResourceInternal(
            HttpServletRequest request,
            String requestPath,
            List<? extends Resource> locations,
            ResourceResolverChain chain) {
        Resource found = super.resolveResourceInternal(request, requestPath, locations, chain);
        if (found != null) {
            return found;
        }
        String alternate = remap(requestPath);
        if (alternate == null || alternate.equals(requestPath)) {
            return null;
        }
        return super.resolveResourceInternal(request, alternate, locations, chain);
    }

    private String remap(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return null;
        }
        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;

        Matcher legacyCard = LEGACY_CARD.matcher(path);
        if (legacyCard.matches()) {
            return findCardInAnyDeck(
                    Integer.parseInt(legacyCard.group(1)),
                    legacyCard.group(2).toLowerCase(Locale.ROOT),
                    normalizeExt(legacyCard.group(3)));
        }
        if (LEGACY_BACK.matcher(path).matches()) {
            return findBackInAnyDeck();
        }
        Matcher numericBack = NUMERIC_DECK_BACK.matcher(path);
        if (numericBack.matches()) {
            String candidate = "backs/" + numericBack.group(1) + ".jpg";
            if (existsOnDisk(candidate)) {
                return candidate;
            }
        }

        Matcher canonical = CANONICAL.matcher(path);
        if (canonical.matches()) {
            String deckSegment = canonical.group(1);
            int pmvId = Integer.parseInt(canonical.group(2));
            String shot = canonical.group(3).toLowerCase(Locale.ROOT);
            String ext = normalizeExt(canonical.group(4));
            try {
                long deckId = Long.parseLong(deckSegment);
                String folder = deckFolderById.get(deckId);
                if (folder != null) {
                    String candidate = folder + "/" + pmvId + "/" + shot + "." + ext;
                    if (existsOnDisk(candidate)) {
                        return candidate;
                    }
                }
            } catch (NumberFormatException ignored) {
                // already a folder name
            }
        }
        return null;
    }

    private String findCardInAnyDeck(int pmvId, String shot, String ext) {
        if (!Files.isDirectory(cardsRoot)) {
            return null;
        }
        try (var dirs = Files.list(cardsRoot)) {
            for (Path deckDir : dirs.filter(Files::isDirectory).toList()) {
                String folder = deckDir.getFileName().toString();
                String candidate = folder + "/" + pmvId + "/" + shot + "." + ext;
                if (existsOnDisk(candidate)) {
                    return candidate;
                }
                String altExt = "jpg".equals(ext) ? "png" : "jpg";
                String alternate = folder + "/" + pmvId + "/" + shot + "." + altExt;
                if (existsOnDisk(alternate)) {
                    return alternate;
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private String findBackInAnyDeck() {
        if (!Files.isDirectory(cardsRoot)) {
            return null;
        }
        try (var dirs = Files.list(cardsRoot)) {
            for (Path deckDir : dirs.filter(Files::isDirectory).toList()) {
                String candidate = deckDir.getFileName().toString() + "/back.png";
                if (existsOnDisk(candidate)) {
                    return candidate;
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private boolean existsOnDisk(String relativePath) {
        return Files.isRegularFile(cardsRoot.resolve(relativePath).normalize());
    }

    private static String normalizeExt(String ext) {
        String normalized = ext.toLowerCase(Locale.ROOT);
        return "jpeg".equals(normalized) ? "jpg" : normalized;
    }
}
