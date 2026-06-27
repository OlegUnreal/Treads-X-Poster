import { formatApiError } from "../openai.js";
import { requireValue } from "../config.js";

const THREADS_API_BASE_URL = "https://graph.threads.net/v1.0";

export async function publishToThreads({ accessToken, userId, text }) {
  requireValue(accessToken, "THREADS_ACCESS_TOKEN");
  requireValue(userId, "THREADS_USER_ID");

  const creationId = await createTextContainer({ accessToken, userId, text });
  const published = await publishContainer({ accessToken, userId, creationId });

  return {
    platform: "threads",
    id: published.id,
    creationId,
  };
}

async function createTextContainer({ accessToken, userId, text }) {
  const params = new URLSearchParams({
    media_type: "TEXT",
    text,
    access_token: accessToken,
  });

  const response = await performThreadsRequest(`${THREADS_API_BASE_URL}/${userId}/threads`, {
    method: "POST",
    body: params,
  });

  const payload = await response.json();

  if (!response.ok) {
    throw new Error(`Threads container creation failed: ${formatApiError(payload)}`);
  }

  if (!payload.id) {
    throw new Error("Threads container creation did not return an id.");
  }

  return payload.id;
}

async function publishContainer({ accessToken, userId, creationId }) {
  const params = new URLSearchParams({
    creation_id: creationId,
    access_token: accessToken,
  });

  const response = await performThreadsRequest(`${THREADS_API_BASE_URL}/${userId}/threads_publish`, {
    method: "POST",
    body: params,
  });

  const payload = await response.json();

  if (!response.ok) {
    throw new Error(`Threads publish failed: ${formatApiError(payload)}`);
  }

  return payload;
}

async function performThreadsRequest(url, options) {
  try {
    return await fetch(url, options);
  } catch (error) {
    throw new Error(buildNetworkErrorMessage(error));
  }
}

function buildNetworkErrorMessage(error) {
  const details = [];

  if (error?.message) {
    details.push(error.message);
  }

  if (error?.cause?.code) {
    details.push(`code=${error.cause.code}`);
  }

  if (error?.cause?.errno) {
    details.push(`errno=${error.cause.errno}`);
  }

  if (error?.cause?.syscall) {
    details.push(`syscall=${error.cause.syscall}`);
  }

  if (error?.cause?.hostname) {
    details.push(`host=${error.cause.hostname}`);
  }

  if (error?.cause?.address) {
    details.push(`address=${error.cause.address}`);
  }

  if (error?.cause?.port) {
    details.push(`port=${error.cause.port}`);
  }

  const suffix = details.length > 0 ? ` (${details.join(", ")})` : "";
  return `Threads request failed before the API responded${suffix}`;
}
