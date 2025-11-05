package com.composerai.api.service;

import com.composerai.api.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.IDN;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class CompanyLogoProvider {

    private static final Logger logger = LoggerFactory.getLogger(CompanyLogoProvider.class);
    private static final String GOOGLE_S2_TEMPLATE = "https://www.google.com/s2/favicons?domain=%s&sz=%d";
    private static final String FAVICON_KIT_TEMPLATE = "https://api.faviconkit.com/%s/%d";
    private static final int DEFAULT_SIZE = 128;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration TTL = Duration.ofHours(6);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(REQUEST_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private static final String GENERIC_PERSON_AVATAR = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0naHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmcnIHZpZXdCb3g9JzAgMCAxMjggMTI4Jz48ZGVmcz48bGluZWFyR3JhZGllbnQgaWQ9J2cnIHgxPScwJScgeTE9JzAlJyB4Mj0nMCUnIHkyPScxMDAlJz48c3RvcCBvZmZzZXQ9JzAlJyBzdG9wLWNvbG9yPScjMWUyOTNiJy8+PHN0b3Agb2Zmc2V0PScxMDAlJyBzdG9wLWNvbG9yPScjMGYxNzJhJy8+PC9saW5lYXJHcmFkaWVudD48L2RlZnM+PHJlY3Qgd2lkdGg9JzEyOCcgaGVpZ2h0PScxMjgnIHJ4PSczMicgZmlsbD0ndXJsKCNnKScvPjxjaXJjbGUgY3g9JzY0JyBjeT0nNDgnIHI9JzI0JyBmaWxsPSdyZ2JhKDI1NSwyNTUsMjU1LDAuODUpJy8+PHBhdGggZD0nTTMyIDEwOGMwLTE3LjY3MyAxNC4zMjctMzIgMzItMzJzMzIgMTQuMzI3IDMyIDMyJyBmaWxsPSdyZ2JhKDI1NSwyNTUsMjU1LDAuNzUpJy8+PC9zdmc+";

    private final ConcurrentMap<String, CachedLogo> inMemoryCache = new ConcurrentHashMap<>();

    // TODO replace with shared cache provider when available.
    public Optional<String> logoUrlForDomain(String domain) {
        String normalized = normalizeDomain(domain);
        if (normalized == null) {
            return Optional.empty();
        }

        CachedLogo cached = inMemoryCache.get(normalized);
        if (cached != null && !cached.isExpired()) {
            return cached.url();
        }

        Optional<String> resolved = fetchValidatedLogo(normalized)
            .or(() -> parentDomain(normalized).flatMap(this::fetchValidatedLogo));
        inMemoryCache.put(normalized, new CachedLogo(resolved.orElse(null), Instant.now()));
        resolved.ifPresentOrElse(
            url -> logger.debug("Accepted company logo from Google S2 for domain={} size={}px", normalized, DEFAULT_SIZE),
            () -> logger.debug("Rejected company logo for domain={} due to validation failure", normalized)
        );
        return resolved;
    }

    private Optional<String> parentDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        int firstDot = domain.indexOf('.');
        if (firstDot <= 0 || firstDot == domain.length() - 1) {
            return Optional.empty();
        }
        String candidate = domain.substring(firstDot + 1);
        if (candidate.contains(".")) {
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public String fallbackAvatarUrl() {
        return GENERIC_PERSON_AVATAR;
    }

    private String normalizeDomain(String domain) {
        if (StringUtils.isBlank(domain)) {
            return null;
        }

        String trimmed = domain.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed = trimmed.substring(trimmed.indexOf("//") + 2);
        }
        int slashIndex = trimmed.indexOf('/');
        if (slashIndex >= 0) {
            trimmed = trimmed.substring(0, slashIndex);
        }
        if (trimmed.isBlank()) {
            return null;
        }

        try {
            return IDN.toASCII(trimmed);
        } catch (Exception e) {
            logger.debug("Failed to normalize domain: {}", domain, e);
            return null;
        }
    }

    private Optional<String> fetchValidatedLogo(String normalizedDomain) {
        return fetchFromProvider(normalizedDomain, GOOGLE_S2_TEMPLATE, "Google S2")
            .or(() -> fetchFromProvider(normalizedDomain, FAVICON_KIT_TEMPLATE, "FaviconKit"));
    }

    private Optional<String> fetchFromProvider(String normalizedDomain, String template, String providerName) {
        String url = String.format(Locale.ROOT, template, normalizedDomain, DEFAULT_SIZE);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                logger.debug("{} logo request failed for domain={} status={}", providerName, normalizedDomain, response.statusCode());
                return Optional.empty();
            }

            byte[] body = response.body();
            if (body == null || body.length == 0) {
                logger.debug("{} logo response empty for domain={}", providerName, normalizedDomain);
                return Optional.empty();
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.startsWith("image/")) {
                logger.debug("{} logo content-type invalid for domain={} type={}", providerName, normalizedDomain, contentType);
                return Optional.empty();
            }

            if (!hasMinimumDimensions(body)) {
                logger.debug("{} logo too small for domain={} (required>={})", providerName, normalizedDomain, DEFAULT_SIZE);
                return Optional.empty();
            }

            return Optional.of(url);
        } catch (IOException e) {
            logger.debug("{} logo request IO failure for domain={}", providerName, normalizedDomain, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("{} logo request interrupted for domain={}", providerName, normalizedDomain, e);
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            logger.debug("{} logo request invalid URI for domain={} url={}", providerName, normalizedDomain, url, e);
            return Optional.empty();
        }
    }

    private boolean hasMinimumDimensions(byte[] imageBytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return false;
            }
            return image.getWidth() >= DEFAULT_SIZE && image.getHeight() >= DEFAULT_SIZE;
        } catch (IOException e) {
            logger.debug("Failed to inspect company logo dimensions", e);
            return false;
        }
    }

    private record CachedLogo(String urlValue, Instant cachedAt) {
        boolean isExpired() {
            return cachedAt.plus(TTL).isBefore(Instant.now());
        }

        Optional<String> url() {
            return Optional.ofNullable(urlValue);
        }
    }
}
