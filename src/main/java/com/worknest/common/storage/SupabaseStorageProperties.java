package com.worknest.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "storage.supabase")
public class SupabaseStorageProperties {

    private String url;
    private String serviceRoleKey;
    private Duration requestTimeout = Duration.ofSeconds(30);
    private Duration signedUrlTtl = Duration.ofMinutes(10);
    private Buckets buckets = new Buckets();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = trimToNull(url);
    }

    public String getServiceRoleKey() {
        return serviceRoleKey;
    }

    public void setServiceRoleKey(String serviceRoleKey) {
        this.serviceRoleKey = trimToNull(serviceRoleKey);
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getSignedUrlTtl() {
        return signedUrlTtl;
    }

    public void setSignedUrlTtl(Duration signedUrlTtl) {
        this.signedUrlTtl = signedUrlTtl;
    }

    public Buckets getBuckets() {
        return buckets;
    }

    public void setBuckets(Buckets buckets) {
        this.buckets = buckets == null ? new Buckets() : buckets;
    }

    public String bucketForPath(String relativePath) {
        String root = rootFolder(relativePath);
        return switch (root) {
            case "avatars" -> requireBucket("avatars", buckets.avatars);
            case "employee-documents", "offer-letters", "task-attachments", "announcements", "documents", "future" ->
                    requireBucket("documents", buckets.documents);
            case "candidate-cvs" -> requireBucket("recruitment", buckets.recruitment);
            case "chat-files" -> requireBucket("chat", buckets.chat);
            case "project-files" -> requireBucket("projects", buckets.projects);
            case "company-logos" -> requireBucket("logos", buckets.logos);
            case "temp" -> requireBucket("documents", buckets.documents);
            default -> throw new IllegalArgumentException("Unsupported Supabase storage folder: " + root);
        };
    }

    public String normalizedBaseUrl() {
        String value = trimToNull(url);
        if (value == null) {
            throw new IllegalStateException("SUPABASE_URL is required");
        }
        return value.replaceAll("/+$", "");
    }

    public String requiredServiceRoleKey() {
        String value = trimToNull(serviceRoleKey);
        if (value == null) {
            throw new IllegalStateException("SUPABASE_SERVICE_ROLE_KEY is required");
        }
        return value;
    }

    public Map<String, String> requiredBuckets() {
        return Map.of(
                "SUPABASE_BUCKET_AVATARS", requireBucket("avatars", buckets.avatars),
                "SUPABASE_BUCKET_DOCUMENTS", requireBucket("documents", buckets.documents),
                "SUPABASE_BUCKET_RECRUITMENT", requireBucket("recruitment", buckets.recruitment),
                "SUPABASE_BUCKET_CHAT", requireBucket("chat", buckets.chat),
                "SUPABASE_BUCKET_PROJECTS", requireBucket("projects", buckets.projects),
                "SUPABASE_BUCKET_LOGOS", requireBucket("logos", buckets.logos)
        );
    }

    private String rootFolder(String relativePath) {
        String normalized = trimToNull(relativePath);
        if (normalized == null) {
            throw new IllegalArgumentException("Storage path is required");
        }
        normalized = normalized.replace('\\', '/');
        int slash = normalized.indexOf('/');
        return (slash < 0 ? normalized : normalized.substring(0, slash)).toLowerCase(Locale.ROOT);
    }

    private String requireBucket(String label, String bucket) {
        String value = trimToNull(bucket);
        if (value == null) {
            throw new IllegalStateException("Supabase " + label + " bucket is required");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class Buckets {
        private String avatars;
        private String documents;
        private String recruitment;
        private String chat;
        private String projects;
        private String logos;

        public String getAvatars() {
            return avatars;
        }

        public void setAvatars(String avatars) {
            this.avatars = avatars;
        }

        public String getDocuments() {
            return documents;
        }

        public void setDocuments(String documents) {
            this.documents = documents;
        }

        public String getRecruitment() {
            return recruitment;
        }

        public void setRecruitment(String recruitment) {
            this.recruitment = recruitment;
        }

        public String getChat() {
            return chat;
        }

        public void setChat(String chat) {
            this.chat = chat;
        }

        public String getProjects() {
            return projects;
        }

        public void setProjects(String projects) {
            this.projects = projects;
        }

        public String getLogos() {
            return logos;
        }

        public void setLogos(String logos) {
            this.logos = logos;
        }
    }
}
