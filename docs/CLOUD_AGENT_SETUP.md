# Building Meta VR apps with Claude Code cloud agents

How to set up a Claude Code cloud environment so an agent can build a Meta VR
Glasses / Quest app end to end, with Meta's tooling (the `metavr` CLI, its MCP
server and the Meta VR agent skills) and the Android SDK available and unblocked.

Three things must be in place. Missing any one of them is what stalls an agent.

## 1. Environment settings (claude.ai, once per environment)

Open the cloud environment menu in the session's title bar, then **Edit**.

**Network access → Custom**, keep the default package-manager list, and add:

| Host | Used for |
| --- | --- |
| `developers.meta.com`, `wearables.developer.meta.com` | Meta developer docs (`llms.txt`, `metavr docs search`) |
| `securecdn.oculus.com` | `metavr` native binary download |
| `dl.google.com`, `maven.google.com` | Android SDK and Google Maven (AGP, Compose, AndroidX) |
| `repo.maven.apache.org`, `plugins.gradle.org`, `services.gradle.org` | Gradle, Kotlin, Maven Central (Meta UI Set, Spatial SDK) |
| `github.com`, `raw.githubusercontent.com` | Meta samples and skills (`meta-quest/*`) |

**Permission mode:** start tasks in **Auto** (mode dropdown next to the prompt box).
Bypass mode isn't offered in cloud sessions; allow rules (below) are what let
`metavr`, Gradle and `sdkmanager` run without being stopped.

Steps: <https://code.claude.com/docs/en/cloud-environments#network-access>

## 2. Repository files (commit these)

An agent can't grant itself permissions, so a person commits these files.

### `.claude/settings.json`

```json
{
  "extraKnownMarketplaces": {
    "meta-vr": { "source": { "source": "github", "repo": "meta-quest/agentic-tools" } }
  },
  "enabledPlugins": { "meta-vr@meta-vr": true },
  "env": { "NODE_USE_ENV_PROXY": "1" },
  "permissions": {
    "allow": [
      "Bash(metavr *)",
      "Bash(npx -y metavr *)",
      "Bash(npx -y metavr@latest *)",
      "Bash(./gradlew *)",
      "Bash(sdkmanager *)",
      "Bash(adb *)",
      "Bash(git clone https://github.com/meta-quest/*)",
      "WebFetch(domain:developers.meta.com)"
    ]
  },
  "hooks": {
    "SessionStart": [
      { "hooks": [ { "type": "command", "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/session-start.sh" } ] }
    ]
  }
}
```

- The `meta-vr` plugin brings Meta's skills (`hz-quest-verify-first`,
  `hz-new-project-creation`, `hz-metavrx-ui-set`, …) and the `metavr` MCP server.
- `NODE_USE_ENV_PROXY=1` matters: Node's built-in `fetch` ignores the cloud proxy
  without it, and `metavr`'s npm launcher then fails to download its binary
  (HTTP 403).

### `.claude/hooks/session-start.sh`

See [this repo's hook](../.claude/hooks/session-start.sh). In cloud sessions it
installs the Android command-line tools, platform 36 and build-tools, exports
`ANDROID_HOME` and the Node proxy settings, fetches the `metavr` binary, and warms
Gradle. The container is cached after it runs, so later sessions start fast.

## 3. Starter prompt

Paste this into a new cloud session on a repo that has the files above (an empty
repo plus the two files is enough):

```text
Build a Meta VR app that runs on Meta VR Glasses and Meta Quest: <describe the app>.

Use Meta's official tooling, not memory:
- Start with the hz-quest-verify-first skill to pick the build path, then
  hz-new-project-creation. Use the other hz-* skills as they apply
  (hz-metavrx-ui-set for 2D UI, hz-spatial-sdk for immersive, hz-vr-debug).
- Verify every manifest entry, SDK version and Meta API with `metavr docs search`
  / `metavr docs fetch` (or https://developers.meta.com/llms.txt) before using it.
- Copy version sets from Meta's current samples (github.com/meta-quest).

Requirements:
- Supports Meta VR Glasses (Look and Pinch) and Quest (controllers and hands).
- Builds with ./gradlew; add a GitHub Actions workflow that builds the APK.
- Run `metavr doctor` first and tell me about anything missing.

Commit as you go, push to the working branch, and open a draft PR.
```

## Known limits

- `metavr tools install android-sdk` / `jdk` are gated per Meta account; the hook
  installs the Android SDK from Google directly instead.
- No headset is attached to a cloud container, so device commands (`metavr app
  install`, `metavr capture`, …) need a local machine. Meta Spatial Simulator
  needs a desktop GUI as well.
- `metavr` stores its login in a system keyring, which containers don't have; it
  warns and runs without a login.
