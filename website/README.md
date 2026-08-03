# GraphGlue website

The documentation site at [graphglue.github.io](https://graphglue.github.io), built with
[VitePress](https://vitepress.dev). The guide lives in `docs/`; the API reference under `docs/api/`
is generated from the Dokka output by
[`@graphglue/dokka-vitepress`](https://github.com/graphglue/dokka-vitepress) and is not checked in.

## Prerequisites

- Node.js 20 or newer
- A JDK, for the Gradle run that produces the Dokka output

The transform package is an ordinary npm dependency, released from
[graphglue/dokka-vitepress](https://github.com/graphglue/dokka-vitepress). To try local changes to
it, `npm link` a checkout or point the dependency at it with `file:`.

## Getting started

```sh
cd graph-glue/website

npm install
npm run dev      # generate the API reference if missing, then serve on localhost:5173
```

## Commands

| Command | What it does |
| --- | --- |
| `npm run api` | Runs `./gradlew :dokkaGenerate` and transforms the output into `docs/api/` and `.vitepress/dokka/` |
| `npm run dev` | Generates the API reference if it is missing, then starts the dev server |
| `npm run build` | Regenerates everything and builds the static site into `.vitepress/dist` |
| `npm run preview` | Serves the last build |

`npm run api` skips Gradle when `build/dokka/html` is already newer than the Kotlin sources. Pass
`--force` to regenerate anyway, or `--skip-dokka` to reuse the existing output:

```sh
npm run api -- --force
npm run api -- --skip-dokka
```

Editing the guide only needs `npm run dev`; the API reference does not have to be rebuilt.

## Layout

```
website/
├── docs/                   VitePress srcDir
│   ├── index.md            landing page
│   ├── docs/               the guide
│   ├── api/                generated - the API reference
│   └── public/             static assets
├── .vitepress/
│   ├── config.ts           site configuration
│   ├── theme/              theme extension and brand styles
│   └── dokka/              generated - stylesheet, icons, fonts, sidebar, search index
└── scripts/docs.mjs        the script behind every npm script above
```

## Deployment

Pushing to `main` runs [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml), which
performs the same steps as `npm run build` and publishes `.vitepress/dist` to
[graphglue/graphglue.github.io](https://github.com/graphglue/graphglue.github.io). Pull requests
build the site without deploying it.
