# Facebook Page Manager

A **real, native Android app** (Kotlin + Jetpack Compose) for managing
Facebook Pages — publish, schedule, inbox, comments, media library,
analytics, templates, and bulk CSV scheduling.

- **Demo Mode is the default**: the app ships with built-in sample data so
  every screen works out of the box. A persistent banner always shows when
  you're in Demo Mode — it's never disguised as real data.
- **Real Mode uses only the official Meta Graph API** via the official
  Facebook Login SDK. No passwords, no cookies, no scraping, no fake
  responses. When Meta says no (missing permission, App Review not done),
  the app shows Meta's exact error plus a plain-language explanation of
  what's required. See [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md).
- **Scheduling is local-first**: scheduled posts live in the on-device
  database and a WorkManager job publishes each at its due time, then
  notifies you of success or failure with the reason. Editable and
  cancellable any time before publishing.

## Screens

Dashboard · Pages · Create Post · Bulk Upload · Calendar · Inbox ·
Comments · Media Library · Analytics · Templates · Settings

## Quick start

### 1. Prereqs

- JDK 17, Android SDK (API 34, build-tools 34.0.0)
- A Meta Developer app if you want Real Mode: https://developers.facebook.com/apps
  → create app → add **Facebook Login** product → note the **App ID**

### 2. Build

```bash
# Demo Mode works with no further setup:
./gradlew assembleDebug

# For Real Mode, pass your Meta app id:
./gradlew assembleDebug -PFACEBOOK_APP_ID=1234567890
# …or put it in local.properties (never commit this file):
#   facebook_app_id=1234567890
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
Or use the helper: `./scripts/build-apk.sh [APP_ID]` → copies the APK to
`dist/FacebookPageManager-debug.apk`.

### 3. Run

Install on a device/emulator (API 26+). The app opens in **Demo Mode** —
explore everything, then flip to Real Mode in Settings → Mode and connect
with Facebook Login on the Pages tab.

## Real Mode setup (Meta app)

1. Meta app dashboard → **Facebook Login** → Settings → add Android platform:
   package `com.facebookpagemanager.app`, your key hash.
   Debug key hash:
   `keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore | openssl sha1 -binary | openssl base64`
2. Request the permissions you need under **App Review → Permissions and
   Features** (see [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md) for the full
   matrix and what happens without each one).
3. Keep the app in **Development mode** while testing with your own account;
   switch to **Live** after App Review approval.

## Project layout

```
app/src/main/kotlin/com/facebookpagemanager/app/
  auth/        FacebookAuthManager (official Login SDK), TokenStore (encrypted)
  data/
    demo/      DemoSeeder — sample pages/posts/messages/comments/templates
    local/     Room database (10 entities), DAOs
    remote/    Retrofit Graph API + video upload services, DTOs
    repo/      PageManagerRepository — GraphApiRepository (real) / DemoRepository (demo)
  prefs/       ModeManager — demo mode, theme, selected page, FB name (DataStore)
  worker/      ScheduledPostWorker + factory, PostScheduler, notifications
  ui/
    nav/       Routes, NavGraph (drawer + bottom bar + DEMO banner)
    screens/   11 screens, each with its own ViewModel
    theme/     Material 3 theme
    components/ shared UI (loading/error/empty, requirement cards)
    util/      time formatting, ViewModel factory helper
backend/       Optional FastAPI server: token exchange + webhooks (see backend/README.md)
docs/          PERMISSIONS.md — permission & App Review guide
scripts/       build-apk.sh
```

## Environment

Copy `.env.example` to `.env` for the **backend** only. The Android app
takes the Facebook App ID at build time (see above) — never bake the app
secret into the APK; server-side token exchange lives in `backend/`.

## Honest limitations

- Publishing/scheduling/inbox/comments in Real Mode need the Meta
  permissions in `docs/PERMISSIONS.md`, most requiring App Review. Until
  granted, features fail with clear explanations — by design.
- Reels via API have limited availability per app (Meta's call, not ours).
- `pages_messaging` always needs App Review; the Inbox says so up front.
- This project is not affiliated with or endorsed by Meta Platforms, Inc.
