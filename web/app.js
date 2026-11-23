// FitLog web app (for iPhone and any browser). Same behaviour as the Android app:
// talks to the same Apps Script, queues sets offline, keeps a Today list you can edit.
import { prettyExercise, normExercise, toExerciseCode } from './names.js';

// ---------- storage (this device only) ----------
const KEY = 'fitlog.';
const store = {
  get(k, fallback) {
    try { const v = localStorage.getItem(KEY + k); return v === null ? fallback : JSON.parse(v); }
    catch { return fallback; }
  },
  set(k, v) { try { localStorage.setItem(KEY + k, JSON.stringify(v)); } catch { /* private mode */ } },
};

const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
const fmt = n => (Number.isInteger(n) ? String(n) : String(+n.toFixed(3)));
const $ = id => document.getElementById(id);

const state = {
  url: store.get('url', ''),
  secret: store.get('secret', ''),
  person: store.get('person', ''),
  people: store.get('people', []),
  exercises: store.get('exercises', []),
  pending: store.get('pending', []),
  today: store.get('todayDate', '') === todayIso() ? store.get('today', []) : [],
  date: todayIso(),
  saving: false,
};

// ---------- talking to the sheet ----------
class SheetError extends Error {}
class NetworkError extends Error {}

/**
 * POSTs to the Apps Script web app. The body is sent as text/plain so the browser doesn't need a
 * CORS preflight; Google redirects to script.googleusercontent.com, which fetch follows.
 */
async function call(body) {
  let res;
  try {
    res = await fetch(state.url, {
      method: 'POST',
      body: JSON.stringify({ ...body, secret: state.secret }),
      redirect: 'follow',
    });
  } catch (e) {
    throw new NetworkError(navigator.onLine ? "Couldn't reach the sheet" : 'No connection');
  }
  if (!res.ok) throw new SheetError(`Server returned HTTP ${res.status} — check the script URL`);
  const text = await res.text();
  try { return JSON.parse(text); }
  catch { throw new SheetError('Unexpected reply. Is the script deployed as a Web app with access "Anyone"?'); }
}

const confirmed = (s, r) => ({
  ...s, inSheet: true,
  setNo: (r.setNos && r.setNos[0] > 0) ? r.setNos[0] : null,
  row: r.row > 0 ? r.row : null,
});

// ---------- persistence helpers ----------
function saveToday() { store.set('today', state.today); store.set('todayDate', todayIso()); }
function savePending() { store.set('pending', state.pending); }
function merge(a, b) {
  const seen = new Set(); const out = [];
  for (const x of [...a, ...b].map(s => String(s).trim()).filter(Boolean)) {
    const k = x.toLowerCase(); if (!seen.has(k)) { seen.add(k); out.push(x); }
  }
  return out.sort((x, y) => x.toLowerCase().localeCompare(y.toLowerCase()));
}
function remember(who, code) {
  state.exercises = merge(state.exercises, [code]); store.set('exercises', state.exercises);
  state.people = merge(state.people, [who]); store.set('people', state.people);
}
function mergeMeta(r) {
  state.exercises = merge(state.exercises, r.exercises || []); store.set('exercises', state.exercises);
  state.people = merge(state.people, r.people || []); store.set('people', state.people);
  if (!state.person && state.people.length === 1) setPerson(state.people[0]);
  renderChips();
}

// ---------- actions ----------
async function save() {
  const who = state.person.trim();
  const code = toExerciseCode($('exercise').value, state.exercises);
  const reps = parseInt($('reps').value, 10);
  const wText = $('weight').value.replace(',', '.').trim();
  const weight = wText === '' ? 0 : Number(wText);
  if (!who) return toast('Enter who is lifting (Person)');
  if (!code) return toast('Enter an exercise name');
  if (!(reps > 0)) return toast('Enter the number of reps');
  if (!(weight >= 0) || Number.isNaN(weight)) return toast('Enter a valid weight');

  const id = Date.now();
  const payload = { action: 'log', clientId: id, person: who, date: state.date, exercise: code, sets: [{ weight, reps }] };
  const entry = {
    id, exercise: prettyExercise(code), person: who, weight: fmt(weight), reps,
    inSheet: false, setNo: null, row: null, code, date: state.date,
  };

  const done = () => { remember(who, code); clearSet(); };
  const queue = () => { state.pending.push(payload); savePending(); renderPending(); addToday(entry); done(); };
  if (!state.url) {
    queue();
    return toast('Saved on phone — add the script URL in Settings to sync');
  }

  setSaving(true);
  try {
    const r = await call(payload);
    if (r.ok) { addToday(confirmed(entry, r)); done(); syncPending(false); }
    else toast('Not saved — sheet said: ' + (r.error || 'unknown error'));
  } catch (e) {
    if (e instanceof NetworkError) queue();
    else toast('Not saved — ' + e.message);
  } finally {
    setSaving(false);
  }
}

