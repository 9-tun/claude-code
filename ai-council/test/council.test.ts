import { test } from "node:test";
import assert from "node:assert/strict";
import { config } from "../src/config.js";
import { runCouncil, threadToTurns, type ThreadIO } from "../src/council.js";
import type { RunResult } from "../src/runner.js";

function fakeThread() {
  const msgs = new Map<string, string>();
  let n = 0;
  const io: ThreadIO = {
    async post(text) {
      const ts = String(++n);
      msgs.set(ts, text);
      return ts;
    },
    async update(ts, text) {
      msgs.set(ts, text);
    },
  };
  return { io, msgs };
}

test("runCouncil posts each answer and a synthesis", async () => {
  const { io, msgs } = fakeThread();
  const prompts: string[] = [];
  await runCouncil(io, [{ speaker: "User", text: "Q?" }], async (member, prompt): Promise<RunResult> => {
    prompts.push(prompt);
    return { member, ok: true, text: `answer from ${member.id}`, ms: 1000 };
  });

  const all = [...msgs.values()];
  assert.equal(all.length, config.members.length + 1);
  for (const m of config.members) assert.ok(all.some((t) => t.includes(`answer from ${m.id}`)));
  assert.ok(all[all.length - 1].startsWith("*⚖️ 종합"));
  assert.ok(!all.some((t) => t.includes("⏳")), "placeholders must be replaced");
  assert.ok(prompts[prompts.length - 1].includes("answer from gpt"), "synthesis sees all answers");
});

test("runCouncil reports failures and skips synthesis with <2 answers", async () => {
  const { io, msgs } = fakeThread();
  await runCouncil(io, [{ speaker: "User", text: "Q?" }], async (member): Promise<RunResult> =>
    member.id === "claude"
      ? { member, ok: true, text: "only me", ms: 1 }
      : { member, ok: false, text: "not logged in", ms: 1 },
  );
  const all = [...msgs.values()];
  assert.ok(all.some((t) => t.includes("_(실패)_") && t.includes("not logged in")));
  assert.ok(all.some((t) => t.includes("종합을 생략")));
});

test("threadToTurns rebuilds history and merges continuation chunks", () => {
  const turns = threadToTurns([
    { text: "first question", fromBot: false },
    { text: "*🟠 Claude* _(3s)_\npart 1", fromBot: true },
    { text: "part 2", fromBot: true },
    { text: "*🟢 GPT* _(실패)_\n⚠️ error", fromBot: true },
    { text: "*⚖️ 종합 (Claude)*\nsummary", fromBot: true },
    { text: "follow-up", fromBot: false },
  ]);
  assert.deepEqual(turns, [
    { speaker: "User", text: "first question" },
    { speaker: "Claude", text: "part 1\npart 2" },
    { speaker: "종합 (Claude)", text: "summary" },
    { speaker: "User", text: "follow-up" },
  ]);
});
