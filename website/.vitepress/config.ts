import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { defineConfig, type DefaultTheme } from 'vitepress'

/**
 * The API sidebar is generated from the Dokka output by `npm run api`. Loading it lazily keeps
 * the site buildable in a fresh checkout, where the Kotlin docs have not been generated yet.
 */
function apiSidebar(): DefaultTheme.SidebarItem[] {
    const file = fileURLToPath(new URL('./dokka/sidebar.json', import.meta.url))
    if (!existsSync(file)) return []
    return JSON.parse(readFileSync(file, 'utf8')) as DefaultTheme.SidebarItem[]
}

const api = apiSidebar()

export default defineConfig({
    title: 'GraphGlue',
    description:
        'Annotation-based, code-first GraphQL servers on Kotlin, Spring Boot and Neo4j - with connections, filtering, ordering and authorization built in.',
    lang: 'en-US',

    srcDir: 'docs',
    cleanUrls: true,
    lastUpdated: true,
    appearance: 'dark',

    head: [
        ['link', { rel: 'icon', href: '/img/logo.svg', type: 'image/svg+xml' }],
        ['meta', { name: 'theme-color', content: '#7f52ff' }],
        ['meta', { property: 'og:type', content: 'website' }],
        ['meta', { property: 'og:site_name', content: 'GraphGlue' }]
    ],

    sitemap: {
        hostname: 'https://graphglue.github.io'
    },

    themeConfig: {
        logo: '/img/logo.svg',

        nav: [
            { text: 'Guide', link: '/docs/', activeMatch: '^/docs/' },
            { text: 'API', link: api.length > 0 ? '/api/' : '/docs/', activeMatch: '^/api/' },
            {
                text: 'Ecosystem',
                items: [
                    { text: 'Neo4j authorization plugin', link: 'https://github.com/graphglue/authorization-procedure' },
                    { text: 'GraphQL Kotlin', link: 'https://opensource.expediagroup.com/graphql-kotlin' },
                    { text: 'Spring Data Neo4j', link: 'https://spring.io/projects/spring-data-neo4j' }
                ]
            }
        ],

        sidebar: {
            '/docs/': [
                {
                    text: 'Guide',
                    items: [
                        { text: 'Getting started', link: '/docs/' },
                        { text: 'Modeling', link: '/docs/modeling' },
                        { text: 'Authorization', link: '/docs/authorization' },
                        { text: 'Connections', link: '/docs/connections' },
                        { text: 'Configuration properties', link: '/docs/configuration' }
                    ]
                }
            ],
            '/api/': [{ text: 'API Reference', link: '/api/', items: api }]
        },

        search: {
            provider: 'local'
        },

        socialLinks: [{ icon: 'github', link: 'https://github.com/graphglue/graph-glue' }],

        editLink: {
            pattern: 'https://github.com/graphglue/graph-glue/edit/main/website/docs/:path',
            text: 'Edit this page on GitHub'
        },

        outline: [2, 3],

        footer: {
            message: 'Released under the Apache 2.0 License.',
            copyright: 'Software Quality and Architecture - University of Stuttgart'
        }
    },

    markdown: {
        theme: {
            light: 'github-light',
            dark: 'github-dark'
        },
        // Most snippets in the guide are Kotlin.
        languages: ['kotlin', 'graphql', 'groovy', 'xml']
    }
})
