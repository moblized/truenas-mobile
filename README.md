# TrueNAS Mobile

A fast, native Android app for managing a TrueNAS SCALE server from your phone.

## What it does

- **Dashboard**: live CPU (with temperature), memory split into services / ZFS cache / free, network and disk throughput, pool health and capacity, app summary, and a single "everything's healthy" line.
- **Storage**: pools with health, capacity and full vdev/disk topology with read/write/checksum errors; start, pause, resume or stop scrubs. Datasets with usage, and per-dataset snapshots (list, take, delete). Disks with model, serial, pool and live temperatures.
- **Apps**: every installed app with icon, state, live CPU/RAM; start, stop, restart, update (or pull new images for custom apps), "Update all", open the app's web UI, and stream container logs live.
- **Alerts**: active and dismissed alerts by severity; dismiss, restore, dismiss all.
- **Services**: start/stop (SMB, NFS, SSH, UPS…) and toggle start-on-boot.
- **Shares**: turn SMB and NFS shares on or off.
- **Power**: restart or shut down, with a reason for the audit log.
- **Background alerts** (optional): checks about every 15 minutes and notifies you about new warnings or critical alerts.
- Several servers, light/dark/pure-black themes, Material You colours, phone and tablet layouts.

## Requirements

- TrueNAS SCALE **25.04 or newer** (including 25.10 and 26). The app uses the JSON-RPC 2.0 WebSocket API at `/api/current`, the API TrueNAS keeps going forward (REST was removed in TrueNAS 26).
- Android 8.0 or newer.

## Signing in

Use an **API key** (recommended) or a username and password (two-factor codes supported).

To create a key: TrueNAS web UI → account icon (top right) → **API Keys** → **Add**. Copy it right away; it's only shown once. Enter the username the key belongs to (often `truenas_admin`).

The app connects with HTTPS by default. TrueNAS ships with a self-signed certificate, so the first time you connect the app shows its SHA-256 fingerprint and asks whether to trust it; compare it with **Credentials → Certificates** in the web UI. You're only asked again if the certificate changes.

> TrueNAS revokes an API key if it's ever sent over plain `http://`, so the app refuses to do that.

## Away from home

The app talks directly to your server. To use it outside your network, connect your phone with a VPN such as Tailscale or WireGuard and use the server's VPN address.

## Install

Every push to `main` builds a signed APK and publishes it under **Releases**. On your phone, download the latest `truenas-mobile-*.apk` and open it. New versions install over old ones and keep your saved servers.

## Privacy

Server addresses and credentials stay on the phone, encrypted with a key held in the Android Keystore, and are excluded from backups. The app only talks to your server, plus the TrueNAS app catalog for app icons.

## Building

Open in Android Studio, or `./gradlew assembleRelease`. Kotlin, Jetpack Compose, Material 3, OkHttp, Coil, WorkManager.
