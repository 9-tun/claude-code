import { test } from "node:test";
import assert from "node:assert/strict";
import { runMember } from "../src/runner.js";

const node = process.execPath;

test("runMember sends the prompt via stdin and reads stdout", async () => {
  const r = await runMember(
    { id: "echo", label: "x", command: node, args: ["-e", "process.stdin.pipe(process.stdout)"] },
    "안녕 council",
  );
  assert.equal(r.ok, true);
  assert.equal(r.text, "안녕 council");
});

test("runMember reads the answer from an output file when configured", async () => {
  const script = "const f=process.argv[1];process.stdin.on('data',()=>{});process.stdin.on('end',()=>{require('fs').writeFileSync(f,'from file');console.log('noise')})";
  const r = await runMember(
    { id: "file", label: "x", command: node, args: ["-e", script, "{out}"], readsOutputFile: true },
    "q",
  );
  assert.equal(r.text, "from file");
});

test("runMember reports a missing CLI without throwing", async () => {
  const r = await runMember({ id: "none", label: "x", command: "no-such-cli-xyz", args: [] }, "q");
  assert.equal(r.ok, false);
  assert.match(r.text, /not found/);
});

test("runMember reports non-zero exit with stderr", async () => {
  const r = await runMember(
    { id: "fail", label: "x", command: node, args: ["-e", "console.error('login required');process.exit(2)"] },
    "q",
  );
  assert.equal(r.ok, false);
  assert.match(r.text, /login required/);
});
