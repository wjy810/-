/**
 * Pure matching for the command palette (docs/01 CMD-02): matches Chinese titles by
 * substring, full pinyin and pinyin initials ("jl" → "简历").
 */
export type SearchableCommand = { id: string; title: string; keywords?: string[]; pinyin?: string; initials?: string }

export function normalize(text: string): string {
  return text.toLowerCase().replace(/\s+/g, '')
}

/** Lower score is a better match; null means no match. */
export function scoreCommand(command: SearchableCommand, query: string): number | null {
  const q = normalize(query)
  if (!q) return 0
  const title = normalize(command.title)
  if (title === q) return 0
  if (title.startsWith(q)) return 1
  if (title.includes(q)) return 2
  if (command.initials && normalize(command.initials).startsWith(q)) return 3
  if (command.pinyin && normalize(command.pinyin).startsWith(q)) return 4
  if (command.initials && normalize(command.initials).includes(q)) return 5
  if (command.pinyin && normalize(command.pinyin).includes(q)) return 6
  if (command.keywords?.some(keyword => normalize(keyword).includes(q))) return 7
  return null
}

export function rankCommands<T extends SearchableCommand>(commands: T[], query: string, recent: string[] = []): T[] {
  const scored: Array<{ command: T; score: number; index: number }> = []
  commands.forEach((command, index) => {
    const score = scoreCommand(command, query)
    if (score === null) return
    const recency = recent.indexOf(command.id)
    scored.push({ command, score: score - (recency >= 0 && !query ? 10 - recency : 0), index })
  })
  return scored.sort((a, b) => a.score - b.score || a.index - b.index).map(item => item.command)
}
