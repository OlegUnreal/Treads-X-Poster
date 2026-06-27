#!/usr/bin/env node
import { spawn } from "node:child_process";
import {
  countReadyPosts,
  markQueuedPostPublished,
  readContentPlan,
  readQueuedPosts,
  saveXComposerLinks,
  saveQueuedPosts,
} from "./automation.js";
import { getConfig } from "./config.js";
import { generatePosts } from "./openai.js";
import { publishPost, saveDraft } from "./publisher.js";

const command = process.argv[2] && !process.argv[2].startsWith("--") ? process.argv[2] : "draft";
const args = parseArgs(process.argv.slice(command === process.argv[2] ? 3 : 2));
const config = getConfig();

try {
  if (command === "auto-create") {
    await runAutoCreate(args, config);
  } else if (command === "daily") {
    await runDaily(args, config);
  } else if (command === "publish-queued-x") {
    await runPublishQueuedX(args, config);
  } else if (command === "compose-queued-x") {
    runComposeQueuedX(args);
  } else if (command === "build-x-links") {
    runBuildXLinks(args);
  } else if (command === "publish-queued-threads") {
    await runPublishQueuedThreads(args, config);
  } else {
  const topic = args.topic || config.defaults.topic;
  const count = Number.parseInt(args.count || config.defaults.count, 10);
  const tone = args.tone || config.defaults.tone;
  const language = args.language || config.defaults.language;
  const platforms = args.platforms || "x,threads";
  const shouldPublish = Boolean(args.publish);

  const posts = await generatePosts({
    ...config.openai,
    topic,
    count,
    tone,
    language,
  });

  const savedPath = saveDraft({ posts, topic });
  const selectedIndex = Math.max(Number.parseInt(args.index || "1", 10) - 1, 0);
  const selectedPost = posts[selectedIndex] || posts[0];

  console.log(`Generated ${posts.length} post option(s).`);
  console.log(`Saved drafts to ${savedPath}`);
  console.log("");

  posts.forEach((post, index) => {
    console.log(`${index + 1}. ${post}`);
  });

  if (command === "publish") {
    console.log("");
    console.log(shouldPublish ? "Publishing selected post..." : "Dry run. Add --publish to post for real.");

    const results = await publishPost({
      config,
      text: selectedPost,
      platforms,
      shouldPublish,
    });

    console.log(JSON.stringify(results, null, 2));
  } else if (command === "compose-x") {
    const composeUrl = `https://x.com/intent/tweet?text=${encodeURIComponent(selectedPost)}`;

    console.log("");
    console.log("Opening X composer with selected post...");
    console.log(composeUrl);
    openUrl(composeUrl);
  }
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}

async function runDaily(args, config) {
  const queuePath = args.queue || "generated/queue.jsonl";
  const minimumReady = Number.parseInt(args.minimumReady || "3", 10);
  const threadsPerRun = Number.parseInt(args.threadsPerRun || "1", 10);
  const xPerRun = Number.parseInt(args.xPerRun || "1", 10);
  const readyCount = countReadyPosts({ filePath: queuePath });

  if (readyCount < minimumReady) {
    console.log(`Only ${readyCount} ready post(s) found. Creating more.`);
    await runAutoCreate(args, config);
  } else {
    console.log(`${readyCount} ready post(s) found. Skipping generation.`);
  }

  for (let index = 0; index < threadsPerRun; index += 1) {
    const threadsReady = countReadyPosts({ platform: "threads", filePath: queuePath });

    if (threadsReady === 0) {
      console.log("No ready Threads posts left to publish.");
      break;
    }

    try {
      await runPublishQueuedThreads({ ...args, queue: queuePath, index: "1" }, config);
    } catch (error) {
      console.log(`Threads publish skipped due to error: ${error.message}`);
      break;
    }
  }

  for (let index = 0; index < xPerRun; index += 1) {
    const xReady = countReadyPosts({ platform: "x", filePath: queuePath });

    if (xReady === 0) {
      console.log("No ready X posts left to publish.");
      break;
    }

    try {
      await runPublishQueuedX({ ...args, queue: queuePath, index: "1" }, config);
    } catch (error) {
      console.log(`X publish skipped due to error: ${error.message}`);
      break;
    }
  }

  const xReady = countReadyPosts({ platform: "x", filePath: queuePath });
  const xLinksPath = saveXComposerLinks({ queuePath });
  console.log(`${xReady} ready X post(s) are available for manual composer posting.`);
  console.log(`Saved X composer links to ${xLinksPath}`);
}

function runBuildXLinks(args) {
  const queuePath = args.queue || "generated/queue.jsonl";
  const savedPath = saveXComposerLinks({ queuePath });
  console.log(`Saved X composer links to ${savedPath}`);
}

async function runPublishQueuedThreads(args, config) {
  const queuePath = args.queue || "generated/queue.jsonl";
  const selectedPost = selectQueuedPost({
    queuePath,
    platform: "threads",
    index: args.index,
    emptyMessage: "No ready queued Threads posts found.",
  });

  console.log("Publishing queued post to Threads:");
  console.log(selectedPost.text);

  const [result] = await publishPost({
    config,
    text: selectedPost.text,
    platforms: "threads",
    shouldPublish: true,
  });

  markQueuedPostPublished({
    id: selectedPost.id,
    platform: "threads",
    result,
    filePath: queuePath,
  });

  console.log("Published to Threads.");
  console.log(JSON.stringify(result, null, 2));
}

async function runPublishQueuedX(args, config) {
  const queuePath = args.queue || "generated/queue.jsonl";
  const selectedPost = selectQueuedPost({
    queuePath,
    platform: "x",
    index: args.index,
    emptyMessage: "No ready queued X posts found.",
  });

  console.log("Publishing queued post to X:");
  console.log(selectedPost.text);

  const [result] = await publishPost({
    config,
    text: selectedPost.text,
    platforms: "x",
    shouldPublish: true,
  });

  markQueuedPostPublished({
    id: selectedPost.id,
    platform: "x",
    result,
    filePath: queuePath,
  });

  console.log("Published to X.");
  console.log(JSON.stringify(result, null, 2));
}

function runComposeQueuedX(args) {
  const queuePath = args.queue || "generated/queue.jsonl";
  const posts = readQueuedPosts(queuePath).filter((post) => post.status === "ready");

  if (posts.length === 0) {
    throw new Error("No ready queued posts found.");
  }

  const selectedIndex = Math.max(Number.parseInt(args.index || "1", 10) - 1, 0);
  const selectedPost = posts[selectedIndex] || posts[0];
  const composeUrl = `https://x.com/intent/tweet?text=${encodeURIComponent(selectedPost.text)}`;

  console.log(`Selected queued post ${selectedIndex + 1} of ${posts.length}:`);
  console.log(selectedPost.text);
  console.log("");
  console.log("Open this URL if the browser does not open automatically:");
  console.log(composeUrl);
  openUrl(composeUrl);
}

function selectQueuedPost({ queuePath, platform, index, emptyMessage }) {
  const posts = readQueuedPosts(queuePath).filter((post) => {
    const platforms = Array.isArray(post.platforms) ? post.platforms : [];
    return post.status === "ready" && platforms.includes(platform) && !post.published?.[platform];
  });

  if (posts.length === 0) {
    throw new Error(emptyMessage);
  }

  const selectedIndex = Math.max(Number.parseInt(index || "1", 10) - 1, 0);
  return posts[selectedIndex] || posts[0];
}

async function runAutoCreate(args, config) {
  const planPath = args.plan || "backend/config/content-plan.json";
  const items = readContentPlan(planPath);
  let total = 0;

  for (const item of items) {
    const topic = item.topic || config.defaults.topic;
    const tone = item.tone || config.defaults.tone;
    const language = item.language || config.defaults.language;
    const count = Number.parseInt(item.count || config.defaults.count, 10);
    const platforms = item.platforms || ["x", "threads"];

    const posts = await generatePosts({
      ...config.openai,
      topic,
      count,
      tone,
      language,
    });

    const savedPath = saveQueuedPosts({
      posts,
      topic,
      tone,
      language,
      platforms,
    });

    total += posts.length;
    console.log(`Queued ${posts.length} post(s) for topic: ${topic}`);
    console.log(`Saved queue to ${savedPath}`);
  }

  console.log(`Auto-created ${total} queued post(s).`);
}

function openUrl(url) {
  if (process.platform === "win32") {
    spawn("cmd", ["/c", "start", "", url], {
      detached: true,
      stdio: "ignore",
      windowsHide: true,
    }).unref();
    return;
  }

  if (process.platform === "darwin") {
    spawn("open", [url], { detached: true, stdio: "ignore" }).unref();
    return;
  }

  spawn("xdg-open", [url], { detached: true, stdio: "ignore" }).unref();
}

function parseArgs(rawArgs) {
  const parsed = {};

  for (let index = 0; index < rawArgs.length; index += 1) {
    const arg = rawArgs[index];

    if (!arg.startsWith("--")) {
      continue;
    }

    const [rawKey, inlineValue] = arg.slice(2).split("=", 2);
    const key = toCamelCase(rawKey);
    const nextValue = rawArgs[index + 1];

    if (inlineValue !== undefined) {
      parsed[key] = inlineValue;
    } else if (nextValue && !nextValue.startsWith("--")) {
      parsed[key] = nextValue;
      index += 1;
    } else {
      parsed[key] = true;
    }
  }

  return parsed;
}

function toCamelCase(value) {
  return value.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
}
