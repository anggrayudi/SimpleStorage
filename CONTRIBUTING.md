# Contributing to SimpleStorage

Thanks for helping out. This document covers the two things reviewers ask about most — code
formatting and comments — plus the checks a change is expected to pass.

## Building

* minSdk 26, compileSdk/targetSdk 37, Java 11 source/target level.
* `./gradlew :storage:assembleDebug :storage-compose:assembleDebug` builds the libraries.
* `:storage` and `:storage-compose` compile with `-Xexplicit-api=strict`, so every public
  declaration needs an explicit visibility modifier and an explicit return type. Test sources are
  exempt — the flag is applied through a task filter, not through the Android DSL, precisely so it
  does not force modifiers onto every test class.

## Formatting: ktfmt, Google style

All Kotlin code is formatted with [ktfmt](https://github.com/facebook/ktfmt) in **Google style** —
2-space indent, 100-column limit.

```bash
ktfmt --google-style path/to/File.kt
```

**Format only the files your change touches.** The tree was formatted with older ktfmt releases, and
newer ones reflow code they consider differently — running ktfmt across the whole repository
rewrites thousands of unrelated lines and buries the actual change. A pull request that reformats
files it does not otherwise modify will be asked to drop those hunks.

After running ktfmt, re-read the comments in the files you touched. ktfmt re-wraps `//` text to the
column limit and can split a phrase across lines in a way that reads badly; if that happens, reword
the comment so each line stands on its own rather than leaving the machine-split version.

## Comments: write the ones the code cannot replace

A comment earns its place by carrying something the reader cannot get from the code next to it.
Default to no comment, and write one when the *why* is genuinely hard to recover.

**Cut these — they cost attention and give nothing back:**

* Field, parameter or method docs that restate the name. The signature already said it.
* A summary above self-evident code — a plain `when`, a `.ceil()` on a division, a five-line test
  helper whose body is right there.
* The same explanation in two places. Keep it where a reader would actually ask the question,
  usually where the surprising thing happens rather than on the field it touches.
* Restating *what* a block does. If the code needs that, rename or restructure it instead.

**Keep these, even when they cost lines:**

* Framework or language traps a future edit would walk straight back into.
* Deliberate deviations from a design or a ticket, and why — including what enforces the choice,
  such as a test or a measurement.
* Code that looks removable but is load-bearing: an odd-looking constant, an intentionally absent
  call, a guard that exists for one platform version.
* Product decisions — behaviour chosen deliberately rather than derivable from the code.
* Bug and security notes: what broke, what an attacker could do, why the guard exists.

**Keep them short.** Two or three lines is usually enough. If a comment needs a paragraph, the
explanation probably belongs in a doc rather than inline. Re-read what you wrote and delete every
sentence the code already proves.

One exception worth stating, because it cuts against the first rule: **public KDoc on this library's
API is shipped documentation**, not an inline comment. It appears in IDE hover and in the generated
reference, so a short doc on a public declaration is welcome even when the name is descriptive.
The "don't restate the name" rule applies with full force to internal and private declarations, and
to `//` comments everywhere.

## Tests

* Unit tests (`./gradlew :storage:testDebugUnitTest`) use Robolectric and cover path parsing, MIME
  types, contracts and anything expressible without a device.
* Instrumentation tests carry the behaviour that only a device can prove — copy/move/zip across
  backends, conflict resolution, SAF pickers. Run them with
  `./gradlew :storage:connectedDebugAndroidTest`, or install
  `storage/build/outputs/apk/androidTest/debug/storage-debug-androidTest.apk` and drive it with
  `adb shell am instrument` for faster iteration.
* [`V3_TEST_CASES.md`](V3_TEST_CASES.md) is the log: each case records what was run, on which
  device, and what the output was. Add your case there when you add a test.
* Some tests need a prepared device — an unlocked screen for the picker group, a physical removable
  drive for the OTG probes (opt-in with `-e otgProbe true` so they skip otherwise). Those
  prerequisites are documented per group in `V3_TEST_CASES.md`.

When you fix a bug, make the test fail first without the fix and say so in the pull request. A test
that passes both with and without the change proves nothing, and that is easy to ship by accident.

## Commits and pull requests

* Conventional commit style for the subject: `feat:`, `fix:`, `chore:`, `refactor:`, `test:`,
  `docs:`, imperative mood.
* Explain in the body what the change does and what it does *not* cover — an untested branch or an
  assumption you could not verify is worth a sentence.
* Public API changes belong in [`MIGRATION.md`](MIGRATION.md), and anything the 2.x surface still
  owns is listed there too.
