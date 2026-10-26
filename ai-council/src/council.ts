import { config, getChair, type CouncilMember } from "./config.js";
import { answerPrompt, reviewPrompt, synthesisPrompt, type Turn } from "./prompts.js";
import { runMember, type RunResult } from "./runner.js";
import { parseBotMessage, splitText, toMrkdwn } from "./slack-format.js";

/** Minimal Slack thread operations, so the council logic can be tested without Slack. */
export interface ThreadIO {
  post(text: string): Promise<string>;
  update(ts: string, text: string): Promise<void>;
}

export type Runner = (member: CouncilMember, prompt: string) => Promise<RunResult>;

export interface RawMessage {
  text?: string;
  fromBot: boolean;
}

const PENDING = "⏳ 답변 생성 중...";
const FAILED_MARK = "⚠️";
const SYNTHESIS_TITLE = "⚖️ 종합";
const REVIEW_TITLE = "🔍 교차 평가";

function plainName(label: string): string {
  return label.replace(/^\S+\s/, ""); // "🟠 Claude" -> "Claude"
}

function seconds(ms: number): string {
  return `${Math.round(ms / 1000)}s`;
}

/** Posts a titled message; long bodies are split across follow-up messages. */
async function postSection(io: ThreadIO, ts: string, title: string, body: string): Promise<void> {
  const [first, ...rest] = splitText(toMrkdwn(body));
  await io.update(ts, `${title}\n${first}`);
  for (const chunk of rest) await io.post(chunk);
}

/** Rebuilds the conversation from Slack thread messages for follow-up questions. */
export function threadToTurns(messages: RawMessage[]): Turn[] {
  const turns: Turn[] = [];

  for (const msg of messages) {
    const text = msg.text?.trim();
    if (!text) continue;

    if (!msg.fromBot) {
      turns.push({ speaker: "User", text });
      continue;
    }

    const parsed = parseBotMessage(text);
    if (!parsed) {
      // Continuation chunk of the previous bot message.
      const last = turns[turns.length - 1];
      if (last && last.speaker !== "User") last.text += "\n" + text;
      continue;
    }
    if (parsed.body.startsWith(FAILED_MARK) || parsed.body.startsWith("⏳")) continue;
    if (parsed.speaker.includes(plainName(REVIEW_TITLE))) continue; // reviews are noise for follow-ups
    turns.push({ speaker: parsed.speaker, text: parsed.body });
  }
  return turns;
}

/** Runs the full council for the latest user turn and posts results into the thread. */
export async function runCouncil(io: ThreadIO, turns: Turn[], run: Runner = runMember): Promise<void> {
  const members = config.members;
  const question = turns[turns.length - 1].text;
  const prompt = answerPrompt(turns);

  // Stage 1: independent answers in parallel, each posted as soon as it's ready.
  const placeholders = await Promise.all(members.map((m) => io.post(`*${m.label}*\n${PENDING}`)));

  const results = await Promise.all(
    members.map(async (m, i) => {
      const r = await run(m, prompt);
      const title = r.ok ? `*${m.label}* _(${seconds(r.ms)})_` : `*${m.label}* _(실패)_`;
      const body = r.ok ? r.text : `${FAILED_MARK} ${r.text}`;
      await postSection(io, placeholders[i], title, body);
      return r;
    }),
  );

  const answers = results.filter((r) => r.ok);
  if (answers.length < 2) {
    if (members.length > 1) await io.post("_답변이 2개 미만이라 종합을 생략합니다._");
    return;
  }

  // Stage 2 (optional): anonymized cross-review.
  let reviews: string[] = [];
  if (config.crossReview) {
    const rp = reviewPrompt(question, answers);
    const reviewTs = await Promise.all(
      answers.map((a) => io.post(`*${REVIEW_TITLE} · ${plainName(a.member.label)}*\n${PENDING}`)),
    );
    const reviewResults = await Promise.all(
      answers.map(async (a, i) => {
        const r = await run(a.member, rp);
        const title = `*${REVIEW_TITLE} · ${plainName(a.member.label)}*`;
        await postSection(io, reviewTs[i], title, r.ok ? r.text : `${FAILED_MARK} ${r.text}`);
        return r;
      }),
    );
    reviews = reviewResults.filter((r) => r.ok).map((r) => r.text);
  }

  // Stage 3: chair synthesis.
  const chair = getChair();
  const title = `*${SYNTHESIS_TITLE} (${plainName(chair.label)})*`;
  const ts = await io.post(`${title}\n${PENDING}`);
  const s = await run(
    chair,
    synthesisPrompt(
      question,
      answers.map((a) => ({ label: plainName(a.member.label), text: a.text })),
      reviews,
    ),
  );
  await postSection(io, ts, title, s.ok ? s.text : `${FAILED_MARK} ${s.text}`);
}
