/** Slack shows long messages poorly; keep each chunk well under the ~4000 char limit. */
export const CHUNK_SIZE = 3500;

/** Converts common Markdown to Slack mrkdwn, leaving code blocks untouched. */
export function toMrkdwn(md: string): string {
  return md
    .split(/(```[\s\S]*?```)/g)
    .map((part) => {
      if (part.startsWith("```")) return part;
      return part
        .replace(/^#{1,6}\s+(.+)$/gm, "*$1*") // headings -> bold
        .replace(/\*\*(.+?)\*\*/g, "*$1*") // **bold** -> *bold*
        .replace(/__(.+?)__/g, "*$1*")
        .replace(/~~(.+?)~~/g, "~$1~")
        .replace(/\[([^\]]+)\]\((https?:\/\/[^)]+)\)/g, "<$2|$1>") // links
        .replace(/^(\s*)[-*]\s+/gm, "$1• "); // bullets
    })
    .join("");
}

/** Splits text into chunks, preferring line breaks, and keeps code fences balanced. */
export function splitText(text: string, size = CHUNK_SIZE): string[] {
  const chunks: string[] = [];
  let rest = text;

  while (rest.length > size) {
    let cut = rest.lastIndexOf("\n", size);
    if (cut < size * 0.5) cut = size;
    let chunk = rest.slice(0, cut);
    rest = rest.slice(cut).replace(/^\n/, "");

    // If the chunk leaves a code fence open, close it and reopen in the next chunk.
    if ((chunk.match(/```/g)?.length ?? 0) % 2 === 1) {
      chunk += "\n```";
      rest = "```\n" + rest;
    }
    chunks.push(chunk);
  }
  if (rest.length || chunks.length === 0) chunks.push(rest);
  return chunks;
}

/** Strips the "*🟠 Claude*" header the bot adds, returning the speaker name and body. */
export function parseBotMessage(text: string): { speaker: string; body: string } | null {
  const m = text.match(/^\*(?:\S+\s)?([^*\n]+?)\*(?:\s*_\([^)]*\)_)?\n([\s\S]*)$/);
  if (!m) return null;
  return { speaker: m[1].trim(), body: m[2].trim() };
}
