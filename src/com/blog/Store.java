package com.blog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import com.blog.Models.*;

/**
 * Everything lives in memory for fast access and is written back out to
 * small JSON files under ./data on every change, so nothing is lost when
 * the server restarts.
 */
public final class Store {

    private final Path dataDir;
    private final Object lock = new Object();

    private Profile profile = new Profile();
    private String adminPasswordHash = null; // null until the owner sets a password
    private final List<BlogPost> posts = new ArrayList<>();
    private final List<VlogPost> vlogs = new ArrayList<>();

    // Posts/vlogs written as plain .txt files (see ContentLoader). These are
    // re-read from disk on every startup and are never written into the
    // data/*.json files -- the .txt files themselves are the source of truth.
    private final List<BlogPost> filePosts = new ArrayList<>();
    private final List<VlogPost> fileVlogs = new ArrayList<>();

    private final AtomicLong postIdSeq = new AtomicLong(1);
    private final AtomicLong vlogIdSeq = new AtomicLong(1);

    public Store(Path dataDir) throws IOException {
        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
        load();
    }

    // ---------------- Admin / owner password ----------------

    public boolean hasAdminPassword() {
        synchronized (lock) { return adminPasswordHash != null; }
    }

    /** Only succeeds the first time — after that, use verifyAdminPassword + changeAdminPassword. */
    public boolean claimAdminPassword(String password) {
        synchronized (lock) {
            if (adminPasswordHash != null) return false;
            adminPasswordHash = PasswordUtil.hash(password);
            saveSettings();
            return true;
        }
    }

    public boolean verifyAdminPassword(String password) {
        synchronized (lock) {
            return adminPasswordHash != null && PasswordUtil.verify(password, adminPasswordHash);
        }
    }

    // ---------------- Profile ----------------

    public Profile getProfile() {
        synchronized (lock) { return profile; }
    }

    public void updateProfile(String name, String synopsis, String instagramMain,
                               String instagramSpam, String linkedin, String photoPath) {
        synchronized (lock) {
            if (name != null) profile.name = name;
            if (synopsis != null) profile.synopsis = synopsis;
            if (instagramMain != null) profile.instagramMain = instagramMain;
            if (instagramSpam != null) profile.instagramSpam = instagramSpam;
            if (linkedin != null) profile.linkedin = linkedin;
            if (photoPath != null) profile.photoPath = photoPath;
            profile.updatedAt = Instant.now().toString();
            saveProfile();
        }
    }

    // ---------------- File-based posts (posts/*.txt, vlogs/*.txt) ----------------

    /** Re-scans postsDir/vlogsDir for .txt files and reloads them into memory. */
    public void loadFileContent(Path postsDir, Path vlogsDir) {
        List<BlogPost> loadedPosts = ContentLoader.loadPosts(postsDir);
        List<VlogPost> loadedVlogs = ContentLoader.loadVlogs(vlogsDir);
        synchronized (lock) {
            filePosts.clear();
            filePosts.addAll(loadedPosts);
            fileVlogs.clear();
            fileVlogs.addAll(loadedVlogs);
        }
    }

    // ---------------- Blog posts ----------------

    public BlogPost createPost(String title, String summary, String content, String imagePath) {
        synchronized (lock) {
            BlogPost p = new BlogPost();
            p.id = postIdSeq.getAndIncrement();
            p.title = title;
            p.summary = summary;
            p.content = content;
            p.imagePath = imagePath;
            p.createdAt = Instant.now().toString();
            posts.add(p);
            savePosts();
            return p;
        }
    }

    public List<BlogPost> listPosts() {
        synchronized (lock) {
            List<BlogPost> copy = new ArrayList<>(posts);
            copy.addAll(filePosts);
            copy.sort((a, b) -> b.createdAt.compareTo(a.createdAt));
            return copy;
        }
    }

    public BlogPost findPost(long id) {
        synchronized (lock) {
            for (BlogPost p : posts) if (p.id == id) return p;
            for (BlogPost p : filePosts) if (p.id == id) return p;
            return null;
        }
    }

    public boolean deletePost(long id) {
        synchronized (lock) {
            boolean removed = posts.removeIf(p -> p.id == id);
            if (removed) savePosts();
            return removed;
        }
    }

    // ---------------- Vlog posts ----------------

    public VlogPost createVlog(String title, String summary, String videoPath, String thumbnailPath) {
        synchronized (lock) {
            VlogPost v = new VlogPost();
            v.id = vlogIdSeq.getAndIncrement();
            v.title = title;
            v.summary = summary;
            v.videoPath = videoPath;
            v.thumbnailPath = thumbnailPath;
            v.createdAt = Instant.now().toString();
            vlogs.add(v);
            saveVlogs();
            return v;
        }
    }

