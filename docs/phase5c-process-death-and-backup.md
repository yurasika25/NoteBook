# Phase 5C: process-death and backup validation

These procedures use synthetic notes only. They target only `emulator-5554`; stop if
`adb devices -l` shows a different device or a physical device. They do not uninstall,
clear data, or restore over an installation.

## Device and backup inspection

```sh
adb devices -l
adb -s emulator-5554 shell getprop ro.build.version.sdk   # must be 31
adb -s emulator-5554 shell getprop ro.product.model
adb -s emulator-5554 shell pidof com.notes.easynotebook
adb -s emulator-5554 shell bmgr list transports
adb -s emulator-5554 shell dumpsys backup | sed -n '1,220p'
adb -s emulator-5554 shell dumpsys package com.notes.easynotebook | rg -i 'allowBackup|fullBackup|backupAgent|dataExtraction'
```

Expected application storage is `databases/NoteBook.db`,
`files/note_editor_drafts_*`, and `shared_prefs/note_editor_drafts_*`. Do not use
`run-as` to modify them during inspection.

## Confirmed process-death scenarios

For each scenario, record the PID before backgrounding, then run:

```sh
adb -s emulator-5554 shell input keyevent KEYCODE_HOME
before=$(adb -s emulator-5554 shell pidof com.notes.easynotebook | tr -d '\r')
test -n "$before" || { echo 'FAIL: no target PID'; exit 1; }
adb -s emulator-5554 shell am kill com.notes.easynotebook
sleep 2
after=$(adb -s emulator-5554 shell pidof com.notes.easynotebook | tr -d '\r')
test -z "$after" || { echo "FAIL: process survived ($after)"; exit 1; }
adb -s emulator-5554 shell monkey -p com.notes.easynotebook 1
```

Before killing, create synthetic data through the UI and wait at least 1 second
after the final edit so the 500 ms draft debounce has completed. Verify after relaunch:

1. Saved note: unchanged title/body.
2. Existing-note edit: edited draft is restored.
3. Unfinished new note: draft appears and restores exactly.
4. Saved note plus old draft: committed content remains authoritative.
5. Two unfinished new notes: both remain independently recoverable.

Repeat one case with termination before 500 ms. The expected behavior is that the
latest edit is not guaranteed durable; only the last completed AtomicFile write is
recoverable. This is a durability boundary, not a test failure.

`am kill` is required for this test. Activity recreation and force-stop are not
substitutes for confirmed process death.

## Backup assessment

`allowBackup=true` with `fullBackupOnly=true` and no explicit rules means inclusion
and exclusion are platform/transport dependent. `bmgr list transports` and
`dumpsys backup` establish availability only; they do not prove a restore.

A real restore test requires explicit approval because it must use an isolated test
installation and may overwrite package state. The safe plan is: create a separate
AVD/package install with synthetic data, trigger a backup with the selected transport,
remove only that isolated package, restore it, then compare note and draft SHA-256
values. Do not run this against the current emulator without approval.

## UI stress procedure

Generate deterministic UTF-8 text files of 100 KiB, 500 KiB, and 1 MiB containing
Unicode, long lines, bullets, combining marks, and supplementary characters. Paste
each through the editor and record time-to-paste, cursor movement, selection,
scrolling, save/reopen, background/foreground, and IME behavior. Capture
`dumpsys meminfo com.notes.easynotebook` before/after each workload. Attempt 5 MiB
only if all smaller workloads remain responsive. SQLite round-trip tests do not
substitute for this UI measurement.
