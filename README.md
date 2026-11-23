# FitLog: log your sets to Google Sheets

An app for **Android and iPhone** where you enter the exercise and the weight × reps for each set. Each set is added as a row in **your own** Google Sheet, so you can chart your progress however you like.

```
FitLog app  ──▶  small Apps Script attached to your sheet  ──▶  "LiftData" tab in your sheet
```

Training partners can share one sheet: every row has a **Person** column. Groups who want their data kept apart each connect the app to their own sheet.

---

## 1. Install the app (on your phone, no computer needed)

### iPhone

1. Open **https://sametcan-yuzlu.github.io/FitLog/** in **Safari**.
2. Tap the **Share** button (square with an arrow) → **Add to Home Screen** → **Add**.
3. Open **FitLog** from your home screen. It opens full-screen with the lime dumbbell icon, like a normal app.

Do steps 2 and 3 **before** entering your script URL. On iPhone, the home-screen app keeps its own settings, separate from the Safari tab. Updates arrive on their own: the next time you open it with internet, you get the latest version. It also opens without signal; sets saved offline are sent when you're back online.

(It works in any browser on Android or a computer too, at the same link.)

### Android

1. On your Android phone, open
   **https://github.com/Sametcan-Yuzlu/FitLog/releases/latest/download/FitLog.apk**
2. Open the downloaded file. If Android asks, allow your browser to **install unknown apps**.
3. If Play Protect says the developer is unrecognised, tap **More details → Install anyway**. The app comes from this repo, not the Play Store, which is why you see this.

New versions show up on the [Releases page](https://github.com/Sametcan-Yuzlu/FitLog/releases). Download and open the new APK the same way, and it updates in place without losing your settings.

## 2. Connect your Google Sheet (one time, about 5 minutes, easiest on a computer)

1. Create a Google Sheet, or use one you already have.
2. In the sheet, go to **Extensions → Apps Script**.
3. Delete the sample code and paste everything from [`apps-script/Code.gs`](apps-script/Code.gs). Click **Save**.
4. On line 14, change `SECRET` to your own random string. The app will ask for it.
5. Click **Deploy → New deployment**. Pick ⚙ **Web app** and set:
   - *Execute as:* **Me**
   - *Who has access:* **Anyone** (the app doesn't sign in to Google; your secret is what protects the sheet)
6. Click **Deploy** and approve the permissions. Google warns that the app is unverified: choose *Advanced → Go to … (unsafe)*. This is your own script, so it's fine.
7. Copy the **Web app URL**, which ends in `/exec`. Send it to your phone, for example by emailing it to yourself.

> When you change `Code.gs` later, use **Deploy → Manage deployments → ✏ → Version: New version**. This keeps the same URL.

## 3. Use it

1. On first launch, enter the **Web app URL** and **Secret**. After you save, it should say *Connected to Google Sheets ✓*.
2. Pick the **person** (tap a name chip, or type a new name), check the date (today by default), choose the exercise, and enter the weight and reps of the set you just did.
3. Tap **Save to sheet**. Weight and reps clear, but person, date and exercise stay filled in, so a partner can tap their name and log their set straight after.

Rows go into the **LiftData** tab, one row per set:

| Person | Date | Exercise | SetNo | Weight | Reps |
|---|---|---|---|---|---|
| Samet | 10/22/2026 | hip_adduction | 1 | 85 | 12 |

- Columns are found by their header names in row 1, so extra columns or a different column order are fine. If the tab doesn't exist yet, it's created with these headers.
- **Exercise names:** the app lists the items from the Exercise column's dropdown (data validation) plus anything already logged. It shows them readably, e.g. `biceps_curl(barbell)` appears as "Biceps Curl (Barbell)", but always saves the exact code from the sheet. A new name you type is saved in the same style: "Fly (Cable)" becomes `fly(cable)`.
- **SetNo** is filled in by the sheet: it counts on from the sets already logged that day for that person and exercise (1, 2, 3, …).

Other features:
- **Today list:** every set saved from your phone today appears under the form, newest first. A green tick means the sheet confirmed it, with the set number and row it wrote (e.g. *Set 2 · Leg Press — Samet · 105 × 10 · row 128*). Amber means it's saved on the phone and waiting to sync.
- **Fix mistakes:** tap a set in the Today list to change its weight and reps, or to delete it. Deleting removes the row and renumbers that person's later sets of the exercise that day, so SetNo has no gaps.
- **Suggestions:** names and exercises already in the sheet show up as you type.
- **"Last time" line:** shows that person's previous session for the exercise (e.g. `Samet last time (10/22/2026): 85×12, 85×13`), so you know what to beat.
- **Offline logging:** without signal, sets are saved on the phone and sent when you're back online.
- **Bodyweight exercises:** leave the weight empty and it's logged as 0.

### Charting progress

Add a new tab with, for example, Samet's best leg press weight per day:

```
=QUERY(LiftData!A:F, "select B, max(E) where A = 'Samet' and C = 'leg_press' group by B label max(E) 'Top set'", 1)
```

Then **Insert → Chart → Line chart**.

---

## Maintainer notes

- `FitLogApp/` is the Android Studio project (Kotlin + Jetpack Compose, min Android 8.0).
- `web/` is the iPhone / browser version: plain HTML, CSS and JavaScript with no build step. It's an installable web app with offline support (`sw.js`) and talks to the same Apps Script. Every push that changes `web/` publishes it to GitHub Pages via [pages.yml](.github/workflows/pages.yml). Open `web/tests.html` in a browser to run the exercise-name tests, and run `web/make-icons.ps1` to redraw the icons.
- Both apps use the same exercise-name rules (`ExerciseNames.kt` and `web/names.js`), so keep them in sync.
- Every push to `main` that changes `FitLogApp/` makes [GitHub Actions](.github/workflows/build-apk.yml) build a signed APK and publish it as a new release (`v1.0.<run number>`). You can also start a build from the Actions tab with **Run workflow**.
- The signing key is kept **outside the repo** in repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The original key file is `signing/fitlog-release.jks`, which git ignores. Keep a backup: if the key is lost, new versions can't install over old ones.