    public List<VlogPost> listVlogs() {
        synchronized (lock) {
            List<VlogPost> copy = new ArrayList<>(vlogs);
            copy.addAll(fileVlogs);
            copy.sort((a, b) -> b.createdAt.compareTo(a.createdAt));
            return copy;
        }
    }

    public VlogPost findVlog(long id) {
        synchronized (lock) {
            for (VlogPost v : vlogs) if (v.id == id) return v;
            for (VlogPost v : fileVlogs) if (v.id == id) return v;
            return null;
        }
    }

    public boolean deleteVlog(long id) {
        synchronized (lock) {
            boolean removed = vlogs.removeIf(v -> v.id == id);
            if (removed) saveVlogs();
            return removed;
        }
    }

    // ---------------- Persistence ----------------

    private void saveSettings() {
        writeFile("settings.json", Json.write(Json.obj("adminPasswordHash", adminPasswordHash)));
    }

    private void saveProfile() {
        writeFile("profile.json", Json.write(Json.obj(
                "photoPath", profile.photoPath, "name", profile.name, "synopsis", profile.synopsis,
                "instagramMain", profile.instagramMain, "instagramSpam", profile.instagramSpam,
                "linkedin", profile.linkedin, "updatedAt", profile.updatedAt)));
    }

    private void savePosts() {
        List<Object> arr = new ArrayList<>();
        for (BlogPost p : posts) {
            arr.add(Json.obj("id", p.id, "title", p.title, "summary", p.summary,
                    "content", p.content, "imagePath", p.imagePath, "createdAt", p.createdAt));
        }
        writeFile("posts.json", Json.write(arr));
    }

    private void saveVlogs() {
        List<Object> arr = new ArrayList<>();
        for (VlogPost v : vlogs) {
            arr.add(Json.obj("id", v.id, "title", v.title, "summary", v.summary,
                    "videoPath", v.videoPath, "thumbnailPath", v.thumbnailPath, "createdAt", v.createdAt));
        }
        writeFile("vlogs.json", Json.write(arr));
    }

    private void writeFile(String name, String content) {
        try {
            Path tmp = dataDir.resolve(name + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, dataDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save " + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void load() throws IOException {
        Path settingsFile = dataDir.resolve("settings.json");
        if (Files.exists(settingsFile)) {
            Map<String, Object> m = Json.parseObject(Files.readString(settingsFile));
            adminPasswordHash = Json.str(m, "adminPasswordHash");
        }

        Path profileFile = dataDir.resolve("profile.json");
        if (Files.exists(profileFile)) {
            Map<String, Object> m = Json.parseObject(Files.readString(profileFile));
            profile.photoPath = Json.str(m, "photoPath");
            profile.name = orEmpty(Json.str(m, "name"));
            profile.synopsis = orEmpty(Json.str(m, "synopsis"));
            profile.instagramMain = orEmpty(Json.str(m, "instagramMain"));
            profile.instagramSpam = orEmpty(Json.str(m, "instagramSpam"));
            profile.linkedin = orEmpty(Json.str(m, "linkedin"));
            profile.updatedAt = orEmpty(Json.str(m, "updatedAt"));
        }

        Path postsFile = dataDir.resolve("posts.json");
        if (Files.exists(postsFile)) {
            List<Object> arr = (List<Object>) Json.parse(Files.readString(postsFile));
            for (Object o : arr) {
                Map<String, Object> m = (Map<String, Object>) o;
                BlogPost p = new BlogPost();
                p.id = Json.longVal(m.get("id"));
                p.title = Json.str(m, "title");
                p.summary = Json.str(m, "summary");
                p.content = Json.str(m, "content");
                p.imagePath = Json.str(m, "imagePath");
                p.createdAt = Json.str(m, "createdAt");
                posts.add(p);
                postIdSeq.updateAndGet(v -> Math.max(v, p.id + 1));
            }
        }

        Path vlogsFile = dataDir.resolve("vlogs.json");
        if (Files.exists(vlogsFile)) {
            List<Object> arr = (List<Object>) Json.parse(Files.readString(vlogsFile));
            for (Object o : arr) {
                Map<String, Object> m = (Map<String, Object>) o;
                VlogPost v = new VlogPost();
                v.id = Json.longVal(m.get("id"));
                v.title = Json.str(m, "title");
                v.summary = Json.str(m, "summary");
                v.videoPath = Json.str(m, "videoPath");
                v.thumbnailPath = Json.str(m, "thumbnailPath");
                v.createdAt = Json.str(m, "createdAt");
                vlogs.add(v);
                vlogIdSeq.updateAndGet(val -> Math.max(val, v.id + 1));
            }
        }
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }
}