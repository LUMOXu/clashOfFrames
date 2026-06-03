package com.lumoxu.cof.boot.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves flat UUID card files ({@code /cards/{uuid}.jpg}) and deck backs ({@code /cards/backs/{id}.jpg}).
 */
public class DeckCardResourceResolver extends PathResourceResolver {

    private static final Pattern LEGACY_CARD = Pattern.compile("^[^/]+/cards/(\\d+)([a-z])\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_DECK_BACK = Pattern.compile("^(\\d+)/back\\.(png|jpe?g)$", Pattern.CASE_INSENSITIVE);

    private final Path cardsRoot;

    public DeckCardResourceResolver(Path resourceRoot) {
        cardsRoot = resourceRoot.resolve("cards").normalize();
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
        Matcher numericBack = NUMERIC_DECK_BACK.matcher(path);
        if (numericBack.matches()) {
            String candidate = "backs/" + numericBack.group(1) + ".jpg";
            if (existsOnDisk(candidate)) {
                return candidate;
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
                if ("backs".equals(folder)) {
                    continue;
                }
                String candidate = folder + "/" + pmvId + "/" + shot + "." + ext;
                if (existsOnDisk(candidate)) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
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
