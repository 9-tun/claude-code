import { test } from "node:test";
import assert from "node:assert/strict";
import { parseBotMessage, splitText, toMrkdwn } from "../src/slack-format.js";

test("toMrkdwn converts bold, headings and bullets but not code", () => {
  const out = toMrkdwn("## Title\n**bold** text\n- item\n```\n**keep**\n```");
  assert.equal(out, "*Title*\n*bold* text\n• item\n```\n**keep**\n```");
});

test("splitText keeps chunks under size and balances code fences", () => {
  const text = "```\n" + "line\n".repeat(200) + "```";
  const chunks = splitText(text, 300);
  assert.ok(chunks.length > 1);
  for (const c of chunks) {
    assert.ok(c.length <= 310, `chunk too long: ${c.length}`);
    assert.equal((c.match(/```/g)?.length ?? 0) % 2, 0);
  }
});

test("splitText returns one chunk for short text", () => {
  assert.deepEqual(splitText("hi"), ["hi"]);
});

test("parseBotMessage extracts speaker and body", () => {
  assert.deepEqual(parseBotMessage("*🟠 Claude* _(12s)_\nanswer"), { speaker: "Claude", body: "answer" });
  assert.deepEqual(parseBotMessage("*⚖️ 종합 (Claude)*\nsum"), { speaker: "종합 (Claude)", body: "sum" });
  assert.equal(parseBotMessage("plain continuation"), null);
});
