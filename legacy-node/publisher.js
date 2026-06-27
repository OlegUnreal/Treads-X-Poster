import fs from "node:fs";
import path from "node:path";
import { publishToThreads } from "./platforms/threads.js";
import { publishToX } from "./platforms/x.js";

export async function publishPost({ config, text, platforms, shouldPublish }) {
  const normalizedPlatforms = normalizePlatforms(platforms);

  if (!shouldPublish) {
    return normalizedPlatforms.map((platform) => ({
      platform,
      dryRun: true,
      text,
    }));
  }

  const results = [];

  for (const platform of normalizedPlatforms) {
    if (platform === "x") {
      results.push(await publishToX({ ...config.x, text }));
    } else if (platform === "threads") {
      results.push(await publishToThreads({ ...config.threads, text }));
    }
  }

  return results;
}

export function saveDraft({ posts, topic, filePath = "generated/posts.jsonl" }) {
  const absolutePath = path.resolve(filePath);
  fs.mkdirSync(path.dirname(absolutePath), { recursive: true });

  const now = new Date().toISOString();
  const lines = posts.map((text) =>
    JSON.stringify({
      createdAt: now,
      topic,
      text,
    }),
  );

  fs.appendFileSync(absolutePath, `${lines.join("\n")}\n`);

  return absolutePath;
}

function normalizePlatforms(platforms) {
  const values = platforms
    .split(",")
    .map((value) => value.trim().toLowerCase())
    .filter(Boolean);

  const allowed = new Set(["x", "threads"]);
  const unknown = values.filter((value) => !allowed.has(value));

  if (unknown.length > 0) {
    throw new Error(`Unknown platform(s): ${unknown.join(", ")}`);
  }

  return values.length > 0 ? values : ["x", "threads"];
}
