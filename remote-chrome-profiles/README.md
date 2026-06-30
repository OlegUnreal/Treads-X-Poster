# Remote Chrome Profiles

Scripts for running separate Chrome/Chromium profiles on an Ubuntu server, each with its own proxy and visible browser window.

## Files

- `profiles.env.example` - copy to `profiles.env` and fill proxy values.
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
