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
      return reply({ ok: true, service: "ULTRA AI Voice" });
    }

    if (url.pathname !== "/api/voice" || request.method !== "POST") {
      return reply({ ok: false, error: "Not found" }, 404);
    }

    if (!env.OPENAI_API_KEY) {
      console.error("Configuration error: OPENAI_API_KEY is missing");
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
              "You are ULTRA, a multilingual Android voice assistant. " +
              "Understand Bengali, Hindi and English. Reply in the same " +
              "language as the user. Be natural, warm and concise. " +
              "Never claim you performed a phone action. " +
              "Actual device actions are handled by the Android app. " +
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

      const raw = await response.text();

      let result = {};
      try {
        result = JSON.parse(raw);
      } catch {
        result = {};
      }

      if (!response.ok) {
        const providerError =
          result?.error?.message || "Unknown OpenAI API error";
        const providerCode = result?.error?.code || null;
        const providerType = result?.error?.type || null;

        console.error("OpenAI API request failed", {
          status: response.status,
          code: providerCode,
          type: providerType,
          message: providerError,
          requestId: response.headers.get("x-request-id")
        });

        return reply({
          ok: false,
          error: "OpenAI API request failed",
          provider_status: response.status,
          provider_code: providerCode,
          provider_message: String(providerError).slice(0, 500)
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
        console.error("OpenAI returned an empty response", {
          responseId: result.id || null,
          status: result.status || null
        });

        return reply({
          ok: false,
          error: "Empty AI response"
        }, 502);
      }

      return reply({
        ok: true,
        reply: answer
      });

    } catch (error) {
      console.error("AI fetch exception", {
        name: error?.name || "Error",
        message: error?.message || "Unknown fetch error"
      });

      return reply({
        ok: false,
        error: "AI service connection failed"
      }, 502);
    }
  }
};
