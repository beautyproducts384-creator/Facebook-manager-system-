"""
Facebook Page Manager — optional companion backend (FastAPI).

This server is NOT required for the Android app to work. It exists for the
two things that should never happen on-device:

1. Exchanging a short-lived Facebook user token for a long-lived one
   (needs the app SECRET, which must never ship inside the APK).
2. Receiving Meta webhook events (page feed changes, etc.).

Run locally:
    pip install -r requirements.txt
    uvicorn main:app --reload --port 8080

Configure with a `.env` file (see ../.env.example).
"""

import logging
import os

import httpx
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

log = logging.getLogger("fpm-backend")

APP_ID = os.environ.get("FACEBOOK_APP_ID", "")
APP_SECRET = os.environ.get("FACEBOOK_APP_SECRET", "")
VERIFY_TOKEN = os.environ.get("WEBHOOK_VERIFY_TOKEN", "")
ALLOWED_ORIGINS = [
    o.strip() for o in os.environ.get("ALLOWED_ORIGINS", "").split(",") if o.strip()
]

GRAPH = "https://graph.facebook.com/v21.0"

app = FastAPI(title="Facebook Page Manager backend")

if ALLOWED_ORIGINS:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=ALLOWED_ORIGINS,
        allow_methods=["GET", "POST"],
        allow_headers=["*"],
    )


@app.get("/health")
async def health():
    """Liveness check."""
    return {"status": "ok"}


class ExchangeRequest(BaseModel):
    short_lived_token: str


@app.post("/api/auth/exchange")
async def exchange_token(body: ExchangeRequest):
    """
    Exchange a short-lived user access token for a long-lived (~60 day) one.

    The Android app obtains the short-lived token via the official Facebook
    Login SDK, then POSTs it here. The app secret never leaves the server.
    """
    if not APP_ID or not APP_SECRET:
        raise HTTPException(
            status_code=500,
            detail="Backend is not configured: FACEBOOK_APP_ID / FACEBOOK_APP_SECRET are missing.",
        )
    if not body.short_lived_token:
        raise HTTPException(status_code=400, detail="short_lived_token is required.")

    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.get(
            f"{GRAPH}/oauth/access_token",
            params={
                "grant_type": "fb_exchange_token",
                "client_id": APP_ID,
                "client_secret": APP_SECRET,
                "fb_exchange_token": body.short_lived_token,
            },
        )
    if resp.status_code != 200:
        # Pass Meta's error through untouched — the app surfaces it honestly.
        raise HTTPException(status_code=resp.status_code, detail=resp.text)
    return resp.json()


@app.get("/api/webhooks")
async def verify_webhook(
    hub_mode: str = Query(default="", alias="hub.mode"),
    hub_challenge: str = Query(default="", alias="hub.challenge"),
    hub_verify_token: str = Query(default="", alias="hub.verify_token"),
):
    """Meta webhook verification handshake (configure the callback URL in the
    Meta app dashboard: https://<host>/api/webhooks)."""
    if hub_mode == "subscribe" and hub_verify_token == VERIFY_TOKEN and VERIFY_TOKEN:
        return int(hub_challenge)
    raise HTTPException(status_code=403, detail="Webhook verification failed.")


@app.post("/api/webhooks")
async def receive_webhook(request: Request):
    """
    Receives Meta webhook events. Currently logs them; extend this to push
    notifications or refresh cached data as needed.
    """
    try:
        payload = await request.json()
    except Exception:
        payload = {}
    log.warning("Webhook event received: %s", payload)
    return {"received": True}
