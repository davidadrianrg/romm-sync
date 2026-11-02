# Atril — working agreement for coding agents

## Branches (non-negotiable)

- `master` is stable. Never develop directly on it.
- `develop` is the integration branch. Daily work starts from `develop`.
- `feat/<short-slug>` is temporary: one task, created from a freshly pulled `develop`.
- When the task is verified, merge it into `develop` with a merge commit (`git merge --no-ff feat/<slug>`), push `develop`, and delete the feature branch **locally and on origin** immediately. Do not leave orphan branches.
- Never merge `develop` into `master` or publish a release unless the user explicitly asks. CI publishes develop builds as a draft release only.
- Useful commit format: `type(scope): summary` (feat, fix, chore, ci, docs). Keep commits small and push frequently.

## Testing on both platforms

Every user-facing change must be checked on Android and Linux, preserving parity.

### Android

- Emulator AVD: `romm_test` (API 34). Check whether one is already running before launching another:

```bash
export ANDROID_HOME="$HOME/.hermes/android-sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
adb devices -l
```

If no emulator is running:

```bash
DISPLAY=:0 "$ANDROID_HOME/emulator/emulator" -avd romm_test -gpu host -accel on -no-audio -no-snapshot-save
```

Use a window, not `-no-window` (headless crashes in this environment). Build and install:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Exercise the real UI with `adb shell input`/`uiautomator` or screenshots. Check library sync, covers, launcher cards/physical media, details, downloads and saves.

### Linux

- Run the Compose desktop app with the graphical session:

```bash
DISPLAY=:0 ./gradlew :desktop:run
```

- Screenshots: use `grim`, never ImageMagick `import` (it fails in this sway setup). Get the window geometry with `swaymsg -t get_tree` and crop with `grim -g 'x,y wxh'`.
- Verify the Play screen fills the window, covers load, the insertion animation plays, and clearing/cancelling downloads removes notifications.

### Tests

This host has no system JDK; Gradle needs JDK 17 from `~/.hermes/jdk` (otherwise it fails with "Cannot find a Java installation ... languageVersion=17"):

```bash
export JAVA_HOME="$HOME/.hermes/jdk" ANDROID_HOME="$HOME/.hermes/android-sdk"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :core:test :desktop:test testDebugUnitTest
```

Tips for the Linux desktop run under sway: the app relaunches its own JVM with `_JAVA_AWT_WM_NONREPARENTING=1` (see `TilingWmWorkaround`), so `desktop:run` shows two `java` processes. Never `pkill -f` by command line from a script — it can match the calling shell; kill the specific PIDs instead.

## Current focus

- RomM covers: prefer the local `path_cover_large`/`path_cover_small` asset; percent-encode timestamps containing spaces; send a proper User-Agent.
- Launcher: card/physical media scaling, insertion animation, fill the window.
- Downloads: no stale notification after clearing/cancelling the queue.
- Keep Android and Linux behavior aligned; avoid feature work on only one platform.

## Working loop

On each iteration: inspect `git status`, recent commits and CI/logs; pick one concrete, reproducible bug; reproduce it on both platforms when relevant; make the smallest correct fix; run the relevant tests; merge through a temporary `feat/*` branch; push `develop`; delete the branch; then continue with the next finding. Do not rewrite history, delete user data, or touch `master`/releases unless explicitly instructed.
