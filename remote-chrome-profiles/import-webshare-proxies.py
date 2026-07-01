#!/usr/bin/env python3
import argparse
import csv
import io
import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.parse import quote as urlquote
from urllib.parse import urlsplit
from urllib.request import Request
from urllib.request import urlopen


PROFILE_SETTINGS_KEYS = {
    "VNC_DISPLAY",
    "INCOGNITO_MODE",
    "INCOGNITO_DOMAINS",
    "AUTO_CLICK_DOMAINS",
    "AUTO_CLICK_DELAY_SECONDS",
    "START_URL",
    "WINDOW_SIZE",
}


def shell_quote(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def parse_proxy_line(line: str) -> str | None:
    value = line.strip()
    if not value or value.startswith("#"):
        return None

    if "://" in value:
        parsed = urlsplit(value)
        if parsed.hostname and parsed.port:
            return normalize_proxy(
                parsed.scheme or "http",
                parsed.hostname,
                str(parsed.port),
                parsed.username or "",
                parsed.password or "",
            )
        return None

    if "@" in value:
        return parse_proxy_line("http://" + value)

    parts = value.split(":")
    if len(parts) == 2:
        host, port = parts
        return normalize_proxy("http", host, port, "", "")
    if len(parts) >= 4:
        host, port, username = parts[0], parts[1], parts[2]
        password = ":".join(parts[3:])
        return normalize_proxy("http", host, port, username, password)

    return None


def normalize_proxy(scheme: str, host: str, port: str, username: str, password: str) -> str | None:
    scheme = (scheme or "http").strip().lower()
    host = host.strip()
    port = port.strip()
    username = username.strip()
    password = password.strip()

    if scheme not in {"http", "https", "socks5"} or not host or not port.isdigit():
        return None

    if username or password:
        auth = f"{urlquote(username, safe='')}:{urlquote(password, safe='')}@"
    else:
        auth = ""
    return f"{scheme}://{auth}{host}:{port}"


def parse_csv(text: str) -> list[str]:
    sample = text[:4096]
    try:
        dialect = csv.Sniffer().sniff(sample, delimiters=",;\t")
        has_header = csv.Sniffer().has_header(sample)
    except csv.Error:
        dialect = csv.excel
        has_header = False

    if not has_header:
        return []

    rows = csv.DictReader(io.StringIO(text), dialect=dialect)
    proxies: list[str] = []
    for row in rows:
        normalized = {normalize_header(key): (value or "").strip() for key, value in row.items() if key}
        host = first_present(normalized, "proxy_address", "proxyaddress", "address", "host", "ip")
        port = first_present(normalized, "port", "proxy_port", "proxyport")
        username = first_present(normalized, "username", "user", "login")
        password = first_present(normalized, "password", "pass")
        scheme = first_present(normalized, "scheme", "type", "protocol") or "http"
        proxy = normalize_proxy(scheme, host, port, username, password)
        if proxy:
            proxies.append(proxy)
    return proxies


def normalize_header(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.strip().lower()).strip("_")


def first_present(row: dict[str, str], *keys: str) -> str:
    for key in keys:
        value = row.get(key, "")
        if value:
            return value
    return ""


def parse_proxy_file(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8-sig")
    proxies = parse_csv(text)
    if not proxies:
        proxies = [proxy for proxy in (parse_proxy_line(line) for line in text.splitlines()) if proxy]

    seen: set[str] = set()
    unique: list[str] = []
    for proxy in proxies:
        target = proxy_target(proxy)
        if not target or target in seen:
            continue
        seen.add(target)
        unique.append(proxy)
    return unique


def fetch_webshare_proxies(api_token: str, mode: str, page_size: int, include_invalid: bool) -> list[str]:
    proxies: list[str] = []
    page = 1
    while True:
        params = {
            "mode": mode,
            "page": str(page),
            "page_size": str(page_size),
        }
        if mode == "direct" and not include_invalid:
            params["valid"] = "true"

        url = "https://proxy.webshare.io/api/v2/proxy/list/?" + urlencode(params)
        request = Request(url, headers={"Authorization": f"Token {api_token}"})
        try:
            with urlopen(request, timeout=30) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except HTTPError as error:
            message = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"WebShare API returned HTTP {error.code}: {message}") from error
        except URLError as error:
            raise RuntimeError(f"WebShare API request failed: {error}") from error

        for item in payload.get("results", []):
            if not include_invalid and item.get("valid") is False:
                continue
            host = "p.webshare.io" if mode == "backbone" else str(item.get("proxy_address") or "")
            proxy = normalize_proxy(
                "http",
                host,
                str(item.get("port") or ""),
                str(item.get("username") or ""),
                str(item.get("password") or ""),
            )
            if proxy:
                proxies.append(proxy)

        if not payload.get("next"):
            break
        page += 1

    return proxies


def proxy_target(proxy: str) -> str:
    parsed = urlsplit(proxy)
    if not parsed.hostname:
        return ""
    try:
        port = parsed.port
    except ValueError:
        return ""
    return f"{parsed.hostname}:{port}" if port else ""


def read_existing_settings(env_path: Path) -> dict[str, str]:
    settings: dict[str, str] = {}
    if not env_path.exists():
        return settings

    for line in env_path.read_text(encoding="utf-8-sig").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        key = key.strip()
        if key in PROFILE_SETTINGS_KEYS:
            settings[key] = unquote(value.strip())
    if settings.get("INCOGNITO_DOMAINS") == "youtube.com youtu.be":
        settings["INCOGNITO_DOMAINS"] = ""
    return settings


def read_existing_proxy_targets(env_path: Path) -> list[str]:
    values: dict[str, str] = {}
    profile_names: list[str] = []
    if not env_path.exists():
        return []

    for line in env_path.read_text(encoding="utf-8-sig").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        key = key.strip()
        clean_value = unquote(value.strip())
        if key == "PROFILE_NAMES":
            profile_names = clean_value.split()
        elif key.startswith("UPSTREAM_PROXY_"):
            values[key.removeprefix("UPSTREAM_PROXY_")] = clean_value

    ordered_profiles = profile_names or sorted(values.keys(), key=profile_sort_key)
    targets: list[str] = []
    seen: set[str] = set()
    for profile in ordered_profiles:
        target = proxy_target(values.get(profile, ""))
        if target and target not in seen:
            targets.append(target)
            seen.add(target)
    return targets


def profile_sort_key(name: str) -> tuple[int, str]:
    match = re.fullmatch(r"ip(\d+)", name)
    if match:
        return int(match.group(1)), name
    return sys.maxsize, name


def preserve_existing_proxy_order(proxies: list[str], existing_targets: list[str]) -> list[str]:
    fresh_by_target: dict[str, str] = {}
    fresh_order: list[str] = []
    for proxy in proxies:
        target = proxy_target(proxy)
        if not target or target in fresh_by_target:
            continue
        fresh_by_target[target] = proxy
        fresh_order.append(target)

    ordered: list[str] = []
    used: set[str] = set()
    for target in existing_targets:
        proxy = fresh_by_target.get(target)
        if proxy and target not in used:
            ordered.append(proxy)
            used.add(target)

    for target in fresh_order:
        if target not in used:
            ordered.append(fresh_by_target[target])
    return ordered


def unquote(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def write_profiles_env(env_path: Path, proxies: list[str], start_port: int, settings: dict[str, str]) -> None:
    lines: list[str] = [
        "# Generated by import-webshare-proxies.py.",
        "# Edit the source proxy export, then re-run the importer instead of editing profile rows by hand.",
        "",
    ]
    names = [f"ip{index}" for index in range(1, len(proxies) + 1)]
    lines.append(f"PROFILE_NAMES={shell_quote(' '.join(names))}")
    lines.append("")

    for index, name in enumerate(names, start=1):
        lines.append(f"PROFILE_LABEL_{name}={shell_quote(f'{index} - {name}')}")
    lines.append("")

    for index, name in enumerate(names, start=1):
        lines.append(f"PROXY_{name}={shell_quote(f'http://127.0.0.1:{start_port + index}')}")
    lines.append("")

    for name, proxy in zip(names, proxies):
        lines.append(f"UPSTREAM_PROXY_{name}={shell_quote(proxy)}")
    lines.append("")

    default_settings = {
        "VNC_DISPLAY": ":1",
        "INCOGNITO_MODE": "false",
        "INCOGNITO_DOMAINS": "",
        "AUTO_CLICK_DOMAINS": "pornhub.com",
        "AUTO_CLICK_DELAY_SECONDS": "8",
        "START_URL": "profile-home",
        "WINDOW_SIZE": "1000,760",
    }
    default_settings.update(settings)
    for key, value in default_settings.items():
        lines.append(f"{key}={shell_quote(value)}")

    lines.append("")
    env_path.write_text("\n".join(lines), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate profiles.env from a WebShare proxy export.")
    parser.add_argument("proxy_file", nargs="?", help="WebShare export as CSV or one proxy per line")
    parser.add_argument("--env", default="profiles.env", help="Output profiles.env path")
    parser.add_argument("--start-port", type=int, default=11000, help="Base local proxy port; ip1 uses start-port + 1")
    parser.add_argument("--from-webshare-api", action="store_true", help="Fetch current proxies from WebShare API")
    parser.add_argument("--api-token", default="", help="WebShare API token; defaults to WEBSHARE_API_TOKEN")
    parser.add_argument("--mode", choices=("direct", "backbone"), default="direct", help="WebShare proxy list mode")
    parser.add_argument("--page-size", type=int, default=100, help="WebShare API page size")
    parser.add_argument("--include-invalid", action="store_true", help="Include proxies WebShare currently marks invalid")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    env_path = Path(args.env)

    if args.from_webshare_api:
        api_token = args.api_token or os.getenv("WEBSHARE_API_TOKEN", "")
        if not api_token:
            print("Missing WebShare API token. Set WEBSHARE_API_TOKEN or pass --api-token.", file=sys.stderr)
            return 1
        proxies = fetch_webshare_proxies(api_token, args.mode, args.page_size, args.include_invalid)
    else:
        if not args.proxy_file:
            print("Missing proxy file.", file=sys.stderr)
            return 1
        proxy_file = Path(args.proxy_file)
        if not proxy_file.is_file():
            print(f"Missing proxy file: {proxy_file}")
            return 1
        proxies = parse_proxy_file(proxy_file)

    if not proxies:
        print("No usable proxies found.")
        return 1

    settings = read_existing_settings(env_path)
    proxies = preserve_existing_proxy_order(proxies, read_existing_proxy_targets(env_path))
    env_path.parent.mkdir(parents=True, exist_ok=True)
    write_profiles_env(env_path, proxies, args.start_port, settings)
    print(f"Wrote {len(proxies)} proxy profile(s) to {env_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
