/**
 * FitLog backend — paste this into Extensions > Apps Script of your Google Sheet.
 *
 * 1. Change SECRET below to any password-like string (you'll type the same one in the app).
 * 2. Deploy > New deployment > type "Web app"
 *      Execute as:      Me
 *      Who has access:  Anyone
 * 3. Copy the Web app URL (ends in /exec) into the app's Settings.
 *
 * After editing this file later: Deploy > Manage deployments > ✏ > Version: New version
 * (keeps the same URL).
 */

const SECRET = 'change-me-to-something-long';
const SHEET_NAME = 'LiftData';
const HEADERS = ['Person', 'Date', 'Exercise', 'SetNo', 'Weight', 'Reps'];
const DATE_FORMAT = 'M/d/yyyy';

function doPost(e) {
  try {
    const body = JSON.parse(e.postData.contents);
    if (body.secret !== SECRET) return json_({ ok: false, error: 'Wrong secret' });

    switch (body.action) {
      case 'log':       return json_(logSets_(body));
      case 'exercises': return json_(meta_());
      case 'last':      return json_(lastSession_(String(body.person || ''), String(body.exercise || '')));
      default:          return json_({ ok: false, error: 'Unknown action' });
    }
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function doGet() {
  return json_({ ok: true, message: 'FitLog endpoint is running. Use the app to log workouts.' });
}

/**
 * Appends one row per set. SetNo continues from sets already logged for the same
 * person + exercise + date, so logging set by set (or in batches) numbers them 1, 2, 3...
 */
function logSets_(body) {
  const person = String(body.person || '').trim();
  const exercise = normExercise_(body.exercise);
  const sets = Array.isArray(body.sets) ? body.sets : [];
  if (!person) return { ok: false, error: 'Missing person' };
  if (!exercise || sets.length === 0) return { ok: false, error: 'Missing exercise or sets' };

  const date = parseDate_(body.date);
  const dateKey = dayKey_(date);

  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const t = table_();
    let setNo = 0;
    t.rows.forEach(function (r) {
      if (sameText_(r[t.col.Person], person) &&
          normExercise_(r[t.col.Exercise]) === exercise &&
          dayKey_(r[t.col.Date]) === dateKey) {
        setNo = Math.max(setNo, Number(r[t.col.SetNo]) || 0);
      }
    });

    const rows = sets.map(function (s) {
      const row = new Array(t.width).fill('');
      row[t.col.Person] = person;
      row[t.col.Date] = date;
      row[t.col.Exercise] = exercise;
      row[t.col.SetNo] = ++setNo;
      row[t.col.Weight] = Number(s.weight) || 0;
      row[t.col.Reps] = Number(s.reps) || 0;
      return row;
    });

    const start = t.sheet.getLastRow() + 1;
    t.sheet.getRange(start, 1, rows.length, t.width).setValues(rows);
    t.sheet.getRange(start, t.col.Date + 1, rows.length, 1).setNumberFormat(DATE_FORMAT);
    return { ok: true, added: rows.length };
  } finally {
    lock.releaseLock();
  }
}

/** Known exercises and people, for the app's suggestions. */
function meta_() {
  const t = table_();
  const ex = {}, people = {};
  t.rows.forEach(function (r) {
    const e = normExercise_(r[t.col.Exercise]);
    const p = String(r[t.col.Person]).trim();
    if (e) ex[e] = true;
    if (p) people[p] = true;
  });
  return { ok: true, exercises: Object.keys(ex).sort(), people: Object.keys(people).sort() };
}

/** Sets from the most recent date this person did this exercise. */
function lastSession_(person, exercise) {
  const ex = normExercise_(exercise);
  const t = table_();
  const mine = t.rows.filter(function (r) {
    return sameText_(r[t.col.Person], person) && normExercise_(r[t.col.Exercise]) === ex;
  });
  if (mine.length === 0) return { ok: true, sets: [] };

  let latest = '';
  mine.forEach(function (r) { const k = dayKey_(r[t.col.Date]); if (k > latest) latest = k; });
  const rows = mine
    .filter(function (r) { return dayKey_(r[t.col.Date]) === latest; })
    .sort(function (a, b) { return (Number(a[t.col.SetNo]) || 0) - (Number(b[t.col.SetNo]) || 0); });

  return {
    ok: true,
    date: Utilities.formatDate(parseDate_(latest), Session.getScriptTimeZone(), DATE_FORMAT),
    sets: rows.map(function (r) { return { weight: Number(r[t.col.Weight]), reps: Number(r[t.col.Reps]) }; })
  };
}

// ---------- helpers ----------

/** Reads LiftData, locating columns by their header names (creates the tab if missing). */
function table_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
    sheet.appendRow(HEADERS);
    sheet.setFrozenRows(1);
  }
  const width = Math.max(sheet.getLastColumn(), 1);
  const header = sheet.getRange(1, 1, 1, width).getValues()[0].map(function (h) { return String(h).trim(); });
  const col = {};
  HEADERS.forEach(function (h) {
    const i = header.indexOf(h);
    if (i < 0) throw new Error('Column "' + h + '" not found in row 1 of ' + SHEET_NAME);
    col[h] = i;
  });
  const n = sheet.getLastRow() - 1;
  const rows = n > 0 ? sheet.getRange(2, 1, n, width).getValues() : [];
  return { sheet: sheet, col: col, width: width, rows: rows };
}

/** "Hip Adduction" / "hip-adduction" -> "hip_adduction", matching the sheet's style. */
function normExercise_(v) {
  return String(v || '').trim().toLowerCase().replace(/[\s\-]+/g, '_');
}

function sameText_(a, b) {
  return String(a).trim().toLowerCase() === String(b).trim().toLowerCase();
}

/** Accepts a Date, "yyyy-MM-dd" or "M/d/yyyy"; returns a Date at local midnight. */
function parseDate_(v) {
  if (v instanceof Date) return new Date(v.getFullYear(), v.getMonth(), v.getDate());
  const s = String(v || '').trim();
  let m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  if (m) return new Date(+m[1], +m[2] - 1, +m[3]);
  m = s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (m) return new Date(+m[3], +m[1] - 1, +m[2]);
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

/** Sortable "yyyy-MM-dd" key for a date cell (Date object or text). */
function dayKey_(v) {
  const d = parseDate_(v);
  const p = function (x) { return (x < 10 ? '0' : '') + x; };
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
