/**
 * FitLog backend — paste this into Extensions > Apps Script of your Google Sheet.
 *
 * 1. Change SECRET below to any password-like string (you'll type the same one in the app).
 * 2. Deploy > New deployment > type "Web app"
 *      Execute as:      Me
 *      Who has access:  Anyone
 * 3. Copy the Web app URL (ends in /exec) into the app's Settings.
 */

const SECRET = 'change-me-to-something-long';
const SHEET_NAME = 'Log'; // rows are written to this tab (created automatically)
const HEADERS = ['Timestamp', 'Date', 'Exercise', 'Set', 'Weight', 'Reps', 'Unit', 'Volume', 'Notes'];

function doPost(e) {
  try {
    const body = JSON.parse(e.postData.contents);
    if (body.secret !== SECRET) return json_({ ok: false, error: 'Wrong secret' });

    switch (body.action) {
      case 'log':       return json_(logSets_(body));
      case 'exercises': return json_(listExercises_());
      case 'last':      return json_(lastSession_(String(body.exercise || '')));
      default:          return json_({ ok: false, error: 'Unknown action' });
    }
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function doGet() {
  return json_({ ok: true, message: 'FitLog endpoint is running. Use the app to log workouts.' });
}

function logSets_(body) {
  const exercise = String(body.exercise || '').trim();
  const sets = Array.isArray(body.sets) ? body.sets : [];
  if (!exercise || sets.length === 0) return { ok: false, error: 'Missing exercise or sets' };

  const sheet = getSheet_();
  const now = new Date();
  const date = body.date || Utilities.formatDate(now, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  const unit = body.unit || 'kg';
  const notes = body.notes || '';

  const rows = sets.map(function (s, i) {
    const w = Number(s.weight) || 0;
    const r = Number(s.reps) || 0;
    return [now, date, exercise, i + 1, w, r, unit, w * r, i === 0 ? notes : ''];
  });

  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    sheet.getRange(sheet.getLastRow() + 1, 1, rows.length, HEADERS.length).setValues(rows);
  } finally {
    lock.releaseLock();
  }
  return { ok: true, added: rows.length };
}

function listExercises_() {
  const data = readRows_();
  const seen = {};
  data.forEach(function (r) { if (r[2]) seen[String(r[2]).trim()] = true; });
  return { ok: true, exercises: Object.keys(seen).sort() };
}

/** Returns the sets from the most recent save of the given exercise. */
function lastSession_(exercise) {
  const target = exercise.trim().toLowerCase();
  const data = readRows_();
  let stamp = null;
  for (let i = data.length - 1; i >= 0; i--) {
    if (String(data[i][2]).trim().toLowerCase() === target) { stamp = toTime_(data[i][0]); break; }
  }
  if (stamp === null) return { ok: true, sets: [] };

  const rows = data.filter(function (r) {
    return String(r[2]).trim().toLowerCase() === target && toTime_(r[0]) === stamp;
  });
  const d = rows[0][1];
  return {
    ok: true,
    date: d instanceof Date ? Utilities.formatDate(d, Session.getScriptTimeZone(), 'yyyy-MM-dd') : String(d),
    unit: rows[0][6],
    sets: rows.map(function (r) { return { weight: Number(r[4]), reps: Number(r[5]) }; })
  };
}

function getSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) sheet = ss.insertSheet(SHEET_NAME);
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(HEADERS);
    sheet.setFrozenRows(1);
    sheet.getRange(1, 1, 1, HEADERS.length).setFontWeight('bold');
  }
  return sheet;
}

function readRows_() {
  const sheet = getSheet_();
  const n = sheet.getLastRow() - 1;
  return n > 0 ? sheet.getRange(2, 1, n, HEADERS.length).getValues() : [];
}

function toTime_(v) {
  return v instanceof Date ? v.getTime() : String(v);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
