#!/usr/bin/env node
/**
 * One entry point for everything the documentation site needs.
 *
 *   node scripts/docs.mjs api     run Dokka and turn its output into VitePress pages
 *   node scripts/docs.mjs dev     make sure the API pages exist, then start the dev server
 *   node scripts/docs.mjs build   generate everything and build the static site
 *
 * `api` skips Dokka when the HTML output is already newer than the Kotlin sources, so the usual
 * `npm run dev` does not pay for a Gradle run on every start. Pass `--force` to run it anyway,
 * or `--skip-dokka` to reuse whatever is already in `build/dokka/html`.
 */
import { spawn } from 'node:child_process'
import { existsSync, statSync } from 'node:fs'
import { mkdir, readdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const websiteDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repoDir = path.resolve(websiteDir, '..')

const DOKKA_HTML = path.join(repoDir, 'build', 'dokka', 'html')
const API_OUT = path.join(websiteDir, 'docs', 'api')
const ASSETS_OUT = path.join(websiteDir, '.vitepress', 'dokka')
const GENERATED_CSS = path.join(ASSETS_OUT, 'dokka.css')

const MODULES = ['graphglue', 'graphglue-core']

function run(command, args, options = {}) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, { stdio: 'inherit', shell: process.platform === 'win32', ...options })
        child.on('error', reject)
        child.on('exit', (code) => {
            if (code === 0) resolve()
            else reject(new Error(`${command} ${args.join(' ')} exited with code ${code}`))
        })
    })
}

/** Newest mtime below `dir`, or 0 when the directory does not exist. */
async function newestMtime(dir, filter = () => true) {
    if (!existsSync(dir)) return 0
    let newest = 0
    for (const entry of await readdir(dir, { withFileTypes: true, recursive: true })) {
        if (!entry.isFile() || !filter(entry.name)) continue
        const stats = statSync(path.join(entry.parentPath ?? entry.path, entry.name))
        if (stats.mtimeMs > newest) newest = stats.mtimeMs
    }
    return newest
}

async function dokkaIsStale() {
    const generated = await newestMtime(DOKKA_HTML, (name) => name.endsWith('.html'))
    if (generated === 0) return true
    const sources = Math.max(
        ...(await Promise.all(MODULES.map((module) => newestMtime(path.join(repoDir, module, 'src')))))
    )
    return sources > generated
}

/**
 * The theme imports the generated stylesheet unconditionally, so make sure the import resolves
 * even in a checkout where the Kotlin docs have never been built.
 */
async function ensureGeneratedCss() {
    if (existsSync(GENERATED_CSS)) return
    await mkdir(ASSETS_OUT, { recursive: true })
    await writeFile(GENERATED_CSS, '/* Placeholder - run `npm run api` to generate the API reference. */\n', 'utf8')
}

async function api({ force = false, skipDokka = false } = {}) {
    if (skipDokka) {
        if (!existsSync(DOKKA_HTML)) throw new Error(`--skip-dokka was given but ${DOKKA_HTML} does not exist`)
        console.log('dokka: reusing existing output')
    } else if (force || (await dokkaIsStale())) {
        const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew'
        await run(gradlew, [':dokkaGenerate'], { cwd: repoDir })
    } else {
        console.log('dokka: output is up to date, skipping (use --force to regenerate)')
    }

    // Resolved through the website's own node_modules, so it honours the installed version.
    const cli = fileURLToPath(import.meta.resolve('@graphglue/dokka-vitepress/package.json'))
    await run(process.execPath, [
        path.join(path.dirname(cli), 'dist', 'cli.js'),
        '--src', DOKKA_HTML,
        '--out', API_OUT,
        '--assets', ASSETS_OUT,
        '--link-prefix', '/api/',
        '--modules', MODULES.join(',')
    ])
}

async function vitepress(command) {
    await ensureGeneratedCss()
    await run(process.execPath, [path.join(websiteDir, 'node_modules', 'vitepress', 'bin', 'vitepress.js'), command, '.'], {
        cwd: websiteDir
    })
}

const [command = 'dev', ...flags] = process.argv.slice(2)
const options = {
    force: flags.includes('--force'),
    skipDokka: flags.includes('--skip-dokka')
}

try {
    switch (command) {
        case 'api':
            await api(options)
            break
        case 'dev':
            if (!existsSync(API_OUT)) await api(options)
            await vitepress('dev')
            break
        case 'build':
            await api(options)
            await vitepress('build')
            break
        default:
            console.error(`unknown command: ${command}\nexpected one of: api, dev, build`)
            process.exitCode = 1
    }
} catch (error) {
    console.error(error instanceof Error ? error.message : error)
    process.exitCode = 1
}
