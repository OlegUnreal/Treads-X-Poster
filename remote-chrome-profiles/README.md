# Remote Chrome Profiles

Scripts for running separate Chrome/Chromium profiles on an Ubuntu server, each with its own proxy and visible browser window.

## Files

- `profiles.env.example` - copy to `profiles.env` and fill proxy values.
- `import-webshare-proxies.py` - generate `profiles.env` from a WebShare proxy export.
- `start-profile.sh` - start one Chrome profile by name.
- `start-all.sh` - start all enabled profiles from `profiles.env`, with optional staggered delay.
- `check-deps.sh` - check whether Chrome/Chromium and display variables are available.

## Quick Start

```bash
cd ~/chrome-proxy-profiles
cp profiles.env.example profiles.env
nano profiles.env
./check-deps.sh
./start-profile.sh ip1
```

Start all profiles with a controlled delay between windows:

```bash
STAGGER_MIN_SECONDS=5 STAGGER_MAX_SECONDS=45 ./start-all.sh
```

Keep `profiles.env` on the server only and protect it:

```bash
chmod 600 profiles.env
```

## WebShare proxy sync

Preferred production flow: add `WEBSHARE_API_TOKEN` to the production Doppler config. During deploy, the server fetches the current WebShare proxy list and regenerates `profiles.env`.

Manual fallback: put a WebShare export on the server as:

```text
~/chrome-proxy-profiles/webshare-proxies.txt
```

Supported formats:

```text
host:port:username:password
username:password@host:port
http://username:password@host:port
```

CSV exports are also supported when they include recognizable columns such as `proxy_address`, `port`, `username`, and `password`.

Generate `profiles.env`:

```bash
cd ~/chrome-proxy-profiles
python3 import-webshare-proxies.py webshare-proxies.txt --env profiles.env
chmod 600 profiles.env webshare-proxies.txt
```

Fetch directly from WebShare API:

```bash
WEBSHARE_API_TOKEN=... python3 import-webshare-proxies.py --from-webshare-api --env profiles.env
```

When `WEBSHARE_API_TOKEN` is available from Doppler, production deploy uses the API. If no API token is configured but `webshare-proxies.txt` exists, deploy uses the file fallback.

Avoid launching 100 Chrome windows at once. For video sites, start a small batch and use a wide stagger:

```bash
PROFILE_LIMIT=3 STAGGER_MIN_SECONDS=45 STAGGER_MAX_SECONDS=180 ./start-all.sh
```

Each profile uses a separate user data directory under:

```text
~/chrome-proxy-profiles/data/<profile-name>
```

Check leaks from each browser window:

```text
https://browserleaks.com/ip
https://browserleaks.com/webrtc
https://ipleak.net
```
