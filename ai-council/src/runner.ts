import spawn from "cross-spawn";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { config, type CouncilMember } from "./config.js";

export interface RunResult {
  member: CouncilMember;
  ok: boolean;
  text: string;
  ms: number;
}

/** Runs one CLI in headless mode, sending the prompt via stdin. Never throws. */
export async function runMember(member: CouncilMember, prompt: string): Promise<RunResult> {
  const started = Date.now();
  let tmpDir: string | undefined;

  try {
    let outFile: string | undefined;
    if (member.readsOutputFile) {
      tmpDir = await mkdtemp(path.join(os.tmpdir(), "ai-council-"));
      outFile = path.join(tmpDir, "answer.txt");
    }
    const args = member.args.map((a) => (a === "{out}" && outFile ? outFile : a));

    const { code, stdout, stderr } = await exec(member.command, args, prompt);

    let text = stdout;
    if (outFile) {
      text = await readFile(outFile, "utf8").catch(() => stdout);
    }
    text = text.trim();

    if (code !== 0 || !text) {
      const detail = (stderr || text || `exit code ${code}`).trim().slice(-800);
      return { member, ok: false, text: detail, ms: Date.now() - started };
    }
    return { member, ok: true, text, ms: Date.now() - started };
  } catch (err) {
    return { member, ok: false, text: (err as Error).message, ms: Date.now() - started };
  } finally {
    if (tmpDir) await rm(tmpDir, { recursive: true, force: true }).catch(() => {});
  }
}

function exec(
  command: string,
  args: string[],
  input: string,
): Promise<{ code: number | null; stdout: string; stderr: string }> {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: config.workspaceDir,
      env: { ...process.env, NO_COLOR: "1" },
      stdio: ["pipe", "pipe", "pipe"],
      windowsHide: true,
    });

    let stdout = "";
    let stderr = "";
    child.stdout?.setEncoding("utf8").on("data", (d: string) => (stdout += d));
    child.stderr?.setEncoding("utf8").on("data", (d: string) => (stderr += d));

    const timer = setTimeout(() => {
      child.kill();
      const tail = stderr.trim().split("\n").slice(-5).join("\n");
      reject(new Error(`Timed out after ${config.timeoutMs / 1000}s${tail ? `\n${tail}` : ""}`));
    }, config.timeoutMs);

    child.on("error", (err) => {
      clearTimeout(timer);
      reject(
        (err as NodeJS.ErrnoException).code === "ENOENT"
          ? new Error(`'${command}' not found. Is the CLI installed and on PATH?`)
          : err,
      );
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      resolve({ code, stdout, stderr });
    });

    child.stdin?.on("error", () => {}); // CLI may exit before reading stdin
    child.stdin?.end(input, "utf8");
  });
}