async function syncPending(verbose) {
  if (!state.pending.length) { if (verbose) toast('Nothing to sync'); return; }
  if (!state.url) { if (verbose) toast('Add the script URL in Settings first'); return; }
  const remaining = []; let sent = 0; let lastError = null;
  for (const p of state.pending) {
    try {
      const r = await call(p);
      if (r.ok) { sent++; updateToday(p.clientId, s => confirmed(s, r)); }
      else { remaining.push(p); lastError = r.error; }
    } catch (e) { remaining.push(p); lastError = e.message; }
  }
  state.pending = remaining; savePending(); renderPending();
  if (verbose || sent) {
    toast(remaining.length ? `Synced ${sent}, ${remaining.length} still waiting${lastError ? ` (${lastError})` : ''}`
                           : `Synced ${sent} saved set${sent === 1 ? '' : 's'}`);
  }
}

async function refreshMeta() {
  if (!state.url) return;
  try { const r = await call({ action: 'exercises' }); if (r.ok) mergeMeta(r); } catch { /* offline: keep cache */ }
}

let lastReq = 0;
async function loadLast() {
  const hint = $('last');
  const code = toExerciseCode($('exercise').value, state.exercises);
  const who = state.person.trim();
  const mine = ++lastReq;
  hint.hidden = true;
  if (!code || !who || !state.url || !state.exercises.includes(code)) return;
  try {
    const r = await call({ action: 'last', person: who, exercise: code });
    if (mine !== lastReq) return;   // a newer lookup started
    if (r.ok && r.sets && r.sets.length) {
      hint.textContent = `${who} last time (${r.date}): ` + r.sets.map(s => `${fmt(+s.weight)}×${s.reps}`).join(', ');
      hint.hidden = false;
    }
  } catch { /* no hint offline */ }
}

// Edit / delete a set from the Today list.
async function editSet(s, wText, rText) {
  const reps = parseInt(rText, 10);
  const w = String(wText).replace(',', '.').trim();
  const weight = w === '' ? 0 : Number(w);
  if (!(reps > 0)) { toast('Enter the number of reps'); return false; }
  if (!(weight >= 0) || Number.isNaN(weight)) { toast('Enter a valid weight'); return false; }

  if (!s.inSheet) {
    const p = state.pending.find(q => q.clientId === s.id);
    if (p) p.sets = [{ weight, reps }];
    savePending();
    updateToday(s.id, o => ({ ...o, weight: fmt(weight), reps }));
    toast('Changed (still waiting to sync)');
    return true;
  }
  const r = await sheetEdit(s, { action: 'update', weight, reps });
  if (!r) return false;
  updateToday(s.id, o => ({ ...o, weight: fmt(weight), reps, row: r.row > 0 ? r.row : o.row }));
  toast('Updated in sheet');
  return true;
}

async function deleteSet(s) {
  if (!s.inSheet) {
    state.pending = state.pending.filter(q => q.clientId !== s.id); savePending(); renderPending();
    removeToday(s.id); toast('Deleted');
    return true;
  }
  const r = await sheetEdit(s, { action: 'delete' });
  if (!r) return false;
  const deletedRow = r.deletedRow || 0;
  removeToday(s.id);
  // Mirror the sheet: later sets of the same person/exercise/day move down a number; rows below move up.
  state.today = state.today.map(o => {
    let n = o;
    if (o.inSheet && o.person.toLowerCase() === s.person.toLowerCase() && o.code === s.code && o.date === s.date &&
        o.setNo && s.setNo && o.setNo > s.setNo) n = { ...n, setNo: o.setNo - 1 };
    if (deletedRow && o.row && o.row > deletedRow) n = { ...n, row: o.row - 1 };
    return n;
  });
  saveToday(); renderToday();
  toast('Deleted from sheet');
  return true;
}

async function sheetEdit(s, body) {
  if (!(s.setNo && s.code && s.date)) { toast("This set can't be edited from the app — change it in the sheet"); return null; }
  try {
    const r = await call({ ...body, person: s.person, exercise: s.code, date: s.date, setNo: s.setNo });
    if (r.ok) return r;
    toast('Not changed — sheet said: ' + (r.error || 'unknown error'));
  } catch (e) {
    toast(e instanceof NetworkError ? "No connection — try again when you're online" : 'Not changed — ' + e.message);
  }
  return null;
}

