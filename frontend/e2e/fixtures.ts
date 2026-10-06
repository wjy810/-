import { fileURLToPath } from 'node:url'

/** Cookie jar of the dev seeker, written by global-setup.ts. */
export const SEEKER_STATE = fileURLToPath(new URL('./.auth/seeker.json', import.meta.url))
