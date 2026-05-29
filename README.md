# Clash of Frames

Dependency-free local/LAN multiplayer web game. One Node.js process serves the frontend, JSON APIs, and Server-Sent Events.
**Created with good vibes**

## Run Locally

```powershell
node server.js
```

Open `http://localhost:3000`.

## Run On A Server

```powershell
$env:HOST="0.0.0.0"
$env:PORT="3000"
node server.js
```

Then connect from another device with `http://SERVER_IP:3000`. Make sure the server firewall allows the selected port.

For public internet deployment, put the app behind an HTTPS reverse proxy. Login tokens are stored in the browser and should not be sent over plain HTTP on an untrusted network.

## Accounts

Users must register a username and password before entering the game. There is no password recovery flow in the app; if a user forgets the password, they must contact an administrator.

Passwords are stored in `data/state.json` as PBKDF2 salted hashes. To reset a user password, an administrator can edit that user's `passwordHash` field to exactly:

```json
"123456"
```

On the next login, any entered password is accepted once and immediately becomes the user's new salted, hashed password.

## Data And Cache

Persistent local data lives in `data/state.json`; back it up before moving or upgrading the server.

The bootstrap API only sends card-library metadata. The loading screen fetches the selected room's asset manifest and preloads card backs, card faces, the bell, and the table logo. Card assets are served with long-lived browser cache headers so the same selected card groups do not need to download again.
