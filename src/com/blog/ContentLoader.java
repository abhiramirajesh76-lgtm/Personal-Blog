package com.blog;

import com.blog.Models.BlogPost;
import com.blog.Models.VlogPost;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads blog/vlog posts written as plain ".txt" files, so you can publish
 * new content by dropping a file into posts/ or vlogs/ and restarting the
 * server -- no sign-in, no web form, no JSON to hand-edit.
 *
 * File format (blank line separates the header from the body):
 *
 *   Title: My First Post
 *   Summary: A short one-line summary
 *   Image: sunset.jpg
 *
 *   First paragraph of the post.
 *
 *   Second paragraph.
 *
 * Vlog files use a Youtube link instead of Image / body:
 *
 *   Title: Trip to the mountains
 *   Description: A short description
 *   Youtube: https://www.youtube.com/watch?v=dQw4w9WgXcQ
 *   Thumbnail: mountains-thumb.jpg
 *
 * "Youtube:" accepts a full watch/share/embed/shorts URL or a bare video
 * ID. "Thumbnail:" is optional -- without one, YouTube's own thumbnail
 * for the video is used automatically. Image/Thumbnail filenames (when
 * given) must already exist in the uploads/ folder (copy the file there
 * yourself). An optional "Date: YYYY-MM-DD" header controls ordering;
 * without it, the file's name (if it starts with YYYY-MM-DD) or
 * last-modified time is used.
 */
public final class ContentLoader {

    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ISO_LOCAL_DATE;

    private ContentLoader() {}

    public static List<BlogPost> loadPosts(Path folder) {
        List<BlogPost> result = new ArrayList<>();
        List<Path> files = listTextFiles(folder);
        long idBase = 1_000_000_000L;
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            try {
                Map<String, String> header = new LinkedHashMap<>();
                String body = readHeaderAndBody(file, header);

                BlogPost p = new BlogPost();
                p.id = idBase + i;
                p.title = header.getOrDefault("title", file.getFileName().toString());
                p.summary = header.getOrDefault("summary", "");
                p.content = body;
                p.imagePath = normalizeAssetPath(header.get("image"));
                p.createdAt = resolveCreatedAt(header.get("date"), file);
                result.add(p);
            } catch (IOException e) {
                System.err.println("Skipping " + file + ": " + e.getMessage());
            }
        }
        return result;
    }

    public static List<VlogPost> loadVlogs(Path folder) {
        List<VlogPost> result = new ArrayList<>();
        List<Path> files = listTextFiles(folder);
        long idBase = 2_000_000_000L;
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            try {
                Map<String, String> header = new LinkedHashMap<>();
                readHeaderAndBody(file, header); // vlogs don't use a body

                String youtubeField = header.get("youtube");
                String youtubeId = youtubeField == null ? null : extractYoutubeId(youtubeField);
                if (youtubeId == null) {
                    System.err.println("Skipping " + file + ": missing or unrecognized 'Youtube:' link");
                    continue;
                }

                VlogPost v = new VlogPost();
                v.id = idBase + i;
                v.title = header.getOrDefault("title", file.getFileName().toString());
                v.summary = header.getOrDefault("description", header.getOrDefault("summary", ""));
                v.youtubeId = youtubeId;
                String thumbnail = header.get("thumbnail");
                v.thumbnailPath = thumbnail != null && !thumbnail.isBlank()
                        ? normalizeAssetPath(thumbnail)
                        : "https://img.youtube.com/vi/" + youtubeId + "/hqdefault.jpg";
                v.createdAt = resolveCreatedAt(header.get("date"), file);
                result.add(v);
            } catch (IOException e) {
                System.err.println("Skipping " + file + ": " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * Pulls the 11-character video ID out of any common YouTube URL shape
     * (watch?v=, youtu.be/, embed/, shorts/), or accepts a bare ID as-is.
     */
    private static String extractYoutubeId(String input) {
        String value = input.trim();
        if (value.matches("[A-Za-z0-9_-]{11}")) return value; // already a bare ID

        try {
            java.net.URI uri = new java.net.URI(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();

            if (host.contains("youtu.be")) {
                String id = firstPathSegment(path);
                if (id != null) return id;
            }
            if (host.contains("youtube.com")) {
                if (path.startsWith("/embed/") || path.startsWith("/shorts/")) {
                    String id = path.substring(path.indexOf('/', 1) + 1);
                    return id.isBlank() ? null : id;
                }
                String query = uri.getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] kv = param.split("=", 2);
                        if (kv.length == 2 && kv[0].equals("v")) return kv[1];
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static String firstPathSegment(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        int slash = trimmed.indexOf('/');
        String segment = slash < 0 ? trimmed : trimmed.substring(0, slash);
        return segment.isBlank() ? null : segment;
    }

    private static List<Path> listTextFiles(Path folder) {
        try {
            if (!Files.isDirectory(folder)) return List.of();
            try (Stream<Path> stream = Files.list(folder)) {
                return stream
                        .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Reads "Key: value" lines up to the first blank line into {@code header}; returns the rest, trimmed, as the body. */
    private static String readHeaderAndBody(Path file, Map<String, String> header) throws IOException {
        List<String> lines = Files.readAllLines(file);
        int i = 0;
        for (; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) { i++; break; }
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            header.put(key, value);
        }
        StringBuilder body = new StringBuilder();
        for (; i < lines.size(); i++) {
            body.append(lines.get(i)).append('\n');
        }
        return body.toString().trim();
    }

    private static String normalizeAssetPath(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.startsWith("/") || value.startsWith("http://") || value.startsWith("https://")) return value;
        return "/uploads/" + value;
    }

    private static String resolveCreatedAt(String dateField, Path file) {
        if (dateField != null && !dateField.isBlank()) {
            try {
                LocalDate d = LocalDate.parse(dateField.trim(), DATE_ONLY);
                return d.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
            } catch (Exception ignored) {
                // fall through to other strategies
            }
        }
        String name = file.getFileName().toString();
        if (name.length() >= 10) {
            try {
                LocalDate d = LocalDate.parse(name.substring(0, 10), DATE_ONLY);
                return d.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
            } catch (Exception ignored) {
                // fall through to last-modified time
            }
        }
        try {
            return Files.getLastModifiedTime(file).toInstant().toString();
        } catch (IOException e) {
            return Instant.now().toString();
        }
    }
}