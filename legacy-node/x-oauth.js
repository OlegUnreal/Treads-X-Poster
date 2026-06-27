#!/usr/bin/env node
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { URL } from "node:url";
import { getConfig, requireValue } from "./config.js";

const SESSION_PATH = path.resolve("generated/x-oauth-session.json");
const config = getConfig();
const clientId = requireValue(config.x.clientId, "X_CLIENT_ID");
const clientSecret = requireValue(config.x.clientSecret, "X_CLIENT_SECRET");
const redirectUri = config.x.redirectUri;
const scopes = config.x.scopes.split(/\s+/).filter(Boolean);
const command = process.argv[2] || "start";

try {
  if (command === "complete") {
    await completeAuth();
  } else {
    startAuth();
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}

function startAuth() {
  const state = crypto.randomBytes(24).toString("hex");
  const codeVerifier = base64Url(crypto.randomBytes(64));
  const codeChallenge = base64Url(crypto.createHash("sha256").update(codeVerifier).digest());

  fs.mkdirSync(path.dirname(SESSION_PATH), { recursive: true });
  fs.writeFileSync(
    SESSION_PATH,
    JSON.stringify({ state, codeVerifier, redirectUri, createdAt: new Date().toISOString() }, null, 2),
  );

  const authUrl = new URL("https://x.com/i/oauth2/authorize");
  authUrl.searchParams.set("response_type", "code");
  authUrl.searchParams.set("client_id", clientId);
  authUrl.searchParams.set("redirect_uri", redirectUri);
  authUrl.searchParams.set("scope", scopes.join(" "));
  authUrl.searchParams.set("state", state);
  authUrl.searchParams.set("code_challenge", codeChallenge);
  authUrl.searchParams.set("code_challenge_method", "S256");

  console.log("Open this URL in your browser and approve the app:");
  console.log(authUrl.toString());
  console.log("");
  console.log("After X redirects to 127.0.0.1, copy the full browser URL and run:");
  console.log("node .\\src\\x-oauth.js complete --url \"PASTE_URL_HERE\"");
}

async function completeAuth() {
  const callbackUrl = readArg("url");

  if (!callbackUrl) {
    throw new Error('Missing callback URL. Use: node .\\src\\x-oauth.js complete --url "http://127.0.0.1:3000/callback?..."');
  }

  if (!fs.existsSync(SESSION_PATH)) {
    throw new Error("OAuth session file is missing. Run start first.");
  }

  const session = JSON.parse(fs.readFileSync(SESSION_PATH, "utf8"));
  const parsedUrl = new URL(callbackUrl);
  const state = parsedUrl.searchParams.get("state");
  const code = parsedUrl.searchParams.get("code");

  if (state !== session.state) {
    throw new Error("OAuth state did not match. Run start again and retry.");
  }

  if (!code) {
    throw new Error("Callback URL does not include a code parameter.");
  }

  const token = await exchangeCodeForToken({
    code,
    codeVerifier: session.codeVerifier,
  });

  updateEnvValue("X_ACCESS_TOKEN", token.access_token);

  if (token.refresh_token) {
    updateEnvValue("X_REFRESH_TOKEN", token.refresh_token);
  }

  console.log("X access token saved to backend/config/.env.");
}

async function exchangeCodeForToken({ code, codeVerifier }) {
  const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
  const body = new URLSearchParams({
    code,
    grant_type: "authorization_code",
    redirect_uri: redirectUri,
    code_verifier: codeVerifier,
  });

  const response = await fetch("https://api.x.com/2/oauth2/token", {
    method: "POST",
    headers: {
      Authorization: `Basic ${credentials}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body,
  });

  const payload = await response.json();

  if (!response.ok) {
    throw new Error(`X token exchange failed: ${JSON.stringify(payload)}`);
  }

  return payload;
}

function updateEnvValue(key, value) {
  const envPath = path.resolve("backend/config/.env");
  const line = `${key}=${value}`;
  const current = fs.existsSync(envPath) ? fs.readFileSync(envPath, "utf8") : "";
  const pattern = new RegExp(`^${escapeRegExp(key)}=.*$`, "m");
  const next = pattern.test(current)
    ? current.replace(pattern, line)
    : `${current.trimEnd()}\n${line}\n`;

  fs.writeFileSync(envPath, next);
}

function readArg(name) {
  const flag = `--${name}`;
  const index = process.argv.indexOf(flag);

  if (index === -1) {
    return undefined;
  }

  return process.argv[index + 1];
}

function base64Url(buffer) {
  return buffer
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
