#!/usr/bin/env python3
import base64
import select
import socket
import socketserver
import sys
import threading
from dataclasses import dataclass
from urllib.parse import urlsplit


BUFFER_SIZE = 65536


@dataclass(frozen=True)
class Upstream:
    host: str
    port: int
    username: str
    password: str

    @property
    def auth_header(self) -> bytes:
        raw = f"{self.username}:{self.password}".encode("utf-8")
        token = base64.b64encode(raw).decode("ascii")
        return f"Proxy-Authorization: Basic {token}\r\n".encode("ascii")


def parse_upstream(value: str) -> Upstream:
    parsed = urlsplit(value)
    if parsed.scheme not in ("http", "https"):
        raise ValueError("Only http:// or https:// upstream proxies are supported")
    if not parsed.hostname or not parsed.port:
        raise ValueError("Proxy must include host and port")
    if parsed.username is None or parsed.password is None:
        raise ValueError("Proxy must include username and password")
    return Upstream(parsed.hostname, parsed.port, parsed.username, parsed.password)


def relay(client: socket.socket, upstream: socket.socket) -> None:
    sockets = [client, upstream]
    while True:
        readable, _, exceptional = select.select(sockets, [], sockets, 300)
        if exceptional or not readable:
            return
        for source in readable:
            target = upstream if source is client else client
            data = source.recv(BUFFER_SIZE)
            if not data:
                return
            target.sendall(data)


class ProxyHandler(socketserver.StreamRequestHandler):
    upstream: Upstream

    def handle(self) -> None:
        first_line = self.rfile.readline(BUFFER_SIZE)
        if not first_line:
            return

        header_lines = []
        while True:
            line = self.rfile.readline(BUFFER_SIZE)
            if not line or line == b"\r\n":
                break
            if not line.lower().startswith(b"proxy-authorization:"):
                header_lines.append(line)

        try:
            upstream_sock = socket.create_connection(
                (self.upstream.host, self.upstream.port), timeout=30
            )
        except OSError as exc:
            self.wfile.write(f"HTTP/1.1 502 Bad Gateway\r\n\r\n{exc}\n".encode())
            return

        with upstream_sock:
            upstream_sock.sendall(first_line)
            upstream_sock.sendall(self.upstream.auth_header)
            for line in header_lines:
                upstream_sock.sendall(line)
            upstream_sock.sendall(b"\r\n")
            relay(self.connection, upstream_sock)


class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main() -> int:
    if len(sys.argv) != 3:
        print(
            "Usage: proxy-forwarder.py <listen-host:listen-port> "
            "<http://user:pass@upstream-host:upstream-port>",
            file=sys.stderr,
        )
        return 2

    listen_host, listen_port_raw = sys.argv[1].rsplit(":", 1)
    upstream = parse_upstream(sys.argv[2])

    handler = type("ConfiguredProxyHandler", (ProxyHandler,), {"upstream": upstream})
    server = ThreadedTCPServer((listen_host, int(listen_port_raw)), handler)

    print(
        f"Forwarding http://{listen_host}:{listen_port_raw} "
        f"to {upstream.host}:{upstream.port}",
        flush=True,
    )

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        threading.Thread(target=server.shutdown, daemon=True).start()
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

