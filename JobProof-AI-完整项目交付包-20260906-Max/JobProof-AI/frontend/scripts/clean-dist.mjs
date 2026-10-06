import { rmSync } from 'node:fs'
import { dirname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const distDirectory = resolve(projectRoot, 'dist')

if (distDirectory === projectRoot || !distDirectory.startsWith(`${projectRoot}${sep}`)) {
  throw new Error(`Refusing to clean unexpected build directory: ${distDirectory}`)
}

rmSync(distDirectory, { recursive: true, force: true })
