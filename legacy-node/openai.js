import { requireValue } from "./config.js";

export async function generatePosts({ apiKey, model, topic, tone, language, count }) {
  requireValue(apiKey, "OPENAI_API_KEY");

  const response = await fetch("https://api.openai.com/v1/responses", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model,
      input: [
        {
          role: "system",
          content: [
            "You write concise, natural social media posts. Return only valid JSON.",
            "The voice must feel personal, reflective, and emotionally honest, never corporate, preachy, or campaign-like.",
            "Prefer concrete moments, small details, and humane language over slogans, awareness copy, or abstract advocacy.",
            "Do not force brand mentions. Do not mention Behind The Smile unless the user explicitly asks.",
            "For health, medicine, military, trauma, pain, PTSD, sleep, anxiety, or addiction topics: do not give dosages, treatment plans, diagnosis, sourcing instructions, or advice to use controlled medicines.",
            "Do not glamorize substances, self-destruction, or untreated suffering.",
            "If therapy, dependence, or pain appears, frame it as lived experience and support for professional care rather than instruction.",
            "Prefer soft, stigma-reducing, Ukraine-aware language that sounds like one person talking to another.",
          ].join(" "),
        },
        {
          role: "user",
          content: [
            `Create ${count} personal social media post options.`,
            `Topic: ${topic}`,
            `Language: ${language}`,
            `Tone: ${tone}`,
            "Each post must be suitable for both X and Threads.",
            "Keep every post under 260 characters.",
            "Write like a real person sharing a lived moment, not like a campaign or awareness poster.",
            "Prefer small concrete details and emotional honesty.",
            "Do not mention Behind The Smile unless the topic explicitly asks for it.",
            "Do not include direct medical advice beyond encouraging professional care and avoiding self-medication.",
            'Return JSON as: {"posts":["..."]}',
          ].join("\n"),
        },
      ],
      text: {
        format: {
          type: "json_schema",
          name: "social_posts",
          schema: {
            type: "object",
            additionalProperties: false,
            required: ["posts"],
            properties: {
              posts: {
                type: "array",
                minItems: 1,
                items: {
                  type: "string",
                  minLength: 1,
                  maxLength: 280,
                },
              },
            },
          },
        },
      },
    }),
  });

  const payload = await response.json();

  if (!response.ok) {
    throw new Error(`OpenAI request failed: ${formatApiError(payload)}`);
  }

  const text = extractOutputText(payload);
  const parsed = JSON.parse(text);

  return parsed.posts;
}

function extractOutputText(payload) {
  if (typeof payload.output_text === "string") {
    return payload.output_text;
  }

  const output = payload.output || [];

  for (const item of output) {
    for (const content of item.content || []) {
      if (content.type === "output_text" && content.text) {
        return content.text;
      }
    }
  }

  throw new Error("OpenAI response did not include output text.");
}

export function formatApiError(payload) {
  if (payload?.error?.message) {
    return payload.error.message;
  }

  return JSON.stringify(payload);
}
