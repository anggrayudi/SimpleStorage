# SimpleStorage 3.0.0-alpha01 — On-Device Test Cases

> Target: emulator API 36+, branch `release/3.0.0`.
> Priority tags: **[P0]** = known gap, blocks beta if broken. **[P1]** = core behavior. **[P2]** = best effort.
> Execution: implement Groups 1–6 as instrumented tests under `storage/src/androidTest/`, run with
> `./gradlew :storage:connectedDebugAndroidTest`. Group 7 is driven via adb on the sample app.
> Fill the **Status** column with PASS / FAIL / BLOCKED plus a short note.

Notes for the implementer:
- Use `context.getExternalFilesDir(null)` (app-external storage) as the playground — no permission
  or SAF grant is required there, and `getBasePath()` resolves correctly since it is under
  `/storage/emulated/0`.
- Instrumented tests run on the instrumentation thread, so the main looper stays free for the
  conflict-resolver adapters (this is exactly what JVM tests could not cover).
- `TransferSpec.checkAvailableSpace` can stay `true` on the emulator (real StatFs).
- Clean up created files/MediaStore rows in `@After`.

## Group 1 — StorageFile factories & metadata

| ID | Pri | Case | Steps | Expected | Status |
|----|-----|------|-------|----------|--------|
| TC-01 | P1 | Raw file metadata | Create `a.txt` ("hello") in app-external dir; `StorageFile.from(context, file)` | `name=a.txt`, `isFile`, `exists`, `length=5`, `mimeType` text/plain or null, `absolutePath` non-null, `path.storageId=primary` | **PASS** — `StorageFileFactoryTest.tc01_rawFileMetadata`, all fields matched on-device (emulator-5554, API 37). |
| TC-02 | P1 | fromPath round-trip | `StorageFile.fromPath(context, file.absolutePath)` for existing file; and for a nonexistent path | Existing → resolves, same `uri` as TC-01; nonexistent → `null` | **PASS** — `StorageFileFactoryTest.tc02_fromPathRoundTrip`. |
| TC-03 | P0 | MediaStore backend | Insert a file into `MediaStore.Downloads` (resolver.insert + write bytes); `StorageFile.from(context, mediaUri)` | Returns MediaStore-backed instance: `isFile`, correct `name`/`length`; `openInputStream()` returns the written bytes | **PASS** — `StorageFileFactoryTest.tc03_mediaStoreBackend`; bytes verified byte-for-byte via `assertArrayEquals`. |
| TC-04 | P1 | Children & child() | Folder with 2 files + 1 subfolder; `list()`, `child("sub/x.txt")` | `list()` size 3; nested child resolves; missing child → null | **PASS** — `StorageFileFactoryTest.tc04_childrenAndChild`. |

## Group 2 — One-shot transfers (happy paths)

