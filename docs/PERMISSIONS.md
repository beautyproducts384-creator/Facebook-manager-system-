# Meta permissions & App Review — what this app needs and why

This app uses **only the official Meta Graph API** — no passwords, no cookies,
no scraping, nothing faked. Every feature that touches Facebook data declares
the permission below. If a permission is missing or not yet approved, the app
shows you the exact Meta error plus an explanation of what's required — it
never pretends the action succeeded.

> **Demo Mode (default)** needs none of these. It runs on built-in sample
> data so you can explore every screen safely. Switch to Real Mode in
> Settings → Mode when you're ready to connect a real Page.

## Permission matrix

| Permission | Used for | Without it |
|---|---|---|
| `public_profile` | Basic Facebook Login (name shown after connect) | Login itself fails; granted automatically with Login |
| `pages_show_list` | Listing the Pages you manage (Pages tab) | "No Pages found" / empty list; the app explains you must grant it |
| `pages_read_engagement` | Followers, reach, post insights (Dashboard, Analytics) | Stats show as unavailable with a "limited" note; everything else works |
| `pages_manage_posts` | Publishing & scheduling text/photo/video posts and Reels | Publish/schedule fails with Meta's `(#200)` permission error, shown verbatim with this explanation |
| `pages_read_user_content` | Reading post comments (Comments tab) | Comments list fails with an honest permission error |
| `pages_manage_engagement` | Replying to / hiding comments | Reply & hide buttons report the exact missing permission |
| `pages_messaging` | Reading and replying to Page conversations (Inbox) | Inbox shows Meta's error; **this permission always needs App Review** (see below) |
| `read_insights` | Page & post insights beyond basic engagement | Analytics cards show "limited" instead of numbers |

## App Review, honestly

- Permissions in the table above (except `public_profile` and
  `pages_show_list`) require **Meta App Review** before they work for anyone
  except developers/testers of your Meta app. In **Development mode** only
  users with a role in your Meta app can log in.
- To go public: Meta app dashboard → **App Review → Permissions and Features**
  → request each permission → submit a screencast and written justification
  showing exactly how your app uses it. Review typically takes a few days;
  Meta may ask for changes.
- **Business verification** may additionally be required for
  `pages_messaging` and for publishing as a business.

## What "limited" means in this app

When an insights permission is missing, Dashboard/Analytics show the cards
they *can* fill and mark the rest "limited" with a note naming the missing
permission — instead of showing zeros or fake numbers.

## Reels

Reels publishing uses the official `video_reels` upload flow
(start → upload → finish). Meta enables Reels API access per app; if your
app isn't enabled, publishing a Reel fails with Meta's error, which the app
shows verbatim with this explanation. Nothing is silently downgraded.

## Scheduling design

Scheduling is **local-first**: your scheduled posts live in the on-device
database and a WorkManager job publishes each one at its due time through
the Graph API, then notifies you of success or failure (with the reason).
This is deliberate — it makes scheduled posts reliably editable and
cancellable, and every failure is reported accurately.
