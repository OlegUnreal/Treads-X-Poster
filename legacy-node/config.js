import fs from "node:fs";
import path from "node:path";

export function loadEnv(filePath = "backend/config/.env") {
  const candidates = [path.resolve(filePath), path.resolve(".env")];
  const absolutePath = candidates.find((candidate) => fs.existsSync(candidate)) || candidates[0];

  if (!fs.existsSync(absolutePath)) {
    return;
  }

  const lines = fs.readFileSync(absolutePath, "utf8").split(/\r?\n/);

  for (const line of lines) {
    const trimmed = line.trim();

    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }

    const separatorIndex = trimmed.indexOf("=");

    if (separatorIndex === -1) {
      continue;
    }

    const key = trimmed.slice(0, separatorIndex).trim();
    let value = trimmed.slice(separatorIndex + 1).trim();

    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }

    if (!process.env[key]) {
      process.env[key] = value;
    }
  }
}

export function getConfig() {
  loadEnv();

  return {
    openai: {
      apiKey: process.env.OPENAI_API_KEY,
      model: process.env.OPENAI_MODEL || "gpt-5.2",
    },
    defaults: {
      language: process.env.POST_LANGUAGE || "uk",
      topic: process.env.POST_TOPIC || "Behind The Smile",
      tone: process.env.POST_TONE || "warm, fan-friendly, concise",
      count: Number.parseInt(process.env.POST_COUNT || "3", 10),
    },
    x: {
      accessToken: process.env.X_ACCESS_TOKEN,
      clientId: process.env.X_CLIENT_ID,
      clientSecret: process.env.X_CLIENT_SECRET,
      redirectUri: process.env.X_REDIRECT_URI || "http://127.0.0.1:3000/callback",
      scopes: process.env.X_SCOPES || "tweet.read tweet.write users.read",
      apiKey: process.env.X_API_KEY,
      apiSecret: process.env.X_API_SECRET,
      accessTokenSecret: process.env.X_ACCESS_TOKEN_SECRET,
    },
    threads: {
      accessToken: process.env.THREADS_ACCESS_TOKEN,
      userId: process.env.THREADS_USER_ID,
      appId: process.env.THREADS_APP_ID || process.env.META_APP_ID,
      appSecret: process.env.THREADS_APP_SECRET || process.env.META_APP_SECRET,
      redirectUri: process.env.THREADS_REDIRECT_URI || "http://127.0.0.1:3001/callback",
    },
  };
}

export function requireValue(value, name) {
  if (!value) {
    throw new Error(`Missing required setting: ${name}`);
  }

  return value;
}