| ID | Pri | Case | Steps | Expected | Status |
|----|-----|------|-------|----------|--------|
| TC-10 | P1 | copyTo file | Copy 1 file into empty target folder | `TransferResult.Success`, content identical (checksum), source intact, `result.name` correct | **PASS** — `TransferHappyPathTest.tc10_copyToFile`; MD5 verified equal, source untouched. |
| TC-11 | P1 | moveTo file | Move 1 file | Success, content in target, source gone | **PASS** — `TransferHappyPathTest.tc11_moveToFile`. |
| TC-12 | P1 | copyTo folder recursive | Tree: 3 levels, 4 files, 1 empty folder. Copy with default spec (`skipEmptyFiles=true`) | Success; all 4 files present with identical content; document whether the empty folder is skipped | **PASS** — `TransferHappyPathTest.tc12_copyToFolderRecursive`; all 4 checksums matched. Observed: the empty folder (`subA/emptyFolder`) was **not** created in the target — `skipEmptyFiles=true` skips empty folders too, not just zero-length files (verified via on-device `File.list()`, logged "empty folder present in copy target = false"). |
| TC-13 | P1 | zip → unzip round-trip | Zip the TC-12 tree to `archive.zip`; unzip to a fresh folder | Both Success; extracted checksums match originals; `TransferStats.filesTransferred=4` | **PASS** — `TransferHappyPathTest.tc13_zipUnzipRoundTrip`; `stats.filesTransferred == 4` confirmed, all checksums matched. |
| TC-14 | P1 | Invalid target | `copyTo` where target is a FILE, not folder | `Failure(INVALID_TARGET)` | **PASS** — `TransferHappyPathTest.tc14_invalidTarget`. |
| TC-15 | P1 | Progress events | Copy a ~20 MB random file with `updateInterval=100`, collect `onProgress` | At least one `Progress` with `0 < percent <= 100` and `bytesPerSecond > 0`; document if engine thresholds suppress it | **PASS** — `TransferHappyPathTest.tc15_progressEvents`. Observed: exactly 1 progress event fired (`percent=0.234375, bytesTransferred=49152, bytesPerSecond=491520`) before completion — the emulator's virtual disk is fast enough that a 20 MB copy leaves only a narrow window for the 100ms timer, but it fired with valid values every run. |

## Group 3 — Conflict resolution (the critical gap: suspend→callback adapters)

> Tablet pass (2026-07-11): full `./gradlew :storage:connectedDebugAndroidTest` suite (all 7 groups,
> 23 instrumented tests) re-run on AVD `Tablet_10_inch` (API 35 / Android 15, `smallestScreenWidthDp`
> ≈ 800dp — large-screen/tablet form factor; this is the first tablet pass for this library). 23/23
> passed, 0 failures/errors — every test that was PASS on the phone emulator (`emulator-5554`,
> `Pixel_10_API_37`) stayed green here too; no regressions. Confirmed via the tablet's own per-device
> report at `storage/build/outputs/androidTest-results/connected/debug/TEST-Tablet_10_inch(AVD) -
> 15-_storage-.xml`.

