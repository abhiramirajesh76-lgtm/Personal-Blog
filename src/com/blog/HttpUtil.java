package com.blog;

import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class HttpUtil {

    private HttpUtil() {}

    public static byte[] readBody(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = exchange.getRequestBody()) {
            in.transferTo(buffer);
        }
        return buffer.toByteArray();
    }

    /** Reads the request body but aborts (returns null) if it exceeds the given byte limit. */
    public static byte[] readBodyWithLimit(HttpExchange exchange, long limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[16384];
        long total = 0;
        try (InputStream in = exchange.getRequestBody()) {
            int n;
            while ((n = in.read(chunk)) != -1) {
                total += n;
                if (total > limit) return null;
                buffer.write(chunk, 0, n);
            }
        }
        return buffer.toByteArray();
    }

    public static Map<String, String> parseCookies(HttpExchange exchange) {
        Map<String, String> cookies = new HashMap<>();
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) return cookies;
        for (String header : headers) {
            for (String pair : header.split(";")) {
                String[] kv = pair.trim().split("=", 2);
                if (kv.length == 2) cookies.put(kv[0], kv[1]);
            }
        }
        return cookies;
    }

    public static void setCookie(HttpExchange exchange, String name, String value, int maxAgeSeconds) {
        String cookie = name + "=" + value + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAgeSeconds;
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }

    public static void clearCookie(HttpExchange exchange, String name) {
        exchange.getResponseHeaders().add("Set-Cookie", name + "=; Path=/; HttpOnly; Max-Age=0");
    }

    public static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Json.obj("error", message));
    }

    public static Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
        byte[] raw = readBody(exchange);
        if (raw.length == 0) return new LinkedHashMap<>();
        return Json.parseObject(new String(raw, StandardCharsets.UTF_8));
    }

    public static String contentTypeFor(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".ogv")) return "video/ogg";
        return "application/octet-stream";
    }

    /**
     * Serves a file with HTTP byte-range support (RFC 7233), which browsers
     * need in order to scrub/seek through a <video> element instead of only
     * being able to play it start-to-finish.
     */
    public static void serveFileWithRangeSupport(HttpExchange exchange, Path file) throws IOException {
        long fileSize = Files.size(file);
        String contentType = contentTypeFor(file.toString());
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
        long start = 0, end = fileSize - 1;
        boolean isRangeRequest = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String spec = rangeHeader.substring("bytes=".length());
            String[] parts = spec.split("-", 2);
            try {
                if (!parts[0].isEmpty()) start = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) end = Long.parseLong(parts[1]);
                else end = fileSize - 1;
                isRangeRequest = true;
            } catch (NumberFormatException ignored) {
                start = 0; end = fileSize - 1;
            }
            if (start < 0 || end >= fileSize || start > end) {
                exchange.getResponseHeaders().set("Content-Range", "bytes */" + fileSize);
                exchange.sendResponseHeaders(416, -1);
                exchange.close();
                return;
            }
        }

        long length = end - start + 1;

        if (isRangeRequest) {
            exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            exchange.sendResponseHeaders(206, length);
        } else {
            exchange.sendResponseHeaders(200, length);
        }

        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
             OutputStream os = exchange.getResponseBody()) {
            raf.seek(start);
            byte[] buffer = new byte[32 * 1024];
            long remaining = length;
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = raf.read(buffer, 0, toRead);
                if (read < 0) break;
                os.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }
}
