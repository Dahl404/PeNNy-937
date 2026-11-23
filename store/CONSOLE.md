# Getting PeNNy-937 into production — the actual path

Account type: **personal**. That decides the order below: personal accounts must run a
closed test with **12 testers for 14 continuous days** before Play lets you apply for
production access. Internal testing does not count toward it; the 14 days start when 12
testers are opted in on a closed track. (Google adjusts this rule occasionally — the
Apply-for-production page states the current numbers, trust it over this file.)

Total honest timeline: **~3 weeks** to public listing, most of it waiting.

---

## 0. Before you touch the console (done)

- [x] Upload keystore generated: `~/keys/penny-937-upload.jks`, alias `upload`, RSA 4096,
      valid until 2054. Password in `~/keys/penny-937-upload.pass` (chmod 600).
      **Back both up now** — a password manager entry plus an offline copy. Losing it means
      filing an upload-key reset with Google and waiting.
- [x] `keystore.properties` (gitignored) wires it into `app/build.gradle.kts`.
- [x] Signed AAB: `app/build/outputs/bundle/release/app-release.aab` (8.7 MB, verified).
- [x] Store graphics: `store/icon_512.png`, `store/feature_1024x500.png`.
- [x] Listing copy: `store/listing.md`.
- [x] Privacy policy: `store/privacy.html` (must be hosted — see step 1).

Rebuild any time with:

```bash
source ~/.local/opt/android-env.sh
cd ~/projects/apps/penny-937
./gradlew bundleRelease
```

---

## 1. Developer account housekeeping (do first, it blocks review)

1. **Verify your identity** — Play Console → Settings → Developer account → Account
   details. Government ID + address. Takes minutes to submit, up to 2 days to clear.
   Nothing gets reviewed before this passes.
2. **Host the privacy policy** so the URL is live:
   `https://dahl404.github.io/PeNNy-937/privacy.html` (already published from the
   `docs/` folder of `Dahl404/PeNNy-937` via GitHub Pages). Test it in a browser
   logged out before pasting it into the console.
3. Check Settings → Developer account → **Users and permissions** — only you, that's fine.

## 2. Create the app

Play Console → All apps → **Create app**.

| Field | Value |
| --- | --- |
| App name | `PeNNy-937` |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free |
| Declarations | tick both (Play policies, US export laws) |

The package name `com.masen.penny937` is taken from the first AAB you upload and can
never be changed. Upload the AAB before you get attached to the name.

## 3. Set up your store listing

Grow → **Store presence → Main store listing**.

- App name `PeNNy-937`, short + full description from `store/listing.md`
- App icon → `store/icon_512.png`
- Feature graphic → `store/feature_1024x500.png`
- Phone screenshots → `store/phone-*.png` (2–8; nothing else required for phones)
- Category: **Art & Design**; tags: animation, drawing
- Contact details: your email (required, public). Website: the GitHub repo.
- Privacy policy: the Pages URL from step 1.

## 4. App content declarations

Dashboard → **Policy → App content**. Every card must be green before review.

| Card | Answer |
| --- | --- |
| Privacy policy | the Pages URL |
| App access | *All functionality is available without special access* |
| Ads | **No ads** |
| Content rating | questionnaire — see below |
| Target audience | **13+** (do not select under-13; that drags you into Families policy) |
| News app | No |
| Data safety | **No data collected, no data shared** — see below |
| Government app | No |
| Financial features | None |
| Health | No health features |
| Photo/video permissions | declares no permissions — answer that the app does not request photo/video access (it uses the system picker) |

**Content rating (IARC).** Category: *Utility, Productivity, Communication or Other*.
Then answer No to every content question (violence, sexuality, language, controlled
substances, gambling, user interaction, sharing location, purchases, unrestricted web
access). Result: everyone / PEGI 3. The app has no internet permission, so the
"unrestricted web access" answer is genuinely No.

**Data safety.** "Does your app collect or share any of the required user data types?"
→ **No**. You then get no further questions and no "encryption in transit" or
"deletion request" questions, because there is nothing to describe. This is the one
place the zero-permission manifest pays off — the app cannot phone home, so the honest
answer is also the simplest one.

## 5. Internal testing (today, ~1 hour)

Testing → **Internal testing** → Create new release → upload
`app-release.aab` → release notes → save → review → **Start rollout**.

- Play App Signing: accept the default (**use Play App Signing**). Google generates the
  app signing key and keeps it; your `.jks` becomes the upload key. Do not opt out.
- Add yourself as a tester, open the opt-in link on the phone, install from Play.
- Install this build and use it for a day. It is signed differently from your
  Taildrop-sideloaded debug APKs: **it will not upgrade over them**. Uninstall the
  sideloaded copy first, which deletes its projects — export the ones you care about
  as zips beforehand.

Fix anything you find here, bump `versionCode`, rebuild, re-upload. Nothing is public yet.

## 6. Closed testing — the 14-day gate (start it today too)

Testing → **Closed testing** → Create track → add the same AAB → **rollout**.

1. Create a tester list with **12+ real Google accounts** (friends, family, your own
   spare accounts — they must actually opt in and install, and stay opted in).
2. Send them the opt-in URL. Each tester: open link → accept → install from Play.
3. Wait **14 days with 12+ testers continuously opted in**. If someone leaves and you
   drop below 12, the clock resets — recruit 14–15 to be safe.
4. Ask them for a real bug report in that window. The production application form asks
   what you learned from testing; vague answers get bounced.

Meanwhile — this is the useful part of the 14 days — collect feedback, and grab better
screenshots from the Play-installed build.

## 7. Apply for production

After 14 days: Dashboard → **Apply for production access**. Questions cover how you
tested, who your testers were, what you changed, and how you'll keep quality up.
Write them honestly; mention the specific things testers broke and what you fixed.

Then Publish overview → **Create release** on the Production track → upload the AAB →
rollout to 100%. Review takes typically 1–7 days for a new personal account. First
submissions get more scrutiny; a drawing app with no permissions and no data collection
is about as easy a review as exists.

---

## What will actually get you rejected

- **No privacy policy URL / dead URL.** Most common personal-account rejection.
- **Applying for production before 14 days** — the button is disabled; don't wait on it.
- **Target audience under 13** without Families compliance.
- **Screenshots that don't show the app.** Play checks; phone-frame marketing mockups
  with a tiny screenshot are fine, fake UI is not.
- **A second account to dodge the gate.** They link accounts and both get terminated.

## Facts worth keeping

- Package: `com.masen.penny937` (permanent) · versionCode `11` · versionName `1.7`
- minSdk 26, targetSdk 37 · no permissions · no network · no ads · no IAP
- AAB: 8.7 MB · upload key: `~/keys/penny-937-upload.jks` (alias `upload`)
- Each new upload needs a higher `versionCode` in `app/build.gradle.kts`.
