# Personal Site — About / Blog / Vlog

A personal website with three sections:

- **About** — your photo, a short bio, and links to your social accounts
  (Instagram, a second/spam Instagram, LinkedIn)
- **Blog** — a grid of posts (cover image, title, short summary); click a
  thumbnail to read the full post
- **Vlog** — a YouTube-style grid of videos (thumbnail, title, description);
  click one to watch it in a full player

Everything — your photo, posts, and videos — is something *you* upload
through the site itself. No accounts for visitors; instead there's a single
**owner sign-in** (a password you set the first time you use the site) that
unlocks editing. Anyone can view the site; only you can post to it.

## What it's built with

Plain Java, same philosophy as the birdwatch project: **zero external
dependencies**. Just the JDK's built-in HTTP server
(`com.sun.net.httpserver`), built-in crypto for password hashing
(`javax.crypto`, PBKDF2), and a small hand-written JSON layer. Nothing to
download with Maven/Gradle — open the folder and run it.

The one new piece of engineering here versus the birdwatch app is **HTTP
byte-range support** for video files (see `HttpUtil.serveFileWithRangeSupport`).
Without it, browsers can only play a video start-to-finish; with it, you can
drag the scrubber to jump around — this is what makes the vlog player behave
like a normal video site instead of a dumb file download.

## Running it in VS Code

**Option A — with the Java extension (recommended):**

1. Install **"Extension Pack for Java"** (Microsoft) from the Extensions panel.
2. `File → Open Folder…` → select this `personalblog` folder.
3. Open `src/com/blog/Main.java`.
4. Click **Run** above the `main` method (or press `F5`).
5. Open `http://localhost:3000`.

**Option B — plain terminal:**

```bash
javac -d out $(find src -name "*.java")
java -cp out com.blog.Main
```
(Windows PowerShell: `javac -d out (Get-ChildItem -Recurse -Filter *.java -Path src).FullName`)

Either way you'll see `Personal site running at http://localhost:3000`.

## First-time setup

The very first time you open the site and click **Sign in** in the top
right, you'll be asked to *set* a password (6+ characters) rather than enter
one — that's expected, since no owner password exists yet. After that, the
same button becomes a normal sign-in.

Once signed in:
- Click your (empty) profile photo circle, or **Edit profile**, on the About
  tab to add your photo, bio, and social links.
- Click **+ New post** on the Blog tab to publish a post (cover image,
  title, short summary, and the full body text — leave a blank line
  between paragraphs to start a new one).
- Click **+ New vlog** on the Vlog tab to publish a video (the video file
  itself, plus an optional thumbnail image — a saved frame from the video
  works well; without one, the card just shows a plain dark placeholder
  until clicked).

Sign-in lasts 30 days per browser (a cookie), so you won't need to
re-enter your password constantly while you're actively posting.

## How it's organized

```
personalblog/
├── src/com/blog/
│   ├── Main.java             HTTP server setup + all routes
│   ├── Store.java            In-memory data store + JSON persistence
│   ├── Models.java           Profile / BlogPost / VlogPost data classes
│   ├── SessionManager.java   Cookie-based owner sessions
│   ├── PasswordUtil.java     PBKDF2 password hashing
│   ├── MultipartParser.java  Parses photo/video uploads (multipart/form-data)
│   ├── HttpUtil.java         Cookies, JSON responses, byte-range file serving
│   └── Json.java             Dependency-free JSON reader/writer
├── web/                       Frontend: index.html, style.css, app.js
├── data/                       (created automatically — your saved content)
├── uploads/                    (created automatically — photos & videos)
└── .vscode/                    Run/debug configuration for VS Code
```

## Notes & limits worth knowing

- **Video upload limit is 250MB.** Large videos are currently buffered in
  memory during upload before being written to disk — fine for occasional
  personal uploads, but if you're regularly uploading long/high-resolution
  video, keep an eye on the server's available RAM. (A future improvement
  would be streaming the upload straight to disk instead of buffering it.)
- **Photo upload limit is 8MB** per image.
- **Sessions reset on restart** (you'll need to sign in again after
  restarting the server), but your posts, profile, and uploaded files are
  all safely on disk and unaffected.
- **Data lives in `data/*.json`** and **files live in `uploads/`.** To
  reset the whole site, stop the server and delete both folders.
- Blog post content is stored as plain text (not HTML/Markdown) and
  rendered with paragraph breaks preserved — this keeps things simple and
  safe (no risk of broken or malicious HTML creeping into your posts). If
  you want bold/italic/links within posts later, that's a natural
  extension — see below.

## If you deploy this publicly

The same VPS + systemd + Caddy approach from the birdwatch project's README
applies here — see that project's notes, or ask and I can walk through it
for this app specifically. A couple of things to keep in mind once it's
public:

- **Pick a real password** when you first sign in — it's the only thing
  standing between "just you can post" and "anyone can post."
- Uploaded photos/videos are public to anyone with the link, same as any
  normal website — there's no per-post privacy control.

## Ideas if you want to extend it

- Rich text or Markdown formatting for blog post content
- Auto-generate a video thumbnail (grab a frame) instead of requiring a
  manually uploaded one
- Tags/categories and a simple search across posts
- An RSS feed for the blog
- Comments (would need to bring back a lightweight visitor-accounts system,
  similar to the birdwatch app's login)
# Personal-Blog
