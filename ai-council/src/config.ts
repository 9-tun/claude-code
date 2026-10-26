import "dotenv/config";
import path from "node:path";
import { fileURLToPath } from "node:url";

export interface CouncilMember {
  /** Stable key used in env vars and logs. */
  id: string;
  /** Label shown in Slack. */
  label: string;
  /** Executable name (resolved via PATH; .cmd shims work on Windows). */
  command: string;
  /** Arguments. The prompt itself is always sent through stdin. */
  args: string[];
  /**
   * If set, the CLI writes its final answer to this file instead of stdout.
   * "{out}" inside args is replaced with a temp file path.
   */
  readsOutputFile?: boolean;
}

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function env(name: string, fallback = ""): string {
  return process.env[name]?.trim() || fallback;
}

function list(name: string): string[] {
  return env(name)
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}

// Each CLI is run in headless mode with the prompt on stdin.
// Verify flags with `<cli> --help` if a CLI update breaks something.
const ALL_MEMBERS: CouncilMember[] = [
  {
    id: "claude",
    label: "🟠 Claude",
    command: env("CLAUDE_BIN", "claude"),
    args: ["-p", "--output-format", "text"],
  },
  {
    id: "gpt",
    label: "🟢 GPT",
    command: env("CODEX_BIN", "codex"),
    args: [
      "exec",
      "--skip-git-repo-check",
      "--sandbox",
      "read-only",
      "--ephemeral",
      "--color",
      "never",
      "--output-last-message",
      "{out}",
      "-",
    ],
    readsOutputFile: true,
  },
  {
    id: "gemini",
    label: "🔵 Gemini",
    command: env("GEMINI_BIN", "gemini"),
    // -p runs headless; its value is appended after the stdin content.
    args: ["--output-format", "text", "-p", "Respond to the request above."],
  },
];

const enabled = list("COUNCIL_MEMBERS");

export const config = {
  slackBotToken: env("SLACK_BOT_TOKEN"),
  slackAppToken: env("SLACK_APP_TOKEN"),

  /** Channels where every top-level message triggers the council. Empty = DMs and mentions only. */
  channelIds: list("COUNCIL_CHANNEL_IDS"),
  /** Slack user IDs allowed to trigger the bot. Empty = anyone who can post. */
  allowedUserIds: list("ALLOWED_USER_IDS"),

  members: enabled.length
    ? ALL_MEMBERS.filter((m) => enabled.includes(m.id))
    : ALL_MEMBERS,
  /** Member that writes the final synthesis. */
  chairId: env("COUNCIL_CHAIR", "claude"),
  /** Adds an anonymized cross-review stage (slower, more calls). */
  crossReview: env("COUNCIL_REVIEW", "false") === "true",

  timeoutMs: Number(env("CLI_TIMEOUT_SEC", "300")) * 1000,
  /** Max characters of thread history passed to each model. */
  maxContextChars: Number(env("MAX_CONTEXT_CHARS", "30000")),
  /** Language instruction appended to every prompt. */
  answerLanguage: env("ANSWER_LANGUAGE", "Korean"),

  /** Empty directory the CLIs run in, so they don't read unrelated files. */
  workspaceDir: env("WORKSPACE_DIR", path.join(rootDir, "workspace")),
};

export function getChair(): CouncilMember {
  return config.members.find((m) => m.id === config.chairId) ?? config.members[0];
}
