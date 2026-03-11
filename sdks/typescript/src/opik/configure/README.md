# Opik TS ✨

The Opik TS tool helps you quickly add Opik SDK to your Node.js project for LLM
observability and tracing.

> **⚠️ Experimental:** This tool is still in an experimental phase.

# Usage

## Quick Start

```bash
npx opik-ts configure
```

This will guide you through the setup process interactively.

For Opik Cloud or self-hosted Comet, the workspace prompt is optional during
configure. If you provide an API key and omit `--workspace`, the CLI will fetch
and use your default workspace automatically.

## Quick Local Setup

For local development with a local Opik instance:

```bash
npx opik-ts configure --use-local
```

This skips API key and workspace prompts and automatically configures for local
deployment (http://localhost:5173/api).

## Non-interactive setup

For agents, CI, or scripted setup, pass overrides directly:

```bash
npx opik-ts configure \
  --deployment-type cloud \
  --api-key "$OPIK_API_KEY" \
  --project-name "Default Project" \
  --package-manager pnpm
```

You can also provide the same values through `OPIK_TS_*` environment variables.

# Options

The following CLI arguments are available:

| Option              | Description                                                                                       | Type    | Default | Environment Variable      |
| ------------------- | ------------------------------------------------------------------------------------------------- | ------- | ------- | ------------------------- |
| `--help`            | Show help                                                                                         | boolean |         |                           |
| `--version`         | Show version number                                                                               | boolean |         |                           |
| `--debug`           | Enable verbose logging                                                                            | boolean | `false` | `OPIK_TS_DEBUG`           |
| `--default`         | Use default options for all prompts                                                               | boolean | `true`  | `OPIK_TS_DEFAULT`         |
| `--force-install`   | Force install packages even if peer dependency checks fail                                        | boolean | `false` | `OPIK_TS_FORCE_INSTALL`   |
| `--install-dir`     | Directory to install Opik SDK in                                                                  | string  |         | `OPIK_TS_INSTALL_DIR`     |
| `--use-local`       | Configure for local deployment (skips API key/workspace setup prompts)                            | boolean | `false` | `OPIK_TS_USE_LOCAL`       |
| `--deployment-type` | Configure a specific deployment without prompting: `cloud`, `self-hosted`, or `local`             | string  |         | `OPIK_TS_DEPLOYMENT_TYPE` |
| `--url`             | Base URL for your Opik instance. Required for self-hosted in non-interactive mode                 | string  |         | `OPIK_TS_URL`             |
| `--api-key`         | Opik API key for cloud or self-hosted setup                                                       | string  |         | `OPIK_TS_API_KEY`         |
| `--workspace`       | Workspace override for cloud or self-hosted setup. If omitted, configure uses the API key default | string  |         | `OPIK_TS_WORKSPACE`       |
| `--project-name`    | Project name to write into the generated configuration                                            | string  |         | `OPIK_TS_PROJECT_NAME`    |
| `--package-manager` | Package manager to use when auto-detection is ambiguous: `npm`, `pnpm`, `yarn`, or `bun`          | string  |         | `OPIK_TS_PACKAGE_MANAGER` |

# Development

To develop the CLI locally:

```bash
pnpm try --install-dir=[a path]
```

To build and use the tool globally:

```bash
pnpm build
pnpm link --global
opik-ts [options]
```

## Contributing

This CLI is part of the Opik project. For contributing guidelines, please see
the main Opik repository.
