// app.js — frontend logic for the personal site. Talks to /api/* endpoints
// defined in Main.java. No build step, no framework — fetch + DOM only.

let isOwner = false;
let passwordIsSet = false;

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

init();

async function init() {
  wireTabs();
  wireOwnerAuth();
  wireProfileEditing();
  wireBlog();
  wireVlog();

  await refreshOwnerStatus();
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

// ---------- Owner auth ----------
function wireOwnerAuth() {
  const dialog = $('#signInDialog');

  $('#signInForm').addEventListener('submit', async e => {
    e.preventDefault();
    const errorEl = $('[data-error-for="signInForm"]');
    errorEl.textContent = '';
    const password = e.target.password.value;
    try {
      if (!passwordIsSet) {
        await api('/api/admin/claim', { method: 'POST', body: JSON.stringify({ password }) });
      } else {
        await api('/api/admin/login', { method: 'POST', body: JSON.stringify({ password }) });
      }
      dialog.close();
      e.target.reset();
      await refreshOwnerStatus();
    } catch (err) {
      errorEl.textContent = err.message;
    }
  });

  $('#cancelSignIn').addEventListener('click', () => dialog.close());
}

async function refreshOwnerStatus() {
  const status = await api('/api/admin/status');
  isOwner = status.loggedIn;
  passwordIsSet = status.passwordSet;
  renderOwnerArea();
}

function renderOwnerArea() {
  const area = $('#ownerArea');
  area.innerHTML = '';

  if (isOwner) {
    const badge = document.createElement('span');
    badge.className = 'owner-badge';
    badge.textContent = 'Owner';
    const signOutBtn = document.createElement('button');
    signOutBtn.textContent = 'Sign out';
    signOutBtn.onclick = async () => {
      await api('/api/admin/logout', { method: 'POST' });
      await refreshOwnerStatus();
    };
    area.append(badge, signOutBtn);
  } else {
    const signInBtn = document.createElement('button');
    signInBtn.textContent = 'Sign in';
    signInBtn.onclick = () => openSignInDialog();
    area.append(signInBtn);
  }

  $('#editProfileBtn').hidden = !isOwner;
  $('#photoFrame').hidden = !isOwner;
  $('#newPostBtn').hidden = !isOwner;
  $('#newVlogBtn').hidden = !isOwner;
  $('#deletePostBtn').hidden = !isOwner;
  $('#deleteVlogBtn').hidden = !isOwner;
}

function openSignInDialog() {
  const dialog = $('#signInDialog');
  $('#signInTitle').textContent = passwordIsSet ? 'Sign in' : 'Set up your site';
  $('#signInHint').textContent = passwordIsSet
    ? 'Enter the site password to edit this page.'
    : 'Choose a password for editing this site. You\u2019ll use it to sign in from now on.';
  $('[data-error-for="signInForm"]').textContent = '';
  $('#signInForm').reset();
  dialog.showModal();
}

// ---------- Profile / About tab ----------
function wireProfileEditing() {
  $('#photoFrame').addEventListener('click', () => openProfileDialog());
  $('#editProfileBtn').addEventListener('click', () => openProfileDialog());
  $('#cancelProfile').addEventListener('click', () => $('#profileDialog').close());

  $('#profileForm').addEventListener('submit', async e => {
    e.preventDefault();
    const errorEl = $('[data-error-for="profileForm"]');
    errorEl.textContent = '';
    try {
      await api('/api/profile', { method: 'POST', body: new FormData(e.target) });
      $('#profileDialog').close();
      await loadProfile();
    } catch (err) {
      errorEl.textContent = err.message;
    }
  });
}

function openProfileDialog() {
  if (!isOwner) return;
  $('[data-error-for="profileForm"]').textContent = '';
  $('#profileDialog').showModal();
}

async function loadProfile() {
  const p = await api('/api/profile');

  $('#brandName').textContent = p.name || 'Your Name';
  $('#aboutName').textContent = p.name || 'Welcome';
  $('#aboutSynopsis').textContent = p.synopsis ||
    'This is your site. Sign in as the owner to add your photo, bio, and links.';

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

  // Pre-fill the edit form with current values so re-submitting without
  // touching a field doesn't clear it.
  const form = $('#profileForm');
  form.name.value = p.name || '';
  form.synopsis.value = p.synopsis || '';
  form.instagramMain.value = p.instagramMain || '';
  form.instagramSpam.value = p.instagramSpam || '';
  form.linkedin.value = p.linkedin || '';
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

// ---------- Blog tab ----------
function wireBlog() {
  const dialog = $('#postDialog');
  $('#newPostBtn').addEventListener('click', () => {
    $('#postForm').reset();
    $('#postPhotoPreview').hidden = true;
    $('[data-error-for="postForm"]').textContent = '';
    dialog.showModal();
  });
  $('#cancelPost').addEventListener('click', () => dialog.close());

  $('#postForm').querySelector('input[name="image"]').addEventListener('change', e => {
    previewImage(e.target.files[0], $('#postPhotoPreview'));
  });

  $('#postForm').addEventListener('submit', async e => {
    e.preventDefault();
    const errorEl = $('[data-error-for="postForm"]');
    errorEl.textContent = '';
    try {
      await api('/api/posts', { method: 'POST', body: new FormData(e.target) });
      dialog.close();
      await loadPosts();
    } catch (err) {
      errorEl.textContent = err.message;
    }
  });

  $('#backFromPost').addEventListener('click', showBlogList);
  $('#deletePostBtn').addEventListener('click', async () => {
    const id = $('#deletePostBtn').dataset.id;
    if (!id) return;
    if (!confirm('Delete this post? This can\u2019t be undone.')) return;
    try {
      await api(`/api/posts/${id}`, { method: 'DELETE' });
      showBlogList();
      await loadPosts();
    } catch (err) {
      alert(err.message);
    }
  });
}

async function loadPosts() {
  const grid = $('#postsGrid');
  const rows = await api('/api/posts');
  grid.innerHTML = '';
  if (!rows.length) {
    grid.appendChild(emptyState('No posts yet.' + (isOwner ? ' Click "New post" to publish your first one.' : '')));
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
    .map(para => `<p>${escapeHtml(para).replace(/\n/g, '<br>')}</p>`)
    .join('');
  $('#deletePostBtn').dataset.id = post.id;

  $('#blogListView').hidden = true;
  $('#postDetailView').hidden = false;
}

function showBlogList() {
  $('#postDetailView').hidden = true;
  $('#blogListView').hidden = false;
}

// ---------- Vlog tab ----------
function wireVlog() {
  const dialog = $('#vlogDialog');
  $('#newVlogBtn').addEventListener('click', () => {
    $('#vlogForm').reset();
    $('#vlogPhotoPreview').hidden = true;
    $('[data-error-for="vlogForm"]').textContent = '';
    dialog.showModal();
  });
  $('#cancelVlog').addEventListener('click', () => dialog.close());

  $('#vlogForm').querySelector('input[name="thumbnail"]').addEventListener('change', e => {
    previewImage(e.target.files[0], $('#vlogPhotoPreview'));
  });

  $('#vlogForm').addEventListener('submit', async e => {
    e.preventDefault();
    const errorEl = $('[data-error-for="vlogForm"]');
    errorEl.textContent = '';
    const submitBtn = e.target.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Uploading\u2026';
    try {
      await api('/api/vlogs', { method: 'POST', body: new FormData(e.target) });
      dialog.close();
      await loadVlogs();
    } catch (err) {
      errorEl.textContent = err.message;
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Publish';
    }
  });

  $('#backFromVlog').addEventListener('click', showVlogList);
  $('#deleteVlogBtn').addEventListener('click', async () => {
    const id = $('#deleteVlogBtn').dataset.id;
    if (!id) return;
    if (!confirm('Delete this vlog? This can\u2019t be undone.')) return;
    try {
      await api(`/api/vlogs/${id}`, { method: 'DELETE' });
      showVlogList();
      await loadVlogs();
    } catch (err) {
      alert(err.message);
    }
  });
}

async function loadVlogs() {
  const grid = $('#vlogsGrid');
  const rows = await api('/api/vlogs');
  grid.innerHTML = '';
  if (!rows.length) {
    grid.appendChild(emptyState('No vlogs yet.' + (isOwner ? ' Click "New vlog" to publish your first one.' : '')));
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
  img.src = vlog.thumbnailPath || vlog.videoPath;
  img.alt = vlog.title;
  if (!vlog.thumbnailPath) {
    // No custom thumbnail — fall back to a plain dark placeholder instead
    // of pointing an <img> at a video file, which won't render a frame.
    img.removeAttribute('src');
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
  player.src = vlog.videoPath;
  $('#vlogDetailTag').textContent = formatDate(vlog.createdAt);
  $('#vlogDetailTitle').textContent = vlog.title;
  $('#vlogDetailSummary').textContent = vlog.summary;
  $('#deleteVlogBtn').dataset.id = vlog.id;

  $('#vlogListView').hidden = true;
  $('#vlogDetailView').hidden = false;
}

function showVlogList() {
  const player = $('#vlogPlayer');
  player.pause();
  player.removeAttribute('src');
  player.load();
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

function previewImage(file, imgEl) {
  if (file) {
    imgEl.src = URL.createObjectURL(file);
    imgEl.hidden = false;
  } else {
    imgEl.hidden = true;
  }
}

function formatDate(value) {
  const d = new Date(value);
  if (isNaN(d)) return value;
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}
