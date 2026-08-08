package com.blog;

/** Plain data holders for the blog's content. */
public final class Models {

    private Models() {}

    /** Single record — there's only ever one profile (it's your personal site). */
    public static final class Profile {
        public String photoPath;      // nullable until first set
        public String name = "";
        public String synopsis = "";
        public String instagramMain = "";
        public String instagramSpam = "";
        public String linkedin = "";
        public String updatedAt = "";
    }

    public static final class BlogPost {
        public long id;
        public String title;
        public String summary;
        public String content;
        public String imagePath;
        public String createdAt;
    }

    public static final class VlogPost {
        public long id;
        public String title;
        public String summary;
        public String youtubeId;      // e.g. "dQw4w9WgXcQ" — the video is embedded from YouTube
        public String videoPath;      // legacy: a locally uploaded video file, if any
        public String thumbnailPath;
        public String createdAt;
    }
}