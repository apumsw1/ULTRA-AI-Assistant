const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS, GET",
  "Access-Control-Allow-Headers": "Content-Type",
  "Content-Type": "application/json; charset=utf-8"
};

function reply(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: cors
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    if (url.pathname === "/health" && request.method === "GET") {
      return reply({
        ok: true,
        service: "ULTRA AI Voice"
      });
    }

    if (url.pathname !== "/api/voice" || request.method !== "POST") {
      return reply({ ok: false, error: "Not found" }, 404);
    }

    if (!env.OPENAI_API_KEY) {
      return reply({
        ok: false,
        error: "AI API key is not configured"
      }, 503);
    }

    let data;

    try {
      data = await request.json();
    } catch {
      return reply({ ok: false, error: "Invalid JSON" }, 400);
    }

    const message = String(data.message || "").trim();

    if (!message) {
      return reply({ ok: false, error: "Message is empty" }, 400);
    }

    if (message.length > 3000) {
      return reply({ ok: false, error: "Message too long" }, 413);
    }

    const history = Array.isArray(data.history)
      ? data.history.slice(-8)
          .filter(x =>
            x &&
            ["user", "assistant"].includes(x.role) &&
            typeof x.content === "string"
          )
          .map(x => ({
            role: x.role,
            content: x.content.slice(0, 1500)
          }))
      : [];

    try {
      const response = await fetch(
        "https://api.openai.com/v1/responses",
        {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${env.OPENAI_API_KEY}`,
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            model: env.OPENAI_MODEL || "gpt-4o-mini",
            instructions:
              "You are ULTRA, an advanced multilingual voice assistant " +
              "designed primarily to help users control their Android phones. " +
              "You are not a general ChatGPT clone. " +
              "Understand Bengali, Hindi and English. " +
              "Reply in the same language as the user. " +
              "Understand conversation context and follow-up answers. " +
              "Ask a short clarification when a command is ambiguous. " +
              "Be natural, warm and concise for spoken responses. " +
              "Never claim you performed a phone action. " +
              "Actual device actions are executed by the Android app " +
              "only after local permission and safety checks. " +
              "Do not use Markdown in normal spoken replies.",
            input: [
              ...history,
              { role: "user", content: message }
            ],
            max_output_tokens: 220,
            store: false
          })
        }
      );

      const result = await response.json();

      if (!response.ok) {
        return reply({
          ok: false,
          error: "AI provider request failed",
          status: response.status
        }, 502);
      }

      let answer = result.output_text || "";

      if (!answer && Array.isArray(result.output)) {
        for (const item of result.output) {
          for (const part of item.content || []) {
            if (part.type === "output_text") {
              answer += part.text || "";
            }
          }
        }
      }

      answer = answer.trim();

      if (!answer) {
        return reply({
          ok: false,
          error: "Empty AI response"
        }, 502);
      }

      return reply({
        ok: true,
        reply: answer
      });

    } catch {
      return reply({
        ok: false,
        error: "AI service unavailable"
      }, 502);
    }
  }
};
