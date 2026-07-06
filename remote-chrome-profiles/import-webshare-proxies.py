#!/usr/bin/env python3
import argparse
import csv
import io
import json
import os
import re
import sys
from dataclasses import dataclass
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
    "LANGUAGE",
    "ACCEPT_LANGUAGE",
    "TIMEZONE",
    "USER_AGENT",
    "AUTO_BROWSER_PROFILE",
}
PROFILE_SETTINGS_PREFIXES = (
    "WINDOW_POSITION_",
    "WINDOW_SIZE_",
    "LANGUAGE_",
    "ACCEPT_LANGUAGE_",
    "TIMEZONE_",
    "USER_AGENT_",
)
AUTO_BROWSER_PROFILE_PREFIXES = (
    "WINDOW_SIZE_",
    "LANGUAGE_",
    "ACCEPT_LANGUAGE_",
    "TIMEZONE_",
)


@dataclass(frozen=True)
class ProxyRecord:
    proxy: str
    country_code: str = ""
    city_name: str = ""


COUNTRY_BROWSER_PROFILES = {
    "US": ("en-US", "en-US,en;q=0.9", "America/New_York"),
    "GB": ("en-GB", "en-GB,en;q=0.9", "Europe/London"),
    "IE": ("en-IE", "en-IE,en;q=0.9", "Europe/Dublin"),
    "CA": ("en-CA", "en-CA,en;q=0.9,fr-CA;q=0.7", "America/Toronto"),
    "AU": ("en-AU", "en-AU,en;q=0.9", "Australia/Sydney"),
    "NZ": ("en-NZ", "en-NZ,en;q=0.9", "Pacific/Auckland"),
    "ES": ("es-ES", "es-ES,es;q=0.9,en;q=0.7", "Europe/Madrid"),
    "MX": ("es-MX", "es-MX,es;q=0.9,en;q=0.7", "America/Mexico_City"),
    "AR": ("es-AR", "es-AR,es;q=0.9,en;q=0.7", "America/Argentina/Buenos_Aires"),
    "CL": ("es-CL", "es-CL,es;q=0.9,en;q=0.7", "America/Santiago"),
    "CO": ("es-CO", "es-CO,es;q=0.9,en;q=0.7", "America/Bogota"),
    "DE": ("de-DE", "de-DE,de;q=0.9,en;q=0.7", "Europe/Berlin"),
    "AT": ("de-AT", "de-AT,de;q=0.9,en;q=0.7", "Europe/Vienna"),
    "CH": ("de-CH", "de-CH,de;q=0.9,fr;q=0.7,en;q=0.6", "Europe/Zurich"),
    "FR": ("fr-FR", "fr-FR,fr;q=0.9,en;q=0.7", "Europe/Paris"),
    "BE": ("nl-BE", "nl-BE,nl;q=0.9,fr;q=0.8,en;q=0.6", "Europe/Brussels"),
    "NL": ("nl-NL", "nl-NL,nl;q=0.9,en;q=0.7", "Europe/Amsterdam"),
    "IT": ("it-IT", "it-IT,it;q=0.9,en;q=0.7", "Europe/Rome"),
    "PT": ("pt-PT", "pt-PT,pt;q=0.9,en;q=0.7", "Europe/Lisbon"),
    "BR": ("pt-BR", "pt-BR,pt;q=0.9,en;q=0.7", "America/Sao_Paulo"),
    "PL": ("pl-PL", "pl-PL,pl;q=0.9,en;q=0.7", "Europe/Warsaw"),
    "CZ": ("cs-CZ", "cs-CZ,cs;q=0.9,en;q=0.7", "Europe/Prague"),
    "RO": ("ro-RO", "ro-RO,ro;q=0.9,en;q=0.7", "Europe/Bucharest"),
    "UA": ("uk-UA", "uk-UA,uk;q=0.9,en;q=0.7", "Europe/Kyiv"),
    "SE": ("sv-SE", "sv-SE,sv;q=0.9,en;q=0.7", "Europe/Stockholm"),
    "NO": ("nb-NO", "nb-NO,nb;q=0.9,no;q=0.8,en;q=0.7", "Europe/Oslo"),
    "DK": ("da-DK", "da-DK,da;q=0.9,en;q=0.7", "Europe/Copenhagen"),
    "FI": ("fi-FI", "fi-FI,fi;q=0.9,en;q=0.7", "Europe/Helsinki"),
    "JP": ("ja-JP", "ja-JP,ja;q=0.9,en;q=0.7", "Asia/Tokyo"),
    "KR": ("ko-KR", "ko-KR,ko;q=0.9,en;q=0.7", "Asia/Seoul"),
    "SG": ("en-SG", "en-SG,en;q=0.9", "Asia/Singapore"),
    "IN": ("en-IN", "en-IN,en;q=0.9,hi;q=0.7", "Asia/Kolkata"),
}

WINDOW_SIZE_ROTATION = (
    "1366,768",
    "1440,900",
    "1536,864",
    "1600,900",
    "1280,800",
    "1680,1050",
    "1920,1080",
)


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


