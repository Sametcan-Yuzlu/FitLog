# FitLog: log your sets to Google Sheets

An Android app where you enter the exercise and the weight × reps for each set. Each set is added as a row in **your own** Google Sheet, so you can chart your progress however you like.

```
FitLog app  ──▶  small Apps Script attached to your sheet  ──▶  "LiftData" tab in your sheet
```

Training partners can share one sheet: every row has a **Person** column. Groups who want their data kept apart each connect the app to their own sheet.

---

## 1. Install the app (on your phone, no computer needed)

1. On your Android phone, open
   **https://github.com/Sametcan-Yuzlu/FitLog/releases/latest/download/FitLog.apk**
2. Open the downloaded file. If Android asks, allow your browser to **install unknown apps**.
3. If Play Protect says the developer is unrecognised, tap **More details → Install anyway**. The app comes from this repo, not the Play Store, which is why you see this.

New versions show up on the [Releases page](https://github.com/Sametcan-Yuzlu/FitLog/releases). Download and open the new APK the same way, and it updates in place without losing your settings.

## 2. Connect your Google Sheet (one time, about 5 minutes, easiest on a computer)

1. Create a Google Sheet, or use one you already have.
2. In the sheet, go to **Extensions → Apps Script**.
3. Delete the sample code and paste everything from [`apps-script/Code.gs`](apps-script/Code.gs). Click **Save**.
4. On line 11, change `SECRET` to your own random string. The app will ask for it.
5. Click **Deploy → New deployment**. Pick ⚙ **Web app** and set:
   - *Execute as:* **Me**
   - *Who has access:* **Anyone** (the app doesn't sign in to Google; your secret is what protects the sheet)
6. Click **Deploy** and approve the permissions. Google warns that the app is unverified: choose *Advanced → Go to … (unsafe)*. This is your own script, so it's fine.
7. Copy the **Web app URL**, which ends in `/exec`. Send it to your phone, for example by emailing it to yourself.

> When you change `Code.gs` later, use **Deploy → Manage deployments → ✏ → Version: New version**. This keeps the same URL.

## 3. Use it

1. On first launch, enter the **Web app URL** and **Secret**. After you save, it should say *Connected to Google Sheets ✓*.
2. Pick the **person** (tap a name chip, or type a new name), check the date (today by default), type the exercise, and fill in weight and reps. **Add set** copies the previous set's numbers.
3. Tap **Save to Google Sheets**. The person, date and exercise stay filled in, so a partner can tap their name and log their set straight after.

Rows go into the **LiftData** tab, one row per set:

| Person | Date | Exercise | SetNo | Weight | Reps |
|---|---|---|---|---|---|
| Samet | 10/22/2026 | hip_adduction | 1 | 85 | 12 |

- Columns are found by their header names in row 1, so extra columns or a different column order are fine. If the tab doesn't exist yet, it's created with these headers.
- Exercise names are stored in lowercase with underscores: typing "Leg Press" saves `leg_press`.
- **SetNo** continues from the sets already logged that day for that person and exercise. Logging one set at a time numbers them 1, 2, 3, …

Other features:
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
- Every push to `main` that changes `FitLogApp/` makes [GitHub Actions](.github/workflows/build-apk.yml) build a signed APK and publish it as a new release (`v1.0.<run number>`). You can also start a build from the Actions tab with **Run workflow**.
- The signing key is kept **outside the repo** in repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The original key file is `signing/fitlog-release.jks`, which git ignores. Keep a backup: if the key is lost, new versions can't install over old ones.
