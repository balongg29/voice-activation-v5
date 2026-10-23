# Voice System

A client-side Fabric mod for Minecraft 26.2. Say a word, and it presses a keybind for you.
Out of the box, saying **"system open"** presses **K** (your checklist key).
Triggers can be single words or short phrases.

## Setup

1. Install Java 25 and Fabric Loader 0.19.3+, plus Fabric API 0.152.0+26.2.
2. Build the mod: `./gradlew build` (the jar ends up in `build/libs/`).
3. Download the small English speech model, `vosk-model-small-en-us-0.15`, from
   https://alphacephei.com/vosk/models and unzip it.
4. Put the **contents** of the unzipped folder into
   `.minecraft/config/voicesystem-model/` (so `am`, `conf`, `graph`... sit directly inside it).
5. Start the game once. It creates `config/voicesystem.json`.

## Changing the key in-game (Mod Menu)

Install Mod Menu 20.0.1+ (optional). In the mod list, open Voice System and click Configure.
Click the button, press the key you want, and saying "system" will press that key from then on.
The change is saved and applies immediately, no restart needed.

## Config

`config/voicesystem.json`:

```json
{
  "modelFolder": "voicesystem-model",
  "triggers": [
    { "word": "system open", "key": "key.keyboard.k", "holdMillis": 50 }
  ]
}
```

Add more entries to `triggers` to add more words. `key` is a Minecraft key name such as
`key.keyboard.j`, `key.keyboard.left.shift` or `key.mouse.left`. Restart the game after editing.

## Simple Voice Chat

If Simple Voice Chat is installed, Voice System listens through its microphone audio instead of
opening the mic itself, so the two never fight over the mic. Simple Voice Chat must be set to
voice activation (not push-to-talk) and connected, which also works in singleplayer.
Set `"useSimpleVoiceChat": false` in the config to open the mic directly instead.
The log line "Heard: '...'" shows what the recognizer picked up.

## Notes

- Listening starts automatically when the game launches, and stays on.
- Only words listed in the config are recognized, which keeps it accurate and fast.
- Some servers treat automated input as cheating. Check the rules before using it online.
