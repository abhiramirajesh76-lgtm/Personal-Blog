// app.js — frontend logic for the personal site. Talks to /api/* endpoints
// defined in Main.java. No build step, no framework — fetch + DOM only.

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

init();

async function init() {
  wireTabs();
  wireBlog();
  wireVlog();

  await loadProfile();
  await loadPosts();
  await loadVlogs();
}

// ---------- API helper ----------
async function api(url, options = {}) {
  const res = await fetch(url, {
    headers: options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' },
    ...options,
  });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error((data && data.error) || 'Something went wrong');
  return data;
}

// ---------- Tabs ----------
function wireTabs() {
  $$('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => activateTab(btn.dataset.tab));
  });
}

function activateTab(name) {
  $$('.tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === name));
  $$('.tab-panel').forEach(p => p.hidden = true);
  $(`#${name}Tab`).hidden = false;

  // Reset sub-views to their list state when switching tabs
  if (name === 'blog') showBlogList();
  if (name === 'vlog') showVlogList();
}

// ---------- Profile / About tab ----------
async function loadProfile() {
  const p = await api('/api/profile');

  $('#brandName').textContent = p.name || 'Your Name';
  $('#aboutName').textContent = p.name || 'Welcome';
  $('#aboutSynopsis').textContent = p.synopsis || 'This is your site.';

  const img = $('#profilePhoto');
  const placeholder = $('#photoPlaceholder');
  if (p.photoPath) {
    img.src = p.photoPath;
    img.hidden = false;
    placeholder.hidden = true;
  } else {
    img.hidden = true;
    placeholder.hidden = false;
  }

  const social = $('#socialRow');
  social.innerHTML = '';
  const links = [
    { url: p.instagramMain, label: 'Instagram', icon: 'instagram' },
    { url: p.instagramSpam, label: 'Instagram (spam)', icon: 'instagram' },
    { url: p.linkedin, label: 'LinkedIn', icon: 'linkedin' },
  ];
  for (const link of links) {
    if (!link.url) continue;
    const a = document.createElement('a');
    a.href = link.url;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
    a.className = 'social-pill';
    a.innerHTML = iconSvg(link.icon) + `<span>${escapeHtml(link.label)}</span>`;
    social.appendChild(a);
  }
}

function iconSvg(name) {
  if (name === 'instagram') {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="5"/><circle cx="12" cy="12" r="4"/><circle cx="17.2" cy="6.8" r="1"/></svg>';
  }
  if (name === 'linkedin') {
    return '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M4.98 3.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5zM3 9h4v12H3V9zm7 0h3.8v1.7h.05c.53-1 1.83-2.05 3.77-2.05C21.5 8.65 22 11 22 14.1V21h-4v-6.1c0-1.45-.03-3.3-2-3.3-2 0-2.3 1.6-2.3 3.2V21h-4V9z"/></svg>';
  }
  return '';
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

/** Turns *word* or *a few words* into <em>...</em>. Runs after escapeHtml, so it's safe against stray HTML. */
function italicize(str) {
  return str.replace(/\*([^*\n]+)\*/g, '<em>$1</em>');
}

// ---------- Blog tab ----------
function wireBlog() {
  $('#backFromPost').addEventListener('click', showBlogList);
}

async function loadPosts() {
  const grid = $('#postsGrid');
  const rows = await api('/api/posts');
  grid.innerHTML = '';
  if (!rows.length) {
    grid.appendChild(emptyState('No posts yet. Add a .txt file to the posts/ folder to publish one.'));
    return;
  }
  rows.forEach((post, i) => grid.appendChild(postCard(post, rows.length - i)));
}

function postCard(post, entryNumber) {
  const card = document.createElement('button');
  card.className = 'content-card';
  card.addEventListener('click', () => openPost(post.id));

  const thumbWrap = document.createElement('div');
  thumbWrap.className = 'card-thumb-wrap';
  const img = document.createElement('img');
  img.className = 'card-thumb';
  img.src = post.imagePath;
  img.alt = post.title;
  thumbWrap.appendChild(img);

  const body = document.createElement('div');
  body.className = 'card-body';
  body.innerHTML = `
    <p class="entry-tag">Entry ${String(entryNumber).padStart(3, '0')} \u00b7 ${formatDate(post.createdAt)}</p>
    <h3 class="card-title"></h3>
    <p class="card-summary"></p>
  `;
  body.querySelector('.card-title').textContent = post.title;
  body.querySelector('.card-summary').textContent = post.summary;

  card.append(thumbWrap, body);
  return card;
}

async function openPost(id) {
  const post = await api(`/api/posts/${id}`);
  $('#postDetailImage').src = post.imagePath;
  $('#postDetailImage').alt = post.title;
  $('#postDetailTag').textContent = formatDate(post.createdAt);
  $('#postDetailTitle').textContent = post.title;
  $('#postDetailBody').innerHTML = post.content
    .split(/\n\s*\n/)
    .map(para => `<p>${italicize(escapeHtml(para)).replace(/\n/g, '<br>')}</p>`)
    .join('');

  $('#blogListView').hidden = true;
  $('#postDetailView').hidden = false;
}

function showBlogList() {
  $('#postDetailView').hidden = true;
  $('#blogListView').hidden = false;
}

// ---------- Vlog tab ----------
function wireVlog() {
  $('#backFromVlog').addEventListener('click', showVlogList);
}

async function loadVlogs() {
  const grid = $('#vlogsGrid');
  const rows = await api('/api/vlogs');
  grid.innerHTML = '';
  if (!rows.length) {
    grid.appendChild(emptyState('No vlogs yet. Add a .txt file to the vlogs/ folder to publish one.'));
    return;
  }
  rows.forEach((vlog, i) => grid.appendChild(vlogCard(vlog, rows.length - i)));
}

function vlogCard(vlog, entryNumber) {
  const card = document.createElement('button');
  card.className = 'content-card';
  card.addEventListener('click', () => openVlog(vlog.id));

  const thumbWrap = document.createElement('div');
  thumbWrap.className = 'card-thumb-wrap';
  const img = document.createElement('img');
  img.className = 'card-thumb';
  img.alt = vlog.title;
  if (vlog.thumbnailPath) {
    img.src = vlog.thumbnailPath;
  } else {
    // No thumbnail available at all — fall back to a plain dark placeholder.
    img.style.background = '#1c2028';
  }
  thumbWrap.appendChild(img);

  const overlay = document.createElement('div');
  overlay.className = 'play-overlay';
  overlay.innerHTML = '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="11" fill="rgba(20,24,29,0.55)"/><path d="M10 8l6 4-6 4V8z" fill="#fff"/></svg>';
  thumbWrap.appendChild(overlay);

  const body = document.createElement('div');
  body.className = 'card-body';
  body.innerHTML = `
    <p class="entry-tag">Entry ${String(entryNumber).padStart(3, '0')} \u00b7 ${formatDate(vlog.createdAt)}</p>
    <h3 class="card-title"></h3>
    <p class="card-summary"></p>
  `;
  body.querySelector('.card-title').textContent = vlog.title;
  body.querySelector('.card-summary').textContent = vlog.summary;

  card.append(thumbWrap, body);
  return card;
}

async function openVlog(id) {
  const vlog = await api(`/api/vlogs/${id}`);
  const player = $('#vlogPlayer');
  player.src = `https://www.youtube-nocookie.com/embed/${vlog.youtubeId}?rel=0`;
  player.title = vlog.title;
  $('#vlogDetailTag').textContent = formatDate(vlog.createdAt);
  $('#vlogDetailTitle').textContent = vlog.title;
  $('#vlogDetailSummary').textContent = vlog.summary;

  $('#vlogListView').hidden = true;
  $('#vlogDetailView').hidden = false;
}

function showVlogList() {
  const player = $('#vlogPlayer');
  player.src = ''; // stop playback by clearing the embed
  $('#vlogDetailView').hidden = true;
  $('#vlogListView').hidden = false;
}

// ---------- Shared helpers ----------
function emptyState(text) {
  const div = document.createElement('div');
  div.className = 'empty-state';
  div.textContent = text;
  return div;
}

function formatDate(value) {
  const d = new Date(value);
  if (isNaN(d)) return value;
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}