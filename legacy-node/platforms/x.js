import crypto from "node:crypto";
import { formatApiError } from "../openai.js";
import { requireValue } from "../config.js";

const X_POST_URL = "https://api.x.com/2/tweets";

export async function publishToX(credentials) {
  const { accessToken, text } = credentials;

  if (credentials.apiKey && credentials.apiSecret && credentials.accessTokenSecret) {
    return publishToXWithOAuth1(credentials);
  }

  requireValue(accessToken, "X_ACCESS_TOKEN");

  const response = await fetch(X_POST_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ text }),
  });

  const payload = await response.json();

  if (!response.ok) {
    throw new Error(`X publish failed: ${formatApiError(payload)}`);
  }

  return {
    platform: "x",
    id: payload.data?.id,
    text: payload.data?.text,
  };
}

async function publishToXWithOAuth1({ apiKey, apiSecret, accessToken, accessTokenSecret, text }) {
  requireValue(apiKey, "X_API_KEY");
  requireValue(apiSecret, "X_API_SECRET");
  requireValue(accessToken, "X_ACCESS_TOKEN");
  requireValue(accessTokenSecret, "X_ACCESS_TOKEN_SECRET");

  const body = JSON.stringify({ text });
  const response = await fetch(X_POST_URL, {
    method: "POST",
    headers: {
      Authorization: createOAuth1Header({
        method: "POST",
        url: X_POST_URL,
        apiKey,
        apiSecret,
        accessToken,
        accessTokenSecret,
      }),
      "Content-Type": "application/json",
    },
    body,
  });

  const payload = await response.json();

  if (!response.ok) {
    throw new Error(`X publish failed: ${formatApiError(payload)}`);
  }

  return {
    platform: "x",
    id: payload.data?.id,
    text: payload.data?.text,
  };
}

function createOAuth1Header({ method, url, apiKey, apiSecret, accessToken, accessTokenSecret }) {
  const oauth = {
    oauth_consumer_key: apiKey,
    oauth_nonce: cryptoRandomString(),
    oauth_signature_method: "HMAC-SHA1",
    oauth_timestamp: Math.floor(Date.now() / 1000).toString(),
    oauth_token: accessToken,
    oauth_version: "1.0",
  };

  const signatureParams = Object.entries(oauth)
    .map(([key, value]) => [percentEncode(key), percentEncode(value)])
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${value}`)
    .join("&");

  const signatureBase = [
    method.toUpperCase(),
    percentEncode(url),
    percentEncode(signatureParams),
  ].join("&");

  const signingKey = `${percentEncode(apiSecret)}&${percentEncode(accessTokenSecret)}`;
  const signature = cryptoSign(signatureBase, signingKey);

  return `OAuth ${Object.entries({ ...oauth, oauth_signature: signature })
    .map(([key, value]) => `${percentEncode(key)}="${percentEncode(value)}"`)
    .join(", ")}`;
}

function cryptoRandomString() {
  return crypto.randomBytes(24).toString("hex");
}

function cryptoSign(value, key) {
  return crypto.createHmac("sha1", key).update(value).digest("base64");
}

function percentEncode(value) {
  return encodeURIComponent(value)
    .replaceAll("!", "%21")
    .replaceAll("*", "%2A")
    .replaceAll("'", "%27")
    .replaceAll("(", "%28")
    .replaceAll(")", "%29");
}
