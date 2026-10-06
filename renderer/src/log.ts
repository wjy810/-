/** One JSON object per line, like the API's ECS logs. */
export function log(level: 'info' | 'warn' | 'error', message: string, fields: Record<string, unknown> = {}): void {
  const line = JSON.stringify({ '@timestamp': new Date().toISOString(), 'log.level': level, message, service: 'renderer', ...fields })
  if (level === 'error') process.stderr.write(`${line}\n`)
  else process.stdout.write(`${line}\n`)
}
