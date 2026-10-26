import { App, LogLevel, type webApi } from "@slack/bolt";
import { config } from "./config.js";
import { runCouncil, threadToTurns, type RawMessage, type ThreadIO } from "./council.js";

if (!config.slackBotToken || !config.slackAppToken) {
  console.error("SLACK_BOT_TOKEN and SLACK_APP_TOKEN are required. Copy .env.example to .env.");
  process.exit(1);
}

const app = new App({
  token: config.slackBotToken,
  appToken: config.slackAppToken,
  socketMode: true,
  logLevel: LogLevel.INFO,
});

type WebClient = webApi.WebClient;

let botUserId = "";
const busyThreads = new Set<string>();

function threadIO(client: WebClient, channel: string, threadTs: string): ThreadIO {
  return {
    async post(text) {
      const res = await client.chat.postMessage({ channel, thread_ts: threadTs, text, mrkdwn: true });
      return res.ts as string;
    },
    async update(ts, text) {
      await client.chat.update({ channel, ts, text });
    },
  };
}

async function loadThread(client: WebClient, channel: string, threadTs: string): Promise<RawMessage[]> {
  const out: RawMessage[] = [];
  let cursor: string | undefined;
  do {
    const res = await client.conversations.replies({ channel, ts: threadTs, cursor, limit: 200 });
    for (const m of res.messages ?? []) {
      out.push({ text: stripMention(m.text ?? ""), fromBot: m.user === botUserId || !!m.bot_id });
    }
    cursor = res.response_metadata?.next_cursor || undefined;
  } while (cursor);
  return out;
}

function stripMention(text: string): string {
  return botUserId ? text.replaceAll(`<@${botUserId}>`, "").trim() : text;
}

interface Incoming {
  channel: string;
  user?: string;
  text?: string;
  ts: string;
  thread_ts?: string;
  channel_type?: string;
}

async function handle(msg: Incoming, client: WebClient, mentioned: boolean): Promise<void> {
  if (!msg.text || !msg.user || msg.user === botUserId) return;
  if (config.allowedUserIds.length && !config.allowedUserIds.includes(msg.user)) return;

  const isDm = msg.channel_type === "im";
  const isCouncilChannel = config.channelIds.includes(msg.channel);
  if (!isDm && !isCouncilChannel && !mentioned) return;

  const threadTs = msg.thread_ts ?? msg.ts;
  const key = `${msg.channel}:${threadTs}`;
  const io = threadIO(client, msg.channel, threadTs);

  if (busyThreads.has(key)) {
    await io.post("_이전 질문을 처리 중입니다. 답변이 끝난 뒤 다시 질문해 주세요._");
    return;
  }
  busyThreads.add(key);

  try {
    const turns = msg.thread_ts
      ? threadToTurns(await loadThread(client, msg.channel, threadTs))
      : [{ speaker: "User", text: stripMention(msg.text) }];
    if (!turns.length || turns[turns.length - 1].speaker !== "User") return;

    await client.reactions.add({ channel: msg.channel, timestamp: msg.ts, name: "hourglass_flowing_sand" }).catch(() => {});
    await runCouncil(io, turns);
    await client.reactions.remove({ channel: msg.channel, timestamp: msg.ts, name: "hourglass_flowing_sand" }).catch(() => {});
    await client.reactions.add({ channel: msg.channel, timestamp: msg.ts, name: "white_check_mark" }).catch(() => {});
  } catch (err) {
    console.error(err);
    await io.post(`⚠️ 오류: ${(err as Error).message}`).catch(() => {});
  } finally {
    busyThreads.delete(key);
  }
}

// Plain messages in council channels, threads under them, and DMs.
app.message(async ({ message, client }) => {
  if (message.subtype !== undefined) return; // edits, joins, bot messages, ...
  const text = message.text ?? "";
  if (botUserId && text.includes(`<@${botUserId}>`)) return; // handled by app_mention
  await handle(message as Incoming, client, false);
});

// @mentions anywhere the bot is invited.
app.event("app_mention", async ({ event, client }) => {
  await handle(event as Incoming, client, true);
});

(async () => {
  await app.start();
  const auth = await app.client.auth.test();
  botUserId = auth.user_id as string;
  console.log(`AI Council bot running as @${auth.user} (${config.members.map((m) => m.id).join(", ")})`);
})();
