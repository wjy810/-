/**
 * Pieces of an e-mail address or link label between which a line may break: after "@" and before
 * "." or "/". Narrow columns wrap "name@" / "example.com" instead of cutting a word in half.
 */
export function breakableChunks(label: string): string[] {
  return label.split(/(?<=@)|(?=[./])/).filter(Boolean)
}
