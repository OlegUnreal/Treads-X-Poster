#!/usr/bin/env python3
import argparse
import re
from pathlib import Path
from urllib.parse import urlsplit


PROFILE_RE = re.compile(r'^PROFILE_NAMES=(["\']?)(.*?)\1$')
PROXY_RE = re.compile(r'^PROXY_(ip\d+)=')
UPSTREAM_RE = re.compile(r'^UPSTREAM_PROXY_(ip\d+)=(["\']?)(.*?)\2$')


def unquote(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
        return value[1:-1]
    return value


def quote(value: str) -> str:
    return '"' + value.replace('"', '\\"') + '"'


def upstream_target(value: str) -> str:
    parsed = urlsplit(value)
    if not parsed.hostname:
        return ""
    try:
        port = parsed.port
    except ValueError:
        return ""
    return f"{parsed.hostname}:{port}" if port else ""


def has_credentials(value: str) -> bool:
    parsed = urlsplit(value)
    return bool(parsed.scheme and parsed.username and parsed.password and upstream_target(value))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Append proxy Chrome profiles without storing credentials in git.")
    parser.add_argument("--env", default="profiles.env", help="Path to profiles.env")
    parser.add_argument("targets", nargs="+", help="Proxy targets as host:port")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    env_path = Path(args.env)
    if not env_path.exists():
        print(f"Missing {env_path}. Nothing to update.")
        return 0

    lines = env_path.read_text(encoding="utf-8").splitlines()
    profile_names: list[str] = []
    profile_line_index = None
    upstream_values: dict[str, str] = {}
    existing_targets: set[str] = set()
    existing_numbers: list[int] = []

    for index, line in enumerate(lines):
        stripped = line.strip().lstrip("\ufeff")
        profile_match = PROFILE_RE.match(stripped)
        if profile_match:
            profile_line_index = index
            profile_names = [name for name in profile_match.group(2).split() if name]

        proxy_match = PROXY_RE.match(stripped)
        if proxy_match:
            number = int(proxy_match.group(1).removeprefix("ip"))
            existing_numbers.append(number)

        upstream_match = UPSTREAM_RE.match(stripped)
        if upstream_match:
            name = upstream_match.group(1)
            value = unquote(upstream_match.group(3))
            upstream_values[name] = value
            target = upstream_target(value)
            if target:
                existing_targets.add(target)

    if not profile_names:
        print("PROFILE_NAMES is missing or empty. Nothing to update.")
        return 0

    credential_source = next((value for value in upstream_values.values() if has_credentials(value)), "")
    if not credential_source:
        print("No authenticated UPSTREAM_PROXY value found. Nothing to update.")
        return 0

    parsed_source = urlsplit(credential_source)
    credential_prefix = f"{parsed_source.scheme}://{parsed_source.username}:{parsed_source.password}@"
    next_number = max(existing_numbers or [0]) + 1
    added: list[str] = []

    for raw_target in args.targets:
        target = raw_target.strip()
        if not target or target in existing_targets:
            continue
        if ":" not in target:
            print(f"Skipping invalid target: {target}")
            continue

        name = f"ip{next_number}"
        local_port = 11000 + next_number
        profile_names.append(name)
        lines.append("")
        lines.append(f'PROXY_{name}={quote(f"http://127.0.0.1:{local_port}")}')
        lines.append(f'UPSTREAM_PROXY_{name}={quote(credential_prefix + target)}')
        added.append(f"{name} -> {target}")
        existing_targets.add(target)
        next_number += 1

    if not added:
        print("No new proxies to add.")
        return 0

    if profile_line_index is not None:
        lines[profile_line_index] = f'PROFILE_NAMES={quote(" ".join(profile_names))}'

    env_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("Added proxy profiles:")
    for item in added:
        print(f"- {item}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
