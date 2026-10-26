import { config } from "./config.js";

export interface Turn {
  /** "User" or a council member label without emoji, e.g. "Claude". */
  speaker: string;
  text: string;
}

const SYSTEM = `You are one member of an "AI council": the user asks the same question to several AI models (Claude, GPT, Gemini) and compares their views.
Give your own independent, well-reasoned answer. Be concise and practical. If other members' earlier answers appear in the conversation, you may agree or disagree with them explicitly.
You have no access to the user's files; do not try to run tools. Answer in ${config.answerLanguage}.`;

/** Keeps the most recent turns that fit in the character budget (always keeps the last one). */
export function trimTurns(turns: Turn[], maxChars: number): Turn[] {
  const kept: Turn[] = [];
  let total = 0;
  for (let i = turns.length - 1; i >= 0; i--) {
    const size = turns[i].text.length + turns[i].speaker.length + 8;
    if (kept.length > 0 && total + size > maxChars) break;
    kept.unshift(turns[i]);
    total += size;
  }
  return kept;
}

function transcript(turns: Turn[]): string {
  return turns.map((t) => `[${t.speaker}]\n${t.text}`).join("\n\n");
}

export function answerPrompt(turns: Turn[]): string {
  const kept = trimTurns(turns, config.maxContextChars);
  const latest = kept[kept.length - 1];
  const history = kept.slice(0, -1);

  return [
    SYSTEM,
    history.length ? `## Conversation so far\n\n${transcript(history)}` : "",
    `## Latest message from the user\n\n${latest.text}`,
  ]
    .filter(Boolean)
    .join("\n\n");
}

function anonymized(answers: { text: string }[]): string {
  return answers
    .map((a, i) => `### Answer ${String.fromCharCode(65 + i)}\n${a.text}`)
    .join("\n\n");
}

export function reviewPrompt(question: string, answers: { text: string }[]): string {
  return `Several AI models answered the question below. The answers are anonymized.

## Question
${question}

## Answers
${anonymized(answers)}

## Task
Critically review the answers: point out factual errors, weak reasoning and missing points, then rank them from best to worst with a one-line reason each. Be brief. Answer in ${config.answerLanguage}.`;
}

export function synthesisPrompt(
  question: string,
  answers: { label: string; text: string }[],
  reviews: string[],
): string {
  const named = answers.map((a) => `### ${a.label}\n${a.text}`).join("\n\n");
  const reviewBlock = reviews.length
    ? `\n\n## Peer reviews (anonymized as A, B, C in the same order as above)\n${reviews.join("\n\n---\n\n")}`
    : "";

  return `You are the chair of an AI council. Synthesize the members' answers to the user's question.

## Question
${question}

## Member answers
${named}${reviewBlock}

## Output format (in ${config.answerLanguage}, concise)
1. Consensus: points all members agree on
2. Disagreements: where they differ, and who is more convincing and why
3. Final recommendation: the actionable conclusion
4. Open questions: what still needs to be verified (only if any)`;
}