def parse_csv(text: str) -> list[ProxyRecord]:
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
    proxies: list[ProxyRecord] = []
    for row in rows:
        normalized = {normalize_header(key): (value or "").strip() for key, value in row.items() if key}
        host = first_present(normalized, "proxy_address", "proxyaddress", "address", "host", "ip")
        port = first_present(normalized, "port", "proxy_port", "proxyport")
        username = first_present(normalized, "username", "user", "login")
        password = first_present(normalized, "password", "pass")
        scheme = first_present(normalized, "scheme", "type", "protocol") or "http"
        proxy = normalize_proxy(scheme, host, port, username, password)
        if proxy:
            proxies.append(ProxyRecord(
                proxy=proxy,
                country_code=normalize_country_code(first_present(normalized, "country_code", "countrycode", "country", "code")),
                city_name=first_present(normalized, "city_name", "city", "location"),
            ))
    return proxies


def normalize_header(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.strip().lower()).strip("_")


def first_present(row: dict[str, str], *keys: str) -> str:
    for key in keys:
        value = row.get(key, "")
        if value:
            return value
    return ""


def normalize_country_code(value: str) -> str:
    cleaned = re.sub(r"[^A-Za-z]", "", value or "").upper()
    return cleaned if len(cleaned) == 2 else ""


def parse_proxy_file(path: Path) -> list[ProxyRecord]:
    text = path.read_text(encoding="utf-8-sig")
    proxies = parse_csv(text)
    if not proxies:
        proxies = [ProxyRecord(proxy=proxy) for proxy in (parse_proxy_line(line) for line in text.splitlines()) if proxy]

    seen: set[str] = set()
    unique: list[ProxyRecord] = []
    for record in proxies:
        target = proxy_target(record.proxy)
        if not target or target in seen:
            continue
        seen.add(target)
        unique.append(record)
    return unique


def fetch_webshare_proxies(api_token: str, mode: str, page_size: int, include_invalid: bool) -> list[ProxyRecord]:
    proxies: list[ProxyRecord] = []
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
                proxies.append(ProxyRecord(
                    proxy=proxy,
                    country_code=normalize_country_code(str(item.get("country_code") or "")),
                    city_name=str(item.get("city_name") or ""),
                ))

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
        if key in PROFILE_SETTINGS_KEYS or key.startswith(PROFILE_SETTINGS_PREFIXES):
            settings[key] = unquote(value.strip())
    if settings.get("INCOGNITO_DOMAINS"):
        domains = [
            domain
            for domain in settings["INCOGNITO_DOMAINS"].split()
            if domain not in {"youtube.com", "youtu.be"}
        ]
        settings["INCOGNITO_DOMAINS"] = " ".join(domains)
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


def preserve_existing_proxy_order(proxies: list[ProxyRecord], existing_targets: list[str]) -> list[ProxyRecord]:
    fresh_by_target: dict[str, ProxyRecord] = {}
    fresh_order: list[str] = []
    for record in proxies:
        target = proxy_target(record.proxy)
        if not target or target in fresh_by_target:
            continue
        fresh_by_target[target] = record
        fresh_order.append(target)

    ordered: list[ProxyRecord] = []
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


def truthy(value: str) -> bool:
    return value.strip().lower() not in {"0", "false", "no", "off"}


def browser_profile_for_country(country_code: str, index: int) -> dict[str, str]:
    language, accept_language, timezone = COUNTRY_BROWSER_PROFILES.get(
        country_code.upper(),
        ("en-US", "en-US,en;q=0.9", "UTC"),
    )
    return {
        "LANGUAGE": language,
        "ACCEPT_LANGUAGE": accept_language,
        "TIMEZONE": timezone,
        "WINDOW_SIZE": WINDOW_SIZE_ROTATION[(index - 1) % len(WINDOW_SIZE_ROTATION)],
    }


def write_profiles_env(env_path: Path, proxies: list[ProxyRecord], start_port: int, settings: dict[str, str]) -> None:
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

    for name, record in zip(names, proxies):
        lines.append(f"UPSTREAM_PROXY_{name}={shell_quote(record.proxy)}")
        if record.country_code:
            lines.append(f"PROXY_COUNTRY_{name}={shell_quote(record.country_code)}")
        if record.city_name:
            lines.append(f"PROXY_CITY_{name}={shell_quote(record.city_name)}")
    lines.append("")

    default_settings = {
        "VNC_DISPLAY": ":1",
        "INCOGNITO_MODE": "false",
        "INCOGNITO_DOMAINS": "",
        "AUTO_CLICK_DOMAINS": "pornhub.com",
        "AUTO_CLICK_DELAY_SECONDS": "8",
        "START_URL": "profile-home",
        "WINDOW_SIZE": "1000,760",
        "LANGUAGE": "",
        "ACCEPT_LANGUAGE": "",
        "TIMEZONE": "",
        "USER_AGENT": "",
        "AUTO_BROWSER_PROFILE": "true",
    }
    auto_browser_profile = truthy(settings.get("AUTO_BROWSER_PROFILE", "true"))
    preserved_settings = {
        key: value
        for key, value in settings.items()
        if not (auto_browser_profile and key.startswith(AUTO_BROWSER_PROFILE_PREFIXES))
    }
    default_settings.update(preserved_settings)
    for key, value in default_settings.items():
        lines.append(f"{key}={shell_quote(value)}")

    if auto_browser_profile:
        lines.append("")
        lines.append("# Auto-generated from proxy country. Set AUTO_BROWSER_PROFILE=\"false\" to manage these manually.")
        for index, (name, record) in enumerate(zip(names, proxies), start=1):
            generated = browser_profile_for_country(record.country_code, index)
            for key, value in generated.items():
                lines.append(f"{key}_{name}={shell_quote(value)}")
            lines.append("")

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