| ID | Pri | Case | Steps | Expected | Status |
|----|-----|------|-------|----------|--------|
| TC-20 | P0 | REPLACE | Target already has `a.txt` (old content); copy new `a.txt` with `onConflict { REPLACE }` | Success; exactly one `a.txt` in target with NEW content | **PASS** — `ConflictResolutionTest.tc20_replace`. No deadlock: resolver ran on the instrumentation thread with a free main looper as predicted. |
| TC-21 | P0 | CREATE_NEW | Same setup, resolver returns `CREATE_NEW` | Success; target has `a.txt` (old) AND `a (1).txt` (new) | **PASS** — `ConflictResolutionTest.tc21_createNew`; both files present with correct content. |
| TC-22 | P0 | SKIP | Same setup, resolver returns `SKIP` | Since beta02: `TransferResult.Skipped(existingTarget)`; target untouched | **PASS** — tested on Tablet_10_inch (API 35 / Android 15). `ConflictResolutionTest.tc22_skip`; `result is TransferResult.Skipped` with `existingTarget.name == "a.txt"`. Independently confirmed via on-device evidence captured through `adb logcat` (not just the JUnit assertion): target dir listing = `[a.txt(11b)]` only, content = `OLD content` — no second file fabricated. Reproduced deterministically across 4 independent instrumentation runs. |
| TC-23 | P0 | Suspending resolver, no deadlock | Resolver does `withContext(Dispatchers.Main) { delay(300) }` before answering; wrap the whole op in `withTimeout(30s)` | Completes well before timeout; no ANR; resolution honored | **PASS** — `ConflictResolutionTest.tc23_suspendingResolverNoDeadlock`; completed in ~305ms (vs 30s timeout), confirming no deadlock on the instrumentation thread (this is the scenario that deadlocks under Robolectric). |
| TC-24 | P0 | Folder merge | Copy folder onto existing same-name folder containing one overlapping + one distinct file; resolver: `MERGE` for `Conflict.TargetFolder`, `REPLACE` for `Conflict.TargetFile` | Success; distinct files from both sides present; overlapping file has source content; resolver received TargetFolder first, then TargetFile(s) | **PASS after library fix** — `ConflictResolutionTest.tc24_folderMerge`. **Initially FAILED**: found and fixed a real library bug, see below. All content-level assertions (distinct files from both sides, overlapping file replaced with source content, resolver invoked TargetFolder then TargetFile) were already correct on disk even before the fix — only the reported `TransferResult` was wrong. |
| TC-25 | P1 | Resolver receives correct conflict info | In TC-20, capture `conflict.target` | `Conflict.TargetFile`, `target.name == "a.txt"`, `target.exists == true` | **PASS** — `ConflictResolutionTest.tc25_resolverReceivesCorrectConflictInfo`. |
| TC-26 | P0 | Folder parent SKIP (beta02) | Copy folder onto existing same-name folder; resolver returns `SKIP` for `Conflict.TargetFolder` | `TransferResult.Skipped(existingTarget = the folder)`; target untouched | **PASS** — tested on Tablet_10_inch (API 35 / Android 15). `ConflictResolutionTest.tc26_folderParentSkip`; `result is TransferResult.Skipped` with `existingTarget.name == "shared"`. Independently confirmed via on-device evidence captured through `adb logcat`: the entire target tree contains only `shared/common.txt(10b)` = `OLD common` — nothing else was created or modified, confirming the whole transfer aborted at the parent conflict. |
| TC-27 | P0 | Per-file SKIP in merge (beta02) | Merge folder; resolver: `MERGE` for folder, `SKIP` for the overlapping file | `Success` with `stats.filesSkipped == 1`; skipped file untouched; distinct file copied | **PASS** — tested on Tablet_10_inch (API 35 / Android 15). `ConflictResolutionTest.tc27_mergeWithPerFileSkip`; `result.isSuccess` with `stats.filesSkipped == 1`. Independently confirmed via on-device evidence captured through `adb logcat`: target tree = `shared/common.txt(10b)` = `OLD common` (untouched, skipped) + `shared/onlyInSource.txt(14b)` = `only in source` (copied in). Confirmed via the tablet's own JUnit XML report (`device="Tablet_10_inch(AVD) - 15"`) that this test actually executed there, not vacuously. |

## Group 4 — MediaStore transfers

| ID | Pri | Case | Steps | Expected | Status |
|----|-----|------|-------|----------|--------|
| TC-30 | P0 | MediaStore → folder copy | Using TC-03's media file: `mediaStorageFile.copyTo(appExternalFolder)` | Success; file lands in target with identical bytes | **PASS** — `MediaStoreTransferTest.tc30_mediaStoreToFolderCopy`; bytes verified byte-for-byte. |
| TC-31 | P2 | deleteRecursively on media | `delete()` / `deleteRecursively()` on the media-backed StorageFile | Returns true; MediaStore row gone | **PASS** — `MediaStoreTransferTest.tc31_deleteRecursivelyOnMedia`; confirmed the MediaStore row is gone via a direct `ContentResolver.query`. |

## Group 5 — Flow forms & cancellation

| ID | Pri | Case | Steps | Expected | Status |
|----|-----|------|-------|----------|--------|
| TC-40 | P1 | Event stream shape | Collect `copyToAsFlow` for a small file into a list | Ends with exactly ONE `Completed`; `Completed.result` is `Success`; no events after terminal | **PASS** — `FlowFormsTest.tc40_eventStreamShape`. |
| TC-41 | P1 | Cancellation | Launch collection of a ~50 MB copy, cancel the job at first `Progress` | Collection stops promptly (< 2 s); no crash; document target-file leftover state | **PASS** — `FlowFormsTest.tc41_cancellation`; `job.cancelAndJoin()` returned in well under 2s, no crash. Observed leftover state: the target file (`big.bin`) was **fully present** (52428800 of 52428800 bytes) — on this emulator's fast virtual disk, the underlying copy loop finished before the cancellation signal could interrupt it, so cancellation stopped the *event stream* promptly but did not truncate the file. A genuinely slower target (real device, network share) could still show a partial file; this wasn't reproducible here. |

