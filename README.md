# Flop Twitch Giveaway (Fabric 1.21.11, client-side)

On-stream DonutSMP giveaways from Twitch chat. Viewers type `!<IGN>` (e.g. `!Steve`), one entry per viewer and one per IGN.

## Build the .jar
Push this folder to a GitHub repo -> Actions tab -> "Build" -> download the `flop-twitch-giveaway` artifact.
Or locally: `./gradlew build` -> `build/libs/flop-twitch-giveaway-1.0.0.jar`. Needs Fabric API in your mods folder.

## Use
- Press **O** (rebindable in Controls) to open the menu.
- **Connections**: type your Twitch channel name -> Connect. No login needed (read-only chat).
- **Streamlabs** (optional): Edit -> paste your *Socket API token* (streamlabs.com -> Settings -> API Settings -> API Tokens) -> Connect.
  Shows donations/subs/bits/raids in the activity feed; donations whose message contains `!<IGN>` can enter too (toggle).
- **Giveaway**: set Prize, Duration (+/- or 1m/3m/5m/10m), Winners (1-20), Subs only -> Start.
- Countdown card shows on your HUD (top centre) with entries + live "+ Name entered". Last 5 seconds tick.
- When time runs out the wheel opens and spins; with several winners it spins once per winner.
  Afterwards: **Reroll last** (winner not online), **Copy names**, **Done**.
- Hover an entry in the list -> **remove** to disqualify it.
- Accent colour dots + HUD toggle in the top-right of the menu. Settings save to `config/flopgiveaway.json`.

Java names: `!Steve` (3-16 letters/digits/_). Bedrock names: `!.Gamertag` (spaces become `_`, like Geyser/Floodgate).
If a viewer types their name wrong, they just type it again: their old entry is replaced (same spot in the list). A name can only belong to one viewer, and viewers you remove can't re-enter.
 Bot commands like !discord, !uptime are ignored.