// ---------- Today list ----------
function addToday(entry) {
  if (store.get('todayDate', '') !== todayIso()) state.today = [];   // new day, fresh list
  state.today.unshift(entry); saveToday(); renderToday();
}
function updateToday(id, change) {
  const i = state.today.findIndex(s => s.id === id);
  if (i >= 0) { state.today[i] = change(state.today[i]); saveToday(); renderToday(); }
}
function removeToday(id) { state.today = state.today.filter(s => s.id !== id); saveToday(); renderToday(); }

const ICON_OK = '<svg class="ic" viewBox="0 0 24 24" aria-label="Saved to sheet"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-2 15-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>';
const ICON_WAIT = '<svg class="ic" viewBox="0 0 24 24" aria-label="Waiting to sync"><path d="M17.65 6.35A7.96 7.96 0 0 0 12 4a8 8 0 1 0 7.73 10h-2.08A6 6 0 1 1 12 6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg>';
const ICON_PEN = '<svg class="pen" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>';

function renderToday() {
  const list = $('today');
  $('todayWrap').hidden = state.today.length === 0;
  $('todayCount').textContent = `${state.today.length} set${state.today.length === 1 ? '' : 's'}`;
  list.replaceChildren(...state.today.map((s, i) => {
    const li = document.createElement('li');
    if (!s.inSheet) li.className = 'waiting'; else if (i === 0) li.className = 'newest';
    li.innerHTML = (s.inSheet ? ICON_OK : ICON_WAIT) + '<div class="txt"><div class="t1"></div><div class="t2"></div></div>' + ICON_PEN;
    li.querySelector('.t1').textContent = (s.setNo ? `Set ${s.setNo} · ` : '') + s.exercise;
    li.querySelector('.t2').textContent = `${s.person} · ${s.weight} × ${s.reps}` +
      (s.inSheet ? (s.row ? ` · row ${s.row}` : '') : ' · waiting to sync');
    li.addEventListener('click', () => openEdit(s.id));
    return li;
  }));
}

// ---------- rendering & UI ----------
function renderPending() {
  const n = state.pending.length;
  $('pending').hidden = n === 0;
  $('pendingText').textContent = `${n} set${n === 1 ? '' : 's'} waiting to sync`;
}

function setPerson(p) {
  state.person = p; store.set('person', p.trim());
  if ($('person').value !== p) $('person').value = p;
  renderChips(); loadLast();
}
function renderChips() {
  const cur = state.person.trim().toLowerCase();
  $('chips').replaceChildren(...state.people.map(p => {
    const b = document.createElement('button');
    b.className = 'chip'; b.type = 'button'; b.textContent = p;
    b.setAttribute('aria-pressed', String(p.toLowerCase() === cur));
    b.addEventListener('click', () => setPerson(p));
    return b;
  }));
}

function renderDate() {
  const [y, m, d] = state.date.split('-').map(Number);
  $('dateLabel').textContent = state.date === todayIso() ? 'Today' : `${m}/${d}`;
  $('date').value = state.date;
}

function setSaving(on) {
  state.saving = on;
  $('saveBtn').disabled = on;
  $('saveBtn').textContent = on ? 'Saving…' : 'Save to sheet';
}
function clearSet() { $('weight').value = ''; $('reps').value = ''; loadLast(); }

let toastTimer;
function toast(msg) {
  const t = $('toast'); t.textContent = msg; t.hidden = false;
  clearTimeout(toastTimer); toastTimer = setTimeout(() => { t.hidden = true; }, 4000);
}

// Exercise suggestions: pretty names, matching on the name or the code.
function renderSuggest(showAll) {
  const typed = $('exercise').value.trim();
  const typedCode = normExercise(typed);
  const items = state.exercises
    .map(code => [code, prettyExercise(code)])
    .filter(([code, pretty]) => showAll && !typed ? true :
      pretty.toLowerCase() !== typed.toLowerCase() &&
      (pretty.toLowerCase().includes(typed.toLowerCase()) || code.includes(typedCode)))
    .sort((a, b) => a[1].localeCompare(b[1]));
  const ul = $('suggest');
  ul.replaceChildren(...items.map(([, pretty]) => {
    const li = document.createElement('li');
    li.textContent = pretty; li.setAttribute('role', 'option');
    // pointerdown fires before the input loses focus, so the pick isn't lost
    li.addEventListener('pointerdown', e => { e.preventDefault(); pickExercise(pretty); });
    return li;
  }));
  ul.hidden = items.length === 0;
}
function pickExercise(pretty) {
  $('exercise').value = pretty; $('suggest').hidden = true; $('exercise').blur(); loadLast();
}