## Group 6 — search (on-device regression of the 2.3.0 duplication fix)

| ID | Pri | Case | Steps | Expected | Status |
|----|-----|------|-------|----------|--------|
| TC-50 | P1 | Recursive search, no duplicates | Tree of 5 entries (3 files, 2 folders); `search(recursive=true)` terminal emission | Exactly 5 unique results | **PASS** — `SearchTest.tc50_recursiveSearchNoDuplicates`. Confirms the 2.3.0 `walkFileTreeForSearch` duplication fix (`fileTree.addAll(fileTree)` removed) holds on a real device, not just in the JVM simulation `ANALYSIS.md` was based on. |

## Group 7 — Sample app smoke via adb (uiautomator)

| ID | Pri | Case | Steps | Expected | Status |
|----|-----|------|-------|----------|--------|
| TC-60 | P1 | Install & launch | `./gradlew :sample:installLocalDebug`; launch MainActivity; screenshot | No crash; content below action bar, clear of status/gesture bars | **PASS** — installed via `installLocalDebug`, launched with `adb shell am start`; `dumpsys window` showed the activity focused, screenshot confirmed the "Simple Storage" toolbar and content render correctly below the status bar with no crash. |
| TC-61 | P2 | Legacy folder picker | Tap SELECT FOLDER, drive SAF UI ("Use this folder" → allow) via uiautomator | `onFolderSelected` toast/log fires; no crash (deprecated API still functional) | **PASS** — drove the full SAF flow via `uiautomator dump` + `adb shell input tap` (tapped SELECT FOLDER → navigated into Download/SimpleStorageTest, since the volume root and the bare Download folder are both rejected by DocumentsUI with "Can't use this folder" → USE THIS FOLDER → ALLOW on the grant dialog). Toast `"/storage/emulated/0/Download/SimpleStorageTest"` appeared, confirming `onFolderSelected` fired; app did not crash. Deprecated `SimpleStorageHelper.openFolderPicker` API confirmed functional on API 37 / minSdk 26 with the new AGP 9.2.1/Gradle 9.4.1/Kotlin 2.3.10 toolchain. |

## Library bugs found during this pass

### Confirmed and fixed: folder-merge conflict resolution silently reports failure despite full success

- **Symptom**: `TC-24` (`storage/src/androidTest/.../ConflictResolutionTest.kt`) initially failed:
  `copyTo` on a folder with a resolved content conflict (parent `MERGE` + a per-file `REPLACE`)
  returned `TransferResult.Failure(TransferErrorCode.UNKNOWN_IO_ERROR, "Transfer finished without a
  terminal event")` — **even though on-device inspection showed the merge fully succeeded**: all
  3 expected files (`common.txt` with replaced content, `onlyInSource.txt`, `onlyInTarget.txt`)
  were present and correct on disk.
