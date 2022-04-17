// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require('prism-react-renderer/themes/github');
const darkCodeTheme = require('prism-react-renderer/themes/dracula');

/** @type {import('@docusaurus/types').Config} */
const config = {
    title: 'graph-glue',
    tagline: 'Dinosaurs are cool',
    url: 'https://your-docusaurus-test-site.com',
    baseUrl: '/graph-glue/',
    onBrokenLinks: 'throw',
    onBrokenMarkdownLinks: 'throw',
    onDuplicateRoutes: 'throw',
    organizationName: 'graphglue',
    projectName: 'graph-glue',
    trailingSlash: false,

    presets: [
        [
            '@docusaurus/preset-classic',
            /** @type {import('@docusaurus/preset-classic').Options} */
            ({
                docs: {
                    sidebarPath: require.resolve('./sidebars.js'),
                    routeBasePath: '/',
                },
                blog: false,
                theme: {
                    customCss: [require.resolve('./src/css/custom.css')],
                },
            }),
        ],
    ],

    themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
        ({
        colorMode: {
            defaultMode: 'dark',
        },
        navbar: {
            title: 'GraphGlue',
            items: [{
                    type: 'doc',
                    docId: 'docs',
                    position: 'left',
                    label: 'Docs',
                },
                {
                    href: 'https://github.com/graphglue/graph-glue',
                    label: 'GitHub',
                    position: 'right',
                },
            ],
        },
        footer: {
            style: 'dark',
            copyright: `Built with Docusaurus.`,
        },
        prism: {
            theme: lightCodeTheme,
            darkTheme: darkCodeTheme,
            defaultLanguage: 'kotlin',
            additionalLanguages: ['kotlin'],
        },
    }),
};

module.exports = config;