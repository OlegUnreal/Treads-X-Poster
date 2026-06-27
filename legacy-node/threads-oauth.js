#!/usr/bin/env node
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { URL } from "node:url";
import { getConfig, requireValue } from "./config.js";

const SESSION_PATH = path.resolve("generated/threads-oauth-session.json");
const config = getConfig();
const clientId = requireValue(config.threads.appId, "THREADS_APP_ID");
const clientSecret = requireValue(config.threads.appSecret, "THREADS_APP_SECRET");
const redirectUri = config.threads.redirectUri;
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
  const scopes = ["threads_basic", "threads_content_publish"];

  fs.mkdirSync(path.dirname(SESSION_PATH), { recursive: true });
  fs.writeFileSync(
    SESSION_PATH,
    JSON.stringify({ state, redirectUri, createdAt: new Date().toISOString() }, null, 2),
  );

  const authUrl = new URL("https://threads.net/oauth/authorize");
  authUrl.searchParams.set("client_id", clientId);
  authUrl.searchParams.set("redirect_uri", redirectUri);
  authUrl.searchParams.set("scope", scopes.join(","));
  authUrl.searchParams.set("response_type", "code");
  authUrl.searchParams.set("state", state);

  console.log("Open this URL in your browser and approve the app:");
  console.log(authUrl.toString());
  console.log("");
  console.log("After Threads redirects to 127.0.0.1, copy the full browser URL and run:");
  console.log('node .\\src\\threads-oauth.js complete --url "PASTE_URL_HERE"');
}

async function completeAuth() {
  const callbackUrl = readArg("url");

  if (!callbackUrl) {
    throw new Error('Missing callback URL. Use: node .\\src\\threads-oauth.js complete --url "http://127.0.0.1:3001/callback?..."');
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

  const shortToken = await exchangeCodeForToken(code);
  const longToken = await exchangeForLongLivedToken(shortToken.access_token);
  const profile = await getThreadsProfile(longToken.access_token);

  updateEnvValue("THREADS_ACCESS_TOKEN", longToken.access_token);
  updateEnvValue("THREADS_USER_ID", profile.id);

  console.log("Threads credentials saved to backend/config/.env.");
  console.log(`Threads user: ${profile.username || profile.id}`);
}

async function exchangeCodeForToken(code) {
  const body = new URLSearchParams({
    client_id: clientId,
    client_secret: clientSecret,
    grant_type: "authorization_code",
    redirect_uri: redirectUri,
    code,
  });

  const response = await fetch("https://graph.threads.net/oauth/access_token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body,
  });

  const payload = await response.json();

  if (!response.ok) {
    throw new Error(`Threads token exchange failed: ${JSON.stringify(payload)}`);
  }

  return payload;
}

async function exchangeForLongLivedToken(accessToken) {
  const url = new URL("https://graph.threads.net/access_token");
  url.searchParams.set("grant_type", "th_exchange_token");
  url.searchParams.set("client_secret", clientSecret);
  url.searchParams.set("access_token", accessToken);

  const response = await fetch(url);
  const payload = await response.json();

  if (!response.ok) {
    throw new Error(`Threads long-lived token exchange failed: ${JSON.stringify(payload)}`);
  }

  return payload;
}

async function getThreadsProfile(accessToken) {
  const url = new URL("https://graph.threads.net/v1.0/me");
  url.searchParams.set("fields", "id,username");
  url.searchParams.set("access_token", accessToken);

  const response = await fetch(url);
  const payload = await response.json();

  if (!response.ok) {
    throw new Error(`Threads profile lookup failed: ${JSON.stringify(payload)}`);
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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
