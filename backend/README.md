# Facebook Page Manager — backend

Optional FastAPI companion server. The Android app works fully without it;
the backend only handles the two jobs that must not happen on-device:

1. **Token exchange** (`POST /api/auth/exchange`) — trades the short-lived
   Facebook Login token for a long-lived (~60 day) one. Needs the Meta app
   secret, which must never be baked into the APK.
2. **Webhooks** (`GET/POST /api/webhooks`) — verification handshake plus an
   event receiver stub you can extend (log → push notification, cache
   refresh, …).

## Run locally

```bash
cd backend
cp ../.env.example ../.env   # then fill in real values
pip install -r requirements.txt
uvicorn main:app --reload --port 8080
```

Health check: `GET http://localhost:8080/health` → `{"status":"ok"}`.

## Deploy

Any Python host works (Render, Fly.io, Railway, a VPS…). The included
`Dockerfile` listens on `$PORT`. Meta requires a public **HTTPS** URL for
webhooks — set it as the callback URL in your Meta app dashboard
(Products → Webhooks → Page) with the same verify token you put in
`WEBHOOK_VERIFY_TOKEN`.

## Endpoints

| Method | Path                 | Purpose                                            |
|--------|----------------------|----------------------------------------------------|
| GET    | `/health`            | Liveness check                                     |
| POST   | `/api/auth/exchange` | Short-lived → long-lived token (needs app secret)  |
| GET    | `/api/webhooks`      | Meta verification handshake                        |
| POST   | `/api/webhooks`      | Receives page events (currently logs them)         |

Meta's error responses are passed through untouched — the app shows them to
the user with the exact permission/App Review explanation, never a fake
success.
