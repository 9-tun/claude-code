// Verifies each CLI is installed, logged in and answers in headless mode.
import { config } from "./config.js";
import { runMember } from "./runner.js";

const prompt = "Reply with exactly: OK";
console.log(`Checking ${config.members.length} CLI(s) in ${config.workspaceDir} ...\n`);

const results = await Promise.all(config.members.map((m) => runMember(m, prompt)));
for (const r of results) {
  const status = r.ok ? "PASS" : "FAIL";
  console.log(`[${status}] ${r.member.id} (${r.member.command}) ${Math.round(r.ms / 1000)}s`);
  console.log(`       ${r.text.split("\n").slice(-3).join("\n       ")}\n`);
}
process.exit(results.every((r) => r.ok) ? 0 : 1);
