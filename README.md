# TrueNAS Mobile

A fast, native Android app for managing a TrueNAS SCALE server from your phone.

## What it does

Everything in the TrueNAS web UI's menu, made for a phone:

- **Dashboard**: live CPU (with temperature), memory (services / ZFS cache / free), network and disk throughput, pool health, apps, running jobs.
- **Storage**: pools with vdev/disk topology and error counts, scrubs; datasets with details, edit (quota, compression, sync, atime, dedup, read-only), add dataset or zvol, delete, lock/unlock; snapshots (take, roll back, delete); disks with temperatures.
- **Apps**: start, stop, restart, update, update all, edit settings, delete, roll back, live logs, a shell inside containers. Open web interfaces from the app list, at the address TrueNAS reports or your own (such as an HTTPS address from a reverse proxy). Install from the catalog with an easy form (folder picker for storage, ports with conflict warnings, generated passwords, advanced options tucked away), or from Docker Compose YAML. Choose catalog trains. Automatic updates: pick a schedule and which apps, and your server keeps them up to date on its own, even when your phone is off.
- **Shares**: add, edit and delete SMB and NFS shares.
- **Data protection**: periodic snapshot and scrub schedules (add/edit); run, stop, enable/disable and delete replication, cloud sync, rsync and TrueCloud tasks.
- **Network**: interfaces with addresses and live traffic, hostname/DNS/gateway, static routes.
- **Credentials**: users and groups, API keys, certificates.
- **Virtualization**: start, shut down, restart and power off VMs and containers.
- **Reporting**: CPU, memory, ARC, network, disk and temperature history (1 hour to 1 year).
- **System**: updates, services, shell, boot environments, jobs, audit log, restart/shut down.
- **Alerts**: dismiss/restore, and optional background notifications.
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

After that, the app updates itself: it checks GitHub when it opens and once a day, and offers new versions (Menu → Settings → App updates). It can also install them automatically in the background. Android asks you once to allow installs from TrueNAS Mobile.

## Privacy

Server addresses and credentials stay on the phone, encrypted with a key held in the Android Keystore, and are excluded from backups. The app only talks to your server, plus the TrueNAS app catalog for app icons and GitHub to check for new versions of the app.

## Building

Open in Android Studio, or `./gradlew assembleRelease`. Kotlin, Jetpack Compose, Material 3, OkHttp, Coil, WorkManager. The shell uses [xterm.js](https://xtermjs.org) (MIT).
