package com.blog;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

import com.blog.Models.*;

public final class Main {

    private static final int PORT = 3001;
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;    // 8MB per photo
    private static final long MAX_VIDEO_BYTES = 250L * 1024 * 1024;  // 250MB per video

    private static Store store;
    private static final SessionManager sessions = new SessionManager();
    private static Path webDir;
    private static Path uploadsDir;

    public static void main(String[] args) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        webDir = root.resolve("web");
        uploadsDir = root.resolve("uploads");
        Files.createDirectories(uploadsDir);
        store = new Store(root.resolve("data"));

        // Posts/vlogs written as plain .txt files -- see posts/README.txt
        // and vlogs/README.txt for the format. Re-read on every startup.
        Path postsDir = root.resolve("posts");
        Path vlogsDir = root.resolve("vlogs");
        Files.createDirectories(postsDir);
        Files.createDirectories(vlogsDir);
        store.loadFileContent(postsDir, vlogsDir);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/api/admin", Main::handleAdmin);
        server.createContext("/api/profile", Main::handleProfile);
        server.createContext("/api/posts", Main::handlePosts);
        server.createContext("/api/vlogs", Main::handleVlogs);

        server.createContext("/uploads/", exchange -> serveUpload(exchange));
        server.createContext("/", exchange -> serveStaticFile(exchange, webDir, "/"));

        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();
        System.out.println("Personal site running at http://localhost:" + PORT);
    }

    // ================= ADMIN (owner sign-in) =================

    private static void handleAdmin(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String sub = path.substring("/api/admin".length());
        String method = ex.getRequestMethod();

        if (sub.equals("/status") && method.equals("GET")) {
            boolean loggedIn = isOwner(ex);
            HttpUtil.sendJson(ex, 200, Json.obj(
                    "passwordSet", store.hasAdminPassword(),
                    "loggedIn", loggedIn));
            return;
        }
        if (sub.equals("/claim") && method.equals("POST")) {
            Map<String, Object> body = HttpUtil.readJsonBody(ex);
            String password = str(body, "password");
            if (password == null || password.length() < 6) {
                HttpUtil.sendError(ex, 400, "Password must be at least 6 characters");
                return;
            }
            if (!store.claimAdminPassword(password)) {
                HttpUtil.sendError(ex, 400, "A password has already been set for this site");
                return;
            }
            startSession(ex);
            HttpUtil.sendJson(ex, 200, Json.obj("ok", true));
            return;
        }
        if (sub.equals("/login") && method.equals("POST")) {
            Map<String, Object> body = HttpUtil.readJsonBody(ex);
            String password = str(body, "password");
            if (password == null || !store.verifyAdminPassword(password)) {
                HttpUtil.sendError(ex, 401, "Incorrect password");
                return;
            }
            startSession(ex);
            HttpUtil.sendJson(ex, 200, Json.obj("ok", true));
            return;
        }
        if (sub.equals("/logout") && method.equals("POST")) {
            Map<String, String> cookies = HttpUtil.parseCookies(ex);
            sessions.destroy(cookies.get(SessionManager.COOKIE_NAME));
            HttpUtil.clearCookie(ex, SessionManager.COOKIE_NAME);
            HttpUtil.sendJson(ex, 200, Json.obj("ok", true));
            return;
        }
        HttpUtil.sendError(ex, 404, "Not found");
    }

    private static void startSession(HttpExchange ex) {
        String id = sessions.create();
        HttpUtil.setCookie(ex, SessionManager.COOKIE_NAME, id, 60 * 60 * 24 * 30);
    }

    private static boolean isOwner(HttpExchange ex) {
         Map<String, String> cookies = HttpUtil.parseCookies(ex);
    return sessions.isValid(cookies.get(SessionManager.COOKIE_NAME));
    }

    private static boolean requireOwner(HttpExchange ex) throws IOException {
        if (!isOwner(ex)) {
            HttpUtil.sendError(ex, 401, "Sign in required");
            return false;
        }
        return true;
    }

    // ================= PROFILE =================

    private static void handleProfile(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (method.equals("GET")) {
            Profile p = store.getProfile();
            HttpUtil.sendJson(ex, 200, Json.obj(
                    "photoPath", p.photoPath, "name", p.name, "synopsis", p.synopsis,
                    "instagramMain", p.instagramMain, "instagramSpam", p.instagramSpam,
                    "linkedin", p.linkedin, "updatedAt", p.updatedAt));
            return;
        }
        if (method.equals("POST")) {
            if (!requireOwner(ex)) return;

            String contentType = ex.getRequestHeaders().getFirst("Content-Type");
            String boundary = MultipartParser.extractBoundary(contentType);
            if (boundary == null) { HttpUtil.sendError(ex, 400, "Expected multipart/form-data"); return; }

            byte[] raw = HttpUtil.readBodyWithLimit(ex, MAX_IMAGE_BYTES);
            if (raw == null) { HttpUtil.sendError(ex, 413, "Photo is too large (8MB max)"); return; }

            List<MultipartParser.Part> parts = MultipartParser.parse(raw, boundary);
            String name = null, synopsis = null, instagramMain = null, instagramSpam = null, linkedin = null;
            MultipartParser.Part photo = null;
            for (MultipartParser.Part p : parts) {
                switch (p.name) {
                    case "name" -> name = p.asText();
                    case "synopsis" -> synopsis = p.asText();
                    case "instagramMain" -> instagramMain = p.asText();
                    case "instagramSpam" -> instagramSpam = p.asText();
                    case "linkedin" -> linkedin = p.asText();
                    case "photo" -> { if (p.data.length > 0) photo = p; }
                    default -> {}
                }
            }

            String photoPath = null;
            if (photo != null) {
                if (photo.contentType == null || !photo.contentType.startsWith("image/")) {
                    HttpUtil.sendError(ex, 400, "Profile photo must be an image file");
                    return;
                }
                String filename = uniqueFilename(guessExtension(photo.filename, photo.contentType));
                Files.write(uploadsDir.resolve(filename), photo.data);
                photoPath = "/uploads/" + filename;
            }

            store.updateProfile(name, synopsis, instagramMain, instagramSpam, linkedin, photoPath);
            HttpUtil.sendJson(ex, 200, Json.obj("ok", true));
            return;
        }
        HttpUtil.sendError(ex, 405, "Method not allowed");
    }

    // ================= BLOG POSTS =================

    private static void handlePosts(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String sub = path.substring("/api/posts".length());
        String method = ex.getRequestMethod();

        if (sub.isEmpty() || sub.equals("/")) {
            if (method.equals("GET")) {
                List<Object> rows = new ArrayList<>();
                for (BlogPost p : store.listPosts()) rows.add(postSummaryJson(p));
                HttpUtil.sendJson(ex, 200, rows);
                return;
            }
            if (method.equals("POST")) {
                if (!requireOwner(ex)) return;
                createPost(ex);
                return;
            }
            HttpUtil.sendError(ex, 405, "Method not allowed");
            return;
        }

        long id;
        try {
            id = Long.parseLong(sub.substring(1));
        } catch (NumberFormatException e) {
            HttpUtil.sendError(ex, 404, "Not found");
            return;
        }

        if (method.equals("GET")) {
            BlogPost p = store.findPost(id);
            if (p == null) { HttpUtil.sendError(ex, 404, "Post not found"); return; }
            HttpUtil.sendJson(ex, 200, postDetailJson(p));
            return;
        }
        if (method.equals("DELETE")) {
            if (!requireOwner(ex)) return;
            boolean removed = store.deletePost(id);
            if (!removed) { HttpUtil.sendError(ex, 404, "Post not found"); return; }
            HttpUtil.sendJson(ex, 200, Json.obj("ok", true));
            return;
        }
        HttpUtil.sendError(ex, 405, "Method not allowed");
    }

    private static void createPost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        String boundary = MultipartParser.extractBoundary(contentType);
        if (boundary == null) { HttpUtil.sendError(ex, 400, "Expected multipart/form-data"); return; }

        byte[] raw = HttpUtil.readBodyWithLimit(ex, MAX_IMAGE_BYTES);
        if (raw == null) { HttpUtil.sendError(ex, 413, "Image is too large (8MB max)"); return; }

        List<MultipartParser.Part> parts = MultipartParser.parse(raw, boundary);
        String title = null, summary = null, content = null;
        MultipartParser.Part image = null;
        for (MultipartParser.Part p : parts) {
            switch (p.name) {
                case "title" -> title = p.asText();
                case "summary" -> summary = p.asText();
                case "content" -> content = p.asText();
                case "image" -> image = p;
                default -> {}
            }
        }

        if (image == null || image.data.length == 0) { HttpUtil.sendError(ex, 400, "A cover image is required"); return; }
        if (image.contentType == null || !image.contentType.startsWith("image/")) {
            HttpUtil.sendError(ex, 400, "Only image files are allowed for the cover image");
            return;
        }
        if (title == null || title.isBlank()) { HttpUtil.sendError(ex, 400, "A title is required"); return; }
        if (summary == null || summary.isBlank()) { HttpUtil.sendError(ex, 400, "A short summary is required"); return; }
        if (content == null || content.isBlank()) { HttpUtil.sendError(ex, 400, "The post needs some content"); return; }

        String filename = uniqueFilename(guessExtension(image.filename, image.contentType));
        Files.write(uploadsDir.resolve(filename), image.data);

        BlogPost p = store.createPost(title.trim(), summary.trim(), content.trim(), "/uploads/" + filename);
        HttpUtil.sendJson(ex, 200, Json.obj("id", p.id));
    }

    private static Map<String, Object> postSummaryJson(BlogPost p) {
        return Json.obj("id", p.id, "title", p.title, "summary", p.summary,
                "imagePath", p.imagePath, "createdAt", p.createdAt);
    }

    private static Map<String, Object> postDetailJson(BlogPost p) {
        return Json.obj("id", p.id, "title", p.title, "summary", p.summary,
                "content", p.content, "imagePath", p.imagePath, "createdAt", p.createdAt);
    }

    // ================= VLOG POSTS =================

    private static void handleVlogs(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String sub = path.substring("/api/vlogs".length());
        String method = ex.getRequestMethod();

        if (sub.isEmpty() || sub.equals("/")) {
            if (method.equals("GET")) {
                List<Object> rows = new ArrayList<>();
                for (VlogPost v : store.listVlogs()) rows.add(vlogJson(v));
                HttpUtil.sendJson(ex, 200, rows);
                return;
            }
            if (method.equals("POST")) {
                if (!requireOwner(ex)) return;
                createVlog(ex);
                return;
            }
            HttpUtil.sendError(ex, 405, "Method not allowed");
            return;
        }

        long id;
        try {
            id = Long.parseLong(sub.substring(1));
        } catch (NumberFormatException e) {
            HttpUtil.sendError(ex, 404, "Not found");
            return;
        }

        if (method.equals("GET")) {
            VlogPost v = store.findVlog(id);
            if (v == null) { HttpUtil.sendError(ex, 404, "Vlog not found"); return; }
            HttpUtil.sendJson(ex, 200, vlogJson(v));
            return;
        }
        if (method.equals("DELETE")) {
            if (!requireOwner(ex)) return;
            boolean removed = store.deleteVlog(id);
            if (!removed) { HttpUtil.sendError(ex, 404, "Vlog not found"); return; }
            HttpUtil.sendJson(ex, 200, Json.obj("ok", true));
            return;
        }
        HttpUtil.sendError(ex, 405, "Method not allowed");
    }

    private static void createVlog(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        String boundary = MultipartParser.extractBoundary(contentType);
        if (boundary == null) { HttpUtil.sendError(ex, 400, "Expected multipart/form-data"); return; }

        byte[] raw = HttpUtil.readBodyWithLimit(ex, MAX_VIDEO_BYTES);
        if (raw == null) { HttpUtil.sendError(ex, 413, "Video is too large (250MB max)"); return; }

        List<MultipartParser.Part> parts = MultipartParser.parse(raw, boundary);
        String title = null, summary = null;
        MultipartParser.Part video = null, thumbnail = null;
        for (MultipartParser.Part p : parts) {
            switch (p.name) {
                case "title" -> title = p.asText();
                case "summary" -> summary = p.asText();
                case "video" -> video = p;
                case "thumbnail" -> { if (p.data.length > 0) thumbnail = p; }
                default -> {}
            }
        }

        if (video == null || video.data.length == 0) { HttpUtil.sendError(ex, 400, "A video file is required"); return; }
        if (video.contentType == null || !video.contentType.startsWith("video/")) {
            HttpUtil.sendError(ex, 400, "Only video files are allowed");
            return;
        }
        if (title == null || title.isBlank()) { HttpUtil.sendError(ex, 400, "A title is required"); return; }
        if (summary == null || summary.isBlank()) { HttpUtil.sendError(ex, 400, "A short description is required"); return; }
        if (thumbnail != null && (thumbnail.contentType == null || !thumbnail.contentType.startsWith("image/"))) {
            HttpUtil.sendError(ex, 400, "The thumbnail must be an image file");
            return;
        }

        String videoFilename = uniqueFilename(guessExtension(video.filename, video.contentType));
        Files.write(uploadsDir.resolve(videoFilename), video.data);

        String thumbPath = null;
        if (thumbnail != null) {
            String thumbFilename = uniqueFilename(guessExtension(thumbnail.filename, thumbnail.contentType));
            Files.write(uploadsDir.resolve(thumbFilename), thumbnail.data);
            thumbPath = "/uploads/" + thumbFilename;
        }

        VlogPost v = store.createVlog(title.trim(), summary.trim(), "/uploads/" + videoFilename, thumbPath);
        HttpUtil.sendJson(ex, 200, Json.obj("id", v.id));
    }

    private static Map<String, Object> vlogJson(VlogPost v) {
        return Json.obj("id", v.id, "title", v.title, "summary", v.summary,
                "videoPath", v.videoPath, "thumbnailPath", v.thumbnailPath, "createdAt", v.createdAt);
    }

    // ================= Static files & uploads =================

    private static void serveUpload(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET") && !ex.getRequestMethod().equals("HEAD")) {
            HttpUtil.sendError(ex, 405, "Method not allowed");
            return;
        }
        String path = ex.getRequestURI().getPath();
        String relative = path.substring("/uploads/".length());
        Path target = uploadsDir.resolve(relative).normalize();
        if (!target.startsWith(uploadsDir) || !Files.exists(target) || Files.isDirectory(target)) {
            HttpUtil.sendError(ex, 404, "Not found");
            return;
        }
        HttpUtil.serveFileWithRangeSupport(ex, target);
    }

    private static void serveStaticFile(HttpExchange ex, Path baseDir, String prefix) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { HttpUtil.sendError(ex, 405, "Method not allowed"); return; }

        String path = ex.getRequestURI().getPath();
        String relative = path.startsWith(prefix) ? path.substring(prefix.length()) : path;
        if (relative.isBlank() || relative.equals("/")) relative = "index.html";

        Path target = baseDir.resolve(relative).normalize();
        if (!target.startsWith(baseDir) || !Files.exists(target) || Files.isDirectory(target)) {
            target = webDir.resolve("index.html");
            if (!Files.exists(target)) { HttpUtil.sendError(ex, 404, "Not found"); return; }
        }

        byte[] bytes = Files.readAllBytes(target);
        ex.getResponseHeaders().set("Content-Type", HttpUtil.contentTypeFor(target.toString()));
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ================= Helpers =================

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static String uniqueFilename(String ext) {
        return System.currentTimeMillis() + "-" + Math.abs(new Random().nextLong()) + ext;
    }

    private static String guessExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        return switch (contentType == null ? "" : contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            case "video/ogg" -> ".ogv";
            default -> contentType != null && contentType.startsWith("video/") ? ".mp4" : ".jpg";
        };
    }
}