// Edit dialog
let editing = null; let confirmDelete = false; let busy = false;
function openEdit(id) {
  editing = state.today.find(s => s.id === id); if (!editing) return;
  confirmDelete = false;
  $('editTitle').textContent = (editing.setNo ? `Set ${editing.setNo} · ` : '') + editing.exercise;
  $('editSub').textContent = editing.person + (editing.inSheet ? ' · in the sheet' : ' · waiting to sync');
  $('editWeight').value = editing.weight; $('editReps').value = editing.reps;
  renderEditMode();
  $('editDlg').showModal();
}
function renderEditMode() {
  $('editFields').hidden = confirmDelete;
  $('editConfirm').hidden = !confirmDelete;
  $('editConfirm').textContent = editing.inSheet
    ? `Delete this set from the sheet? Later sets of ${editing.person}'s ${editing.exercise} today will be renumbered.`
    : "Delete this set? It hasn't been sent to the sheet yet.";
  $('editDelete').hidden = confirmDelete;
  $('editCancel').textContent = confirmDelete ? 'Back' : 'Cancel';
  $('editOk').textContent = confirmDelete ? 'Delete' : 'Save';
  $('editOk').classList.toggle('danger', confirmDelete);
}
function setBusy(on) { busy = on; for (const b of ['editDelete', 'editCancel', 'editOk']) $(b).disabled = on; }

// Keep the pinned Save bar above the on-screen keyboard (iOS overlays the page).
function trackKeyboard() {
  const vv = window.visualViewport; if (!vv) return;
  const update = () => {
    const kb = Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
    document.documentElement.style.setProperty('--kb', kb > 80 ? kb + 'px' : '0px');
  };
  vv.addEventListener('resize', update); vv.addEventListener('scroll', update); update();
}

// ---------- wiring ----------
function init() {
  $('person').value = state.person;
  renderChips(); renderDate(); renderPending(); renderToday();

  $('person').addEventListener('input', e => setPerson(e.target.value));
  $('date').addEventListener('change', e => { state.date = e.target.value || todayIso(); renderDate(); });

  const ex = $('exercise');
  let lastTimer;
  ex.addEventListener('input', () => { renderSuggest(false); clearTimeout(lastTimer); lastTimer = setTimeout(loadLast, 400); });
  ex.addEventListener('focus', () => renderSuggest(true));
  ex.addEventListener('blur', () => setTimeout(() => { $('suggest').hidden = true; }, 150));
  ex.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); $('suggest').hidden = true; $('weight').focus(); } });
  $('exToggle').addEventListener('click', () => {
    if ($('suggest').hidden) { ex.focus(); renderSuggest(true); } else { $('suggest').hidden = true; }
  });

  $('weight').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); $('reps').focus(); } });
  $('reps').addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); $('reps').blur(); } });
  $('saveBtn').addEventListener('click', () => { if (!state.saving) save(); });
  $('syncBtn').addEventListener('click', () => syncPending(true));

  // Settings
  const dlg = $('settingsDlg');
  const openSettings = () => { $('url').value = state.url; $('secret').value = state.secret; dlg.showModal(); };
  $('settingsBtn').addEventListener('click', openSettings);
  dlg.addEventListener('close', async () => {
    if (dlg.returnValue !== 'save') return;
    const url = $('url').value.trim();
    if (!/^https?:\/\//.test(url)) return toast('Paste the full https:// Web app URL');
    state.url = url; store.set('url', url);
    state.secret = $('secret').value.trim(); store.set('secret', state.secret);
    try {
      const r = await call({ action: 'exercises' });
      if (r.ok) { mergeMeta(r); toast('Connected to Google Sheets ✓'); syncPending(false); }
      else toast('Sheet said: ' + (r.error || 'unknown error'));
    } catch (e) { toast("Couldn't connect: " + e.message); }
  });
  if (!state.url) openSettings();

  // Edit dialog buttons
  $('editCancel').addEventListener('click', () => {
    if (busy) return;
    if (confirmDelete) { confirmDelete = false; renderEditMode(); } else $('editDlg').close();
  });
  $('editDelete').addEventListener('click', () => { confirmDelete = true; renderEditMode(); });
  $('editOk').addEventListener('click', async () => {
    if (busy || !editing) return;
    setBusy(true);
    const ok = confirmDelete ? await deleteSet(editing) : await editSet(editing, $('editWeight').value, $('editReps').value);
    setBusy(false);
    if (ok) $('editDlg').close();
  });

  trackKeyboard();
  window.addEventListener('online', () => syncPending(false));
  refreshMeta().then(() => syncPending(false));

  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
}

init();
