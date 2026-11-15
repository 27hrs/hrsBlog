import { rm } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const DIST = path.join(ROOT, '.vitepress', 'dist')

await rm(DIST, { recursive: true, force: true })
console.log('[clean] removed .vitepress/dist')