- **Root cause**: `storage/src/main/java/com/anggrayudi/storage/file/DocumentFileExt.kt`, private
  `copyFolderTo` (the shared engine behind both `copyFolderTo` and `moveFolderTo`, since
  `moveFolderTo` just delegates to it with `deleteSourceWhenComplete=true`). A local `finalize`
  lambda (defined at line 2609) gates sending the terminal `SingleFolderResult.Completed` event on
  `!success || conflictedFiles.isEmpty()`. It is called once before content conflicts are resolved
  (line 2619, correctly skipping completion when conflicts are pending) and once more,
  unconditionally, after all conflicts have been resolved and copied (line 2681, immediately
  followed by `close()` at line 2682). The `conflictedFiles` `ArrayList` populated during the
  initial file walk was never cleared after its filtered copy (`solutions`) was processed, so the
  second `finalize()` call still saw a non-empty list and incorrectly concluded "conflicts still
  pending" — it skipped sending `Completed` and the flow closed silently with no terminal event at
  all. This is a genuine v2-engine bug, not something introduced by the v3 wrapper; it was invisible
  until now because this content-conflict path was never exercised by an automated test (JVM/Robolectric
  can't reach it — see the file header note about `Dispatchers.Main` deadlocking under Robolectric).
- **Fix applied** (small, unambiguous, separate commit): added `conflictedFiles.clear()` at line
  2645, right after the `solutions` list is derived from it, so the second `finalize()` call
  correctly recognizes completion. Nothing else reads `conflictedFiles` after that point in the
  function. Verified: `TC-24` passes after the fix, `./gradlew :storage:testDebugUnitTest` and the
  full `connectedDebugAndroidTest` suite (21/21) still pass.
- **Blast radius**: any `copyTo`/`moveTo` (v3) or `copyFolderTo`/`moveFolderTo` (v2) call where a
  folder-level conflict resolves to `MERGE` (or its v2 equivalent) **and** at least one file inside
  actually conflicts. Folder copies with no conflicts, or where the conflict resolves to
  `REPLACE`/`CREATE_NEW` (no merge, so no content-conflict scan), are unaffected — this is why
  `TC-12`/`TC-13` (no conflicts) passed both before and after the fix.

### Documented, not fixed: single-file SKIP produces the same "no terminal event" shape

- **Symptom**: `TC-22` — resolving a single-file conflict with `ConflictResolution.SKIP` returns
  `TransferResult.Failure(TransferErrorCode.UNKNOWN_IO_ERROR, "Transfer finished without a terminal
  event")`, deterministically (reproduced identically across two independent runs). The target is
  correctly left untouched, so there's no data-safety issue — but the reported result is
  indistinguishable from a real I/O error, which is a rough API edge.
- **Root cause hypothesis**: `DocumentFileExt.kt`'s single-file `copyFileTo` (private overload, line
  2887: `if (fileConflictResolution == SingleFileConflictCallback.ConflictResolution.SKIP) {
  return }`) returns without sending any `SingleFileResult` event at all when the conflict resolves
  to SKIP. The v3 wrapper's `TransferSpec.await()` (`StorageFileTransfer.kt`) then falls back to its
  generic "no terminal event" `Failure(UNKNOWN_IO_ERROR)` since it never saw a `Completed`.
- **Why not fixed**: unlike the TC-24 bug, this isn't an unambiguous defect — it's a product
  decision about what `TransferResult` SKIP *should* produce (e.g. a dedicated `SKIPPED` error code,
  or `Success` with a stats flag). That's a v3 API-shape change, out of scope for "small,
  unambiguous correction."
- **Related, unverified**: the same `finalize`-reuse pattern as the TC-24 bug also exists in the
  private multi-file engine behind `List<DocumentFile>.copyFilesTo`/`moveFilesTo`
  (`DocumentFileExt.kt` around lines 2094-2171, producing `MultipleFilesResult`). That code path is
  not reachable from any v3 `StorageFile` API (`StorageFileTransfer.kt` never imports
  `MultipleFilesResult`) and so is out of scope for this pass and was **not** reproduced or fixed —
  flagged here only because it shares the identical code shape as the confirmed TC-24 bug.

## Out of scope (documented, not tested here)

- `StorageAccessManager.ensureAccess`/`pickFolder`/`pickFiles`/`createFile`/`pickMedia` — require
  interactive SAF/Photo Picker UI; needs a dedicated UI-automation pass or manual QA.
- SD-card storage paths — emulator has no removable volume by default.
- `rememberLauncherForMediaPicker` — Compose UI test, separate pass.
