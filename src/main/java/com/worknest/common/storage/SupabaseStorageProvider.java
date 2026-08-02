package com.worknest.common.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worknest.common.exception.BadRequestException;
import com.worknest.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SupabaseStorageProvider implements StorageProvider {
    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageProvider.class);

    private final SupabaseStorageProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SupabaseStorageProvider(SupabaseStorageProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .build();
    }

    @Override
    public void initialize(Collection<List<String>> categoryDirectories) {
        properties.normalizedBaseUrl();
        properties.requiredServiceRoleKey();
        properties.requiredBuckets();
        log.info("Supabase Storage provider initialized for {}", properties.normalizedBaseUrl());
    }

    @Override
    public void initializeTenant(String tenantSlug, Collection<List<String>> categoryDirectories) {
        normalizeTenantSlug(tenantSlug);
    }

    @Override
    public String bucketForPath(String relativePath) {
        return properties.bucketForPath(relativePath);
    }

    @Override
    public String objectKey(String tenantSlug, String relativePath) {
        String normalizedTenant = normalizeTenantSlug(tenantSlug);
        String normalizedPath = normalizeRelativePath(relativePath);
        int slash = normalizedPath.indexOf('/');
        if (slash < 0) {
            return normalizedPath + "/" + normalizedTenant;
        }
        return normalizedPath.substring(0, slash)
                + "/" + normalizedTenant
                + "/" + normalizedPath.substring(slash + 1);
    }

    @Override
    public void write(String tenantSlug, String relativePath, byte[] content) {
        if (content == null) {
            throw new BadRequestException("File content is required");
        }
        String bucket = bucketForPath(relativePath);
        String key = objectKey(tenantSlug, relativePath);
        HttpRequest request = baseRequest(objectUri(bucket, key))
                .header("Content-Type", "application/octet-stream")
                .header("x-upsert", "true")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        HttpResponse<byte[]> response = send(request);
        requireSuccess(response, "upload Supabase object", bucket, key);
        log.info("Stored Supabase object bucket={} key={} bytes={}", bucket, key, content.length);
    }

    @Override
    public Resource read(String tenantSlug, String relativePath) {
        String bucket = bucketForPath(relativePath);
        String key = objectKey(tenantSlug, relativePath);
        HttpRequest request = baseRequest(objectUri(bucket, key)).GET().build();
        HttpResponse<byte[]> response = send(request);
        if (response.statusCode() == 404) {
            throw new ResourceNotFoundException("File not found");
        }
        requireSuccess(response, "download Supabase object", bucket, key);
        byte[] body = response.body();
        return new ByteArrayResource(body == null ? new byte[0] : body);
    }

    @Override
    public boolean exists(String tenantSlug, String relativePath) {
        String bucket = bucketForPath(relativePath);
        String key = objectKey(tenantSlug, relativePath);
        HttpRequest request = baseRequest(objectUri(bucket, key))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<byte[]> response = send(request);
        if (response.statusCode() == 404) return false;
        requireSuccess(response, "inspect Supabase object", bucket, key);
        return true;
    }

    @Override
    public boolean hashMatches(String tenantSlug, String relativePath, String expectedSha256) {
        if (expectedSha256 == null || !expectedSha256.matches("(?i)^[0-9a-f]{64}$")) return false;
        try {
            byte[] bytes = read(tenantSlug, relativePath).getContentAsByteArray();
            byte[] expected = HexFormat.of().parseHex(expectedSha256);
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(bytes);
            return MessageDigest.isEqual(expected, actual);
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException | ResourceNotFoundException exception) {
            return false;
        }
    }

    @Override
    public void delete(String tenantSlug, String relativePath) {
        String bucket = bucketForPath(relativePath);
        String key = objectKey(tenantSlug, relativePath);
        byte[] body = jsonBytes(Map.of("prefixes", List.of(key)));
        HttpRequest request = baseRequest(storageUri("/object/" + encodeSegment(bucket)))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> response = send(request);
        requireSuccess(response, "delete Supabase object", bucket, key);
        log.info("Deleted Supabase object bucket={} key={}", bucket, key);
    }

    @Override
    public String getPublicUrl(String tenantSlug, String relativePath) {
        String bucket = bucketForPath(relativePath);
        String key = objectKey(tenantSlug, relativePath);
        return storageUri("/object/public/" + encodeSegment(bucket) + "/" + encodePath(key)).toString();
    }

    @Override
    public String getSignedUrl(String tenantSlug, String relativePath, Duration expiresIn) {
        String bucket = bucketForPath(relativePath);
        String key = objectKey(tenantSlug, relativePath);
        long seconds = Math.max(1, (expiresIn == null ? properties.getSignedUrlTtl() : expiresIn).toSeconds());
        HttpRequest request = baseRequest(storageUri("/object/sign/" + encodeSegment(bucket) + "/" + encodePath(key)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(Map.of("expiresIn", seconds))))
                .build();
        HttpResponse<byte[]> response = send(request);
        requireSuccess(response, "create Supabase signed URL", bucket, key);
        try {
            JsonNode json = objectMapper.readTree(response.body());
            String signedUrl = json.path("signedURL").asText(json.path("signedUrl").asText(null));
            if (signedUrl == null || signedUrl.isBlank()) {
                throw new BadRequestException("Supabase did not return a signed URL");
            }
            if (signedUrl.startsWith("http://") || signedUrl.startsWith("https://")) {
                return signedUrl;
            }
            return properties.normalizedBaseUrl() + "/storage/v1" + signedUrl;
        } catch (IOException exception) {
            throw new BadRequestException("Unable to parse Supabase signed URL response", exception);
        }
    }

    @Override
    public void move(String tenantSlug, String sourceRelativePath, String destinationRelativePath) {
        transfer("move", tenantSlug, sourceRelativePath, destinationRelativePath);
    }

    @Override
    public void copy(String tenantSlug, String sourceRelativePath, String destinationRelativePath) {
        transfer("copy", tenantSlug, sourceRelativePath, destinationRelativePath);
    }

    @Override
    public List<StoredObjectDescriptor> listObjects(String tenantSlug, String relativePrefix) {
        String bucket = bucketForPath(relativePrefix);
        String prefix = objectKey(tenantSlug, relativePrefix);
        List<StoredObjectDescriptor> objects = listSupabaseObjects(bucket, prefix);
        objects.sort(Comparator.comparing(StoredObjectDescriptor::relativePath));
        return List.copyOf(objects);
    }

    private List<StoredObjectDescriptor> listSupabaseObjects(String bucket, String prefix) {
        int slash = prefix.lastIndexOf('/');
        String listPrefix = slash < 0 ? "" : prefix.substring(0, slash);
        String search = slash < 0 ? prefix : prefix.substring(slash + 1);
        Map<String, Object> payload = Map.of(
                "prefix", listPrefix,
                "limit", 1000,
                "offset", 0,
                "sortBy", Map.of("column", "name", "order", "asc"),
                "search", search
        );
        HttpRequest request = baseRequest(storageUri("/object/list/" + encodeSegment(bucket)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(payload)))
                .build();
        HttpResponse<byte[]> response = send(request);
        requireSuccess(response, "list Supabase objects", bucket, prefix);
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(response.body(), new TypeReference<>() {});
            List<StoredObjectDescriptor> objects = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Object nameValue = row.get("name");
                if (!(nameValue instanceof String name) || name.isBlank()) continue;
                String objectKey = listPrefix.isBlank() ? name : listPrefix + "/" + name;
                if (!objectKey.startsWith(prefix)) continue;
                long size = row.get("metadata") instanceof Map<?, ?> metadata && metadata.get("size") instanceof Number number
                        ? number.longValue()
                        : 0L;
                Instant updatedAt = parseInstant(row.get("updated_at"));
                objects.add(new StoredObjectDescriptor(relativePathFromObjectKey(objectKey), size, updatedAt));
            }
            return objects;
        } catch (IOException exception) {
            throw new BadRequestException("Unable to parse Supabase object list", exception);
        }
    }

    private void transfer(String operation, String tenantSlug, String sourceRelativePath, String destinationRelativePath) {
        String sourceBucket = bucketForPath(sourceRelativePath);
        String destinationBucket = bucketForPath(destinationRelativePath);
        String sourceKey = objectKey(tenantSlug, sourceRelativePath);
        String destinationKey = objectKey(tenantSlug, destinationRelativePath);
        Map<String, Object> payload = Map.of(
                "bucketId", sourceBucket,
                "sourceKey", sourceKey,
                "destinationKey", destinationKey,
                "destinationBucket", destinationBucket
        );
        HttpRequest request = baseRequest(storageUri("/object/" + operation))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(payload)))
                .build();
        HttpResponse<byte[]> response = send(request);
        requireSuccess(response, operation + " Supabase object", sourceBucket, sourceKey);
    }

    private HttpRequest.Builder baseRequest(URI uri) {
        String key = properties.requiredServiceRoleKey();
        return HttpRequest.newBuilder(uri)
                .timeout(properties.getRequestTimeout())
                .header("Authorization", "Bearer " + key)
                .header("apikey", key);
    }

    private URI objectUri(String bucket, String key) {
        return storageUri("/object/" + encodeSegment(bucket) + "/" + encodePath(key));
    }

    private URI storageUri(String path) {
        return URI.create(properties.normalizedBaseUrl() + "/storage/v1" + path);
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException exception) {
            throw new BadRequestException("Supabase Storage request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Supabase Storage request was interrupted", exception);
        }
    }

    private void requireSuccess(HttpResponse<byte[]> response, String action, String bucket, String key) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return;
        if (status == 404) throw new ResourceNotFoundException("File not found");
        String body = response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8);
        log.warn("Unable to {} bucket={} key={} status={} body={}", action, bucket, key, status, body);
        throw new BadRequestException("Unable to " + action);
    }

    private byte[] jsonBytes(Object payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (IOException exception) {
            throw new BadRequestException("Unable to serialize Supabase request", exception);
        }
    }

    private Instant parseInstant(Object value) {
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Instant.parse(string);
            } catch (RuntimeException ignored) {
                return Instant.now();
            }
        }
        return Instant.now();
    }

    private String relativePathFromObjectKey(String key) {
        String normalized = normalizeRelativePath(key);
        int firstSlash = normalized.indexOf('/');
        if (firstSlash < 0) return normalized;
        int secondSlash = normalized.indexOf('/', firstSlash + 1);
        if (secondSlash < 0) return normalized.substring(0, firstSlash);
        return normalized.substring(0, firstSlash) + "/" + normalized.substring(secondSlash + 1);
    }

    private String encodePath(String path) {
        String[] segments = normalizeRelativePath(path).split("/");
        List<String> encoded = new ArrayList<>(segments.length);
        for (String segment : segments) {
            encoded.add(encodeSegment(segment));
        }
        return String.join("/", encoded);
    }

    private String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String normalizeTenantSlug(String tenantSlug) {
        String normalized = tenantSlug == null ? "" : tenantSlug.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9-]{0,79}")) {
            throw new BadRequestException("Invalid tenant storage key");
        }
        return normalized;
    }

    private String normalizeRelativePath(String relativePath) {
        String normalized = relativePath == null ? "" : relativePath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new BadRequestException("Storage path is required");
        }
        List<String> segments = new ArrayList<>();
        for (String rawSegment : normalized.split("/")) {
            String segment = rawSegment.trim();
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment) || segment.contains("..")) {
                throw new BadRequestException("Invalid storage path");
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }
}
