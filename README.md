# FitLog: log your sets to Google Sheets

An Android app where you enter the exercise and the weight × reps for each set. Each set is added as a row in **your own** Google Sheet, so you can chart your progress however you like.

```
FitLog app  ──▶  small Apps Script attached to your sheet  ──▶  "Log" tab in your sheet
```

Everyone who uses the app connects it to their own sheet, so your data stays separate from your friends'.

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
2. Choose the date (today by default), type the exercise, pick kg or lb, and fill in weight and reps for each set. **Add set** copies the previous set's numbers, so you only edit what changed.
3. Tap **Save to Google Sheets**.

The rows go into a tab called **Log**, which is created automatically:

`Timestamp | Date | Exercise | Set | Weight | Reps | Unit | Volume | Notes`

Other features:
- **Exercise autocomplete:** suggests names you've logged before.
- **"Last time" line:** shows your previous session for that exercise (e.g. `60×8, 60×8, 62.5×6 kg`), so you know what to beat.
- **Offline logging:** without signal, the workout is saved on the phone and sent when you're back online.
- **Bodyweight exercises:** leave the weight empty and it's logged as 0.

### Charting progress

Add a new tab with, for example, your best weight per day for one exercise:

```
=QUERY(Log!A:I, "select B, max(E) where C = 'Bench Press' group by B label max(E) 'Top set'", 1)
```

Then **Insert → Chart → Line chart**.

---

## Maintainer notes

- `FitLogApp/` is the Android Studio project (Kotlin + Jetpack Compose, min Android 8.0).
- Every push to `main` that changes `FitLogApp/` makes [GitHub Actions](.github/workflows/build-apk.yml) build a signed APK and publish it as a new release (`v1.0.<run number>`). You can also start a build from the Actions tab with **Run workflow**.
- The signing key is kept **outside the repo** in repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The original key file is `signing/fitlog-release.jks`, which git ignores. Keep a backup: if the key is lost, new versions can't install over old ones.
