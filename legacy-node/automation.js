import fs from "node:fs";
import path from "node:path";

export function readContentPlan(filePath = "backend/config/content-plan.json") {
  const absolutePath = path.resolve(filePath);

  if (!fs.existsSync(absolutePath)) {
    throw new Error(`Content plan not found: ${absolutePath}`);
  }

  const plan = JSON.parse(fs.readFileSync(absolutePath, "utf8"));

  if (!Array.isArray(plan.items) || plan.items.length === 0) {
    throw new Error("Content plan must include a non-empty items array.");
  }

  return plan.items;
}

export function saveQueuedPosts({
  posts,
  topic,
  tone,
  language,
  platforms,
  filePath = "generated/queue.jsonl",
}) {
  const absolutePath = path.resolve(filePath);
  fs.mkdirSync(path.dirname(absolutePath), { recursive: true });

  const now = new Date().toISOString();
  const lines = posts.map((text) =>
    JSON.stringify({
      id: createQueueId(),
      status: "ready",
      createdAt: now,
      topic,
      tone,
      language,
      platforms,
      text,
    }),
  );

  fs.appendFileSync(absolutePath, `${lines.join("\n")}\n`);

  return absolutePath;
}

export function readQueuedPosts(filePath = "generated/queue.jsonl") {
  const absolutePath = path.resolve(filePath);

  if (!fs.existsSync(absolutePath)) {
    return [];
  }

  return fs
    .readFileSync(absolutePath, "utf8")
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => JSON.parse(line));
}

export function writeQueuedPosts(posts, filePath = "generated/queue.jsonl") {
  const absolutePath = path.resolve(filePath);
  fs.mkdirSync(path.dirname(absolutePath), { recursive: true });
  const content = posts.map((post) => JSON.stringify(post)).join("\n");
  fs.writeFileSync(absolutePath, content ? `${content}\n` : "");
  return absolutePath;
}

export function markQueuedPostPublished({ id, platform, result, filePath = "generated/queue.jsonl" }) {
  const posts = readQueuedPosts(filePath);
  const now = new Date().toISOString();
  let found = false;

  const updatedPosts = posts.map((post) => {
    if (post.id !== id) {
      return post;
    }

    found = true;
    const published = {
      ...(post.published || {}),
      [platform]: {
        at: now,
        result,
      },
    };

    const expectedPlatforms = Array.isArray(post.platforms) ? post.platforms : [];
    const isFullyPublished =
      expectedPlatforms.length > 0 &&
      expectedPlatforms.every((expectedPlatform) => published[expectedPlatform]);

    return {
      ...post,
      status: isFullyPublished ? "posted" : post.status,
      published,
    };
  });

  if (!found) {
    throw new Error(`Queued post not found: ${id}`);
  }

  writeQueuedPosts(updatedPosts, filePath);
}

export function countReadyPosts({ platform, filePath = "generated/queue.jsonl" } = {}) {
  return readQueuedPosts(filePath).filter((post) => {
    if (post.status !== "ready") {
      return false;
    }

    if (!platform) {
      return true;
    }

    const platforms = Array.isArray(post.platforms) ? post.platforms : [];
    return platforms.includes(platform) && !post.published?.[platform];
  }).length;
}

export function saveXComposerLinks({ filePath = "generated/x-ready.html", queuePath = "generated/queue.jsonl" } = {}) {
  const posts = readQueuedPosts(queuePath).filter((post) => {
    const platforms = Array.isArray(post.platforms) ? post.platforms : [];
    return post.status === "ready" && platforms.includes("x") && !post.published?.x;
  });
  const absolutePath = path.resolve(filePath);
  const rows = posts
    .map((post, index) => {
      const url = `https://x.com/intent/tweet?text=${encodeURIComponent(post.text)}`;
      return `<article>
  <h2>Post ${index + 1}</h2>
  <p>${escapeHtml(post.text)}</p>
  <a href="${url}" target="_blank" rel="noreferrer">Open in X</a>
</article>`;
    })
    .join("\n");

  fs.mkdirSync(path.dirname(absolutePath), { recursive: true });
  fs.writeFileSync(
    absolutePath,
    `<!doctype html>
<html lang="uk">
<head>
  <meta charset="utf-8">
  <title>Ready X Posts</title>
  <style>
    body { font-family: Arial, sans-serif; max-width: 860px; margin: 32px auto; padding: 0 16px; line-height: 1.5; }
    article { border: 1px solid #ddd; border-radius: 8px; padding: 16px; margin: 16px 0; }
    h1 { font-size: 28px; }
    h2 { font-size: 18px; margin-top: 0; }
    a { display: inline-block; margin-top: 8px; padding: 8px 12px; background: #111; color: #fff; border-radius: 6px; text-decoration: none; }
  </style>
</head>
<body>
  <h1>Ready X Posts</h1>
  ${rows || "<p>No ready X posts.</p>"}
</body>
</html>
`,
  );

  return absolutePath;
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function createQueueId() {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
