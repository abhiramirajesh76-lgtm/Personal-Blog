# Personal Site — About / Blog / Vlog

A personal website with three sections:

- **About** — your photo, a short bio, and links to your social accounts
  (Instagram, a second/spam Instagram, LinkedIn)
- **Blog** — a grid of posts (cover image, title, short summary); click a
  thumbnail to read the full post
- **Vlog** — a YouTube-style grid of videos (thumbnail, title, description);
  click one to watch it in a full player

There's no sign-in and no admin panel. Everything — your photo, posts, and
videos — is published by editing plain files directly: drop an image or
video into `uploads/`, describe a post in a `.txt` file in `posts/` or
`vlogs/`, and restart the server. The site itself is read-only for every
visitor, including you.

## What it's built with

Plain Java, same philosophy as the birdwatch project: **zero external
dependencies**. Just the JDK's built-in HTTP server
(`com.sun.net.httpserver`) and a small hand-written JSON layer. Nothing to
download with Maven/Gradle — open the folder and run it.

Two pieces of engineering worth knowing about:

- **HTTP byte-range support** for video files (see
  `HttpUtil.serveFileWithRangeSupport`). Without it, browsers can only play
  a video start-to-finish; with it, you can drag the scrubber to jump
  around — this is what makes the vlog player behave like a normal video
  site instead of a dumb file download.
- **`ContentLoader`**, which scans `posts/` and `vlogs/` for `.txt` files
  on every startup and turns them into blog/vlog entries — this is what
  lets you publish by editing a text file instead of using a web form.

## Running it in VS Code

**Option A — with the Java extension (recommended):**

1. Install **"Extension Pack for Java"** (Microsoft) from the Extensions panel.
2. `File → Open Folder…` → select this `personalblog` folder.
3. Open `src/com/blog/Main.java`.
4. Click **Run** above the `main` method (or press `F5`).
5. Open `http://localhost:3001`.

**Option B — plain terminal:**

```bash
javac -d out $(find src -name "*.java")
java -cp out com.blog.Main
```
(Windows PowerShell: `javac -d out (Get-ChildItem -Recurse -Filter *.java -Path src).FullName`)

Either way you'll see `Personal site running at http://localhost:3001`.

The first time it runs, it will auto-create `posts/`, `vlogs/`, `uploads/`,
and `data/` folders next to `src/` and `web/` if they don't already exist.

## Adding content

**A blog post:**

1. Copy the cover image into `uploads/`.
2. Create a new `.txt` file in `posts/`, ideally named with a date prefix
   so posts stay in order, e.g. `2026-08-03-my-trip-to-japan.txt`.
3. Fill it in:

   ```
   Title: My trip to Japan
   Summary: A short one-line summary shown on the blog grid.
   Image: japan-cover.jpg

   First paragraph of the post goes here.

   Leave a blank line between paragraphs to start a new one. Wrap a
   *word or phrase* in asterisks to italicize it.
   ```

   The blank line after `Image:` is required — it's what separates the
   header from the body. `Image:` should just be the filename you copied
   into `uploads/`, not the full path.
4. Restart the server. The post appears on the Blog tab automatically.

**A vlog post:** same idea, in the `vlogs/` folder, using `Video:` and an
optional `Thumbnail:` instead of `Image:` (no body text):

```
Title: Japan trip vlog
Description: A short description shown on the vlog grid.
Video: japan-trip.mp4
Thumbnail: japan-trip-thumb.jpg
```

**Your profile (About tab):** there's no file loader for this yet — hand-edit
`data/profile.json` (create it if it doesn't exist) with `name`, `synopsis`,
`instagramMain`, `instagramSpam`, `linkedin`, and `photoPath` (a path like
`/uploads/yourphoto.jpg`, pointing at a file you've copied into `uploads/`).
Restart the server after editing it.

**Deleting something:** for a blog/vlog post, just delete its `.txt` file
from `posts/` or `vlogs/` and restart. For the profile, edit or clear the
fields in `data/profile.json`.

See `posts/README.txt` and `vlogs/README.txt` for the full format reference.

## How it's organized

```
personalblog/
├── src/com/blog/
│   ├── Main.java             HTTP server setup + all routes
│   ├── Store.java            In-memory data store + JSON persistence
│   ├── ContentLoader.java    Reads posts/*.txt and vlogs/*.txt into posts
│   ├── Models.java           Profile / BlogPost / VlogPost data classes
│   ├── SessionManager.java   Cookie-based sessions (unused by the UI now)
│   ├── PasswordUtil.java     PBKDF2 password hashing (unused by the UI now)
│   ├── MultipartParser.java  Parses multipart/form-data (unused by the UI now)
│   ├── HttpUtil.java         Cookies, JSON responses, byte-range file serving
│   └── Json.java             Dependency-free JSON reader/writer
├── web/                       Frontend: index.html, style.css, app.js (read-only)
├── posts/                      Blog posts you write as .txt files
├── vlogs/                      Vlog posts you write as .txt files
├── data/                       (created automatically — profile.json lives here)
├── uploads/                    (created automatically — photos & videos you add)
└── .vscode/                    Run/debug configuration for VS Code
```

A few of the original files (`SessionManager`, `PasswordUtil`,
`MultipartParser`) and the `/api/admin/*`, `POST /api/profile`,
`POST /api/posts`, and `POST /api/vlogs` routes in `Main.java` are left over
from the original sign-in-based version. They still work if you script
against them directly, but nothing in the web UI calls them anymore — the
file-based workflow above is the supported way to publish now.

## Notes & limits worth knowing

- **`.txt`-based posts and vlogs aren't editable or deletable from the
  website** — the `.txt` file is the source of truth, so change or delete
  the file itself and restart.
- Posts/vlogs are re-read from `posts/`/`vlogs/` on every server start, so
  changes to those files always need a restart to show up.
- Ordering uses an optional `Date: YYYY-MM-DD` line in the file, or the
  date at the start of the filename, or the file's last-modified time as a
  fallback.
- **Data lives in `data/profile.json`** and **files live in `uploads/`.**
  To reset your profile, stop the server and delete or clear that file.
- Content is stored as plain text (not full HTML/Markdown), with paragraph
  breaks preserved and a small `*italic*` markup supported — this keeps
  things simple and safe (no risk of broken or malicious HTML creeping
  into your posts).

## If you deploy this publicly

The same VPS + systemd + Caddy approach from the birdwatch project's README
applies here — see that project's notes, or ask and I can walk through it
for this app specifically. A couple of things to keep in mind:

- Uploaded photos/videos are public to anyone with the link, same as any
  normal website — there's no per-post privacy control.
- If you're on a host with an ephemeral filesystem (e.g. Render's free/
  standard plans), anything written to disk at runtime is wiped on
  restart — but since `posts/`, `vlogs/`, and `uploads/` are now just
  regular files you commit to your git repo rather than upload through a
  live form, they become part of the deployment itself and survive
  restarts and redeploys without needing a paid persistent disk. Just
  remember to `git add`/commit/push new posts, vlogs, and images before
  deploying.

## Ideas if you want to extend it

- More markup: **bold**, links, or full Markdown for blog post content
- A `profile.txt` loader, so the About page can be edited the same way as
  posts/vlogs instead of hand-editing `data/profile.json`
- Auto-generate a video thumbnail (grab a frame) instead of requiring a
  manually placed one
- Tags/categories and a simple search across posts
- An RSS feed for the blog
- Remove the now-unused sign-in code (`SessionManager`, `PasswordUtil`,
  `MultipartParser`, and the related routes in `Main.java`) if you're sure
  you'll never want it back