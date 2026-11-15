import { existsSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Node 22+ has process.loadEnvFile. We intentionally avoid dotenv.
 * `.env` is shared defaults; `.env.local` may override.
 */
export function loadEnv(cwd = process.cwd()) {
  if (typeof process.loadEnvFile !== 'function') return process.env

  for (const file of ['.env', '.env.local']) {
    const path = resolve(cwd, file)
    if (!existsSync(path)) continue
    try {
      process.loadEnvFile(path)
    } catch (error) {
      console.warn(`[env] 无法读取 ${file}: ${error.message}`)
    }
  }

  return process.env
}
