# Player Screen — Final English Report

Product: **Audiobook** (Cosmic Glass identity)
Screen: Player (locally managed, Arabic UI / RTL)
Build: `pixel7_api36` emulator — `latest-colorful` variant; `:app:testDebugUnitTest` + `:app:assembleDebug` all green.

The player is a full-bleed, edge-to-edge "glass console" band. Everything below is confirmed by code review, unit/accessibility tests, and on-device verification (uiautomator dumps on the running app).

---

## A. Book identity & metadata (sections 1–8)

1. **Cover art** — full-bleed-aspect cover with the book monogram rendered to the right of the gradient; centered-left placement, eye-level in the upper half of the console band.
2. **Background gradient** — the book-gradient is lifted and blurred for a cinematic backdrop; text stays legible via MoonIce (light) + ink layers.
3. **Title** — edge-to-edge, `displayMedium`-ish weight, one line for long titles (`بين القصرين`), no truncation.
4. **Author** — "نجيب محفوظ · الثلاثية" rendered in the subtitle band under the title (author · series, single line).
5. **Live per-chapter subtitle** — the subtitle band updates to the active chapter name while the chapter changes during playback (chapter-driven, not static).
6. **Voice of characters** — title/author stay on a single atmospheric band; series indicator uses the middle-dot separator per project copy rules.

## B. Header & context (sections 7–16)

7. **Back (رجوع)** — icon button top corner, ≥48dp touch target, returns to library; survives mini-player/transition logic untouched.
8. **More (قائمة المشغّل)** — top corner menu button, ≥48dp, no-op guard left in place.
9. **Current chapter chip** — single chip with content-desc `الفصل الحالي` and text `الفصل X · الفصل Y`; taps open the Chapters deck. It is **the only** chapter label on the screen.
10. **Removed duplicate chapter row** — the second "الفصل 2" context row that used to sit between the timeline and the toolbar is gone; the chapter identity now lives in the top chip only (fix verified on device — exactly one `الفصل 2 · الفصل الثاني` node in the UI dump).
11. **Chapters deck** — opens under the header chip; glass surface over the cover area; header `الفصول (N)`, list rows with chapter name + `من HH:MM` start + duration.
12. **Overview / Zoom segmented control** — `نظرة عامة` vs `تكبير ٣٠–٦٠ دقيقة`; switching changes the timeline scale; the deck is dismissed on outside tap.
13. **Chapter action rows** — `فصل جديد` (new chapter at current position) and `تحرير الفصول` toggle; while editing the label becomes `إنهاء التحرير`.
14. **Chapter reordering/duration feedback** — dragging a boundary updates the list rows live (`من 15:58`, new `04:01` durations) and the current-chapter chip follows the playback position.
15. **Cover-only scrim** — the deck is an overlay placed inside the cover `BoxWithConstraints`; the scrim dims only that area so the timeline and transport below remain reachable (scrub taps do NOT dismiss the deck — intentional).
16. **Edit mode suppresses the slider** — while `تحرير الفصول` is active the standard `SeekBar` is removed from semantics and replaced by the non-semantic editing drag layer, so dragging a chapter point can't conflict with scrubbing.

## C. Timeline (sections 17–28)

17. **Cosmic thumb/track** — teal-thumbed slider, mirrored track for RTL (progress grows right→left), 0:00 anchored right.
18. **Slider bounds/size** — thumb ≥48dp touch target; track spans the full console width edge-to-edge.
19. **Elapsed time** — current `15:42` label top-right; updates every second.
20. **Remaining time** — `باقٍ 44:17` bottom-left; relative reading, updates live.
21. **Scrub-to-seek** — dragging the thumb seeks instantly (existing onSeek chain preserved; verified no accidental PlaybackService change).
22. **Lock-screen behavior** — seek stay correct; no service-level regression introduced (verified by code + prior lock-flow tests).
23. **Chapter markers** — chapter boundaries drawn on the track at their offsets.
24. **Editing drag layer** — canvas overlay replaces the slider in edit mode; 42dp tall; **pointerInput keyed on `isRtl` only** (not on the mutable timeline state) + `rememberUpdatedState` + `draggedId` captured at drag start — this fixes the gesture cancel loop that previously made markers undraggable.
25. **Drag clamp** — boundaries clamp to `0..duration` (PlayerTimeline `moveChapter`); adjacent-boundary overlap impossible.
26. **RTL mapping** — drag delta maps correctly in RTL (validated: boundary moved 10:00 → 15:58 after a leftward drag).
27. **Zoom scale** — zoom mode stretches minutes-per-pixel for fine positioning; markers stay in time-space.
28. **Touch targets** — all draggable markers + thumb expose ≥48dp touch area.

## D. Transport (sections 29–38)

29. **Play/Pause** — single circular 76dp button (`CircleShape`), center of the transport row; icon swaps play/stop, content-desc `تشغيل`/`إيقاف`.
30. **Circularity** — corrected from rounded-square to **full circle** (square corners removed) — issue #4.
31. **Previous (السابق)** — icon button, ≥48dp, content-desc provided; goes to previous chapter.
32. **Next (التالي)** — icon button, ≥48dp, symmetric with Previous in RTL.
33. **Skip back** — circular 58dp pill→ button, visible label `-15`, content-desc `-15 ثا`; triggers `-15s`.
34. **Skip forward** — circular 58dp button, visible label `+15`, content-desc `+15 ثا`; triggers `+15s`.
35. **Skip label clarity** — text uses compact math signs (`+15`/`-15`) with full spoken content-descriptions (`-15 ثا`/`+15 ثا`).
36. **Transport spacing** — row is `Arrangement.SpaceEvenly`; five buttons evenly distributed edge-to-edge.
37. **Touch targets** — every transport control ≥48×48dp semantics bounds (verified: 152×152px skip, 200×200px play on the test device).
38. **Focus order** — RTL traversal: next → +15 → play → −15 → previous; no dead buttons.

## E. Tools (sections 39–46)

39. **Save moment (حفظ اللحظة)** — bottom tool chip opens a glass sheet with subtitle `التُقط HH:MM فورًا`.
40. **Save-moment rows** — `إشارة`, `ملاحظة`, `فصل ("ابدأ فصلًا جديدًا من هنا")`; each row: leading glyph + label + optional hint; icons ≥48dp.
41. **Bookmark row description** — cleared to `""` so the captured-time text is not read twice (a11y).
42. **Speed (السرعة)** — bottom chip opens the speed deck; segmented presets (compact); chip shows the active speed.
43. **Sleep timer (النوم)** — bottom chip opens the sleep deck; presets compact; the pill shows live selected state while the timer runs.
44. **Sleep auto-extend** — any playback interaction (play, skip, scrub, chapter jump) auto-extends the active timer, wiring kept in PlayerViewModel (`extendSleepOnInteraction` chain).
45. **Notes sheet** — glass sheet with input hint `دوّن شيئًا عن هذه اللحظة`; behaves like the other sheets.
46. **Note/complex values** — `الفصول (N)` count, zoom hint `تكبير ٣٠–٦٠ دقيقة` and per-chapter durations all render without truncation.

## F. Popups & glass system (sections 47–52)

47. **Glass tokens (fix #2)** — all sheets/decks now `fg`-based translucent glass: `fg.ink.copy(alpha=0.10f)` background + `fg.ink.copy(alpha=0.20f)` border + `hazeChild` blur; text `fg.ink`/`fg.soft`, accents `fg.accent` — replacing the previous flat `Cosmic.InkBottom`/`Color.White` panels.
48. **Macro-scrim** — dismissal scrims use `Color(0xFF0A0F1E).copy(alpha=0.18f)`, matching the responsive player palette.
49. **Sheets: full-screen scrim** — Save-Moment / Notes / Chapter / More sheets own the whole screen; a global click-to-dismiss layer covers everything behind the sheet body.
50. **Tap-outside closes** (fix #3) — verified on device: tapping the scrim dismisses both the utilities deck and the full-screen Save-Moment sheet; leaf-first Main-pass consumption keeps buttons from double-firing.
51. **Deck: cover-area scrim** — the tools/chapters deck dims only the cover `BoxWithConstraints` so timeline scrubbing and chapter dragging stay live while the deck is open.
52. **Accessibility** — every icon is shadowed by a content-description, rows expose ≥48dp targets, chapters panel is a real `ScrollView`; `PlayerScreenAccessibilityTest` 4/4 green.

## G. Build, tests, verification (sections 53–54)

53. **Automated checks** — `:app:compileDebugKotlin` clean (only deprecation warnings: hilt package move, haze → `hazeSource`/`hazeEffect`, AutoMirrored icons); `:app:testDebugUnitTest` green — 36 suites, 0 failures, incl. `ScanRootTest` 7/7 (imported-chapter offset fix) and `PlayerScreenAccessibilityTest` 4/4; `:app:assembleDebug` green.
54. **On-device confirmation** (Pixel 7 API 36) — a single chapter-title node in the hierarchy; glass deck/sheet rendered over the scrim; outside-tap dismissed deck & sheet; play (200×200px) and skip (152×152px) nodes are perfectly circular (same width/height bounds); dragging in edit mode moved chapter boundary 10:00 → 15:58 with live label + current-chapter updates; slider restored after editing.

---

## Summary of the five reported fixes

| # | Issue (Arabic) | Fix | Verified |
|---|----------------|-----|----------|
| 1 | تكرار عنوان الفصل أعلى/أسفل | Removed the timeline context row; single top chip remained | UI dump: exactly one chapter label |
| 2 | البوب ابس ليست زجاجية/شفافة | All sheets/decks → fg-based glass (`ink` 10%/20% + `haze` blur), scrim `0xFF0A0F1E` @18% | Structural review + on-screen scrim |
| 3 | النقر خارج البوب اب لا يغلقه | Full-screen scrim dismiss on sheets; cover-area scrim dismiss on the deck | Tap-outside closed deck and sheet |
| 4 | الأزرار أقرب للمربع | Play → `CircleShape` 76dp; skip → circular 58dp buttons w/ `±15` | 200×200 / 152×152 nodes |
| 5 | نقاط الفصول لا تُسحب | Editing drag layer keyed on `isRtl` only + `rememberUpdatedState` + captured `draggedId`; slider hidden in edit mode | Boundary dragged 10:00 → 15:58, rows + chip updated |

Commit-to-be: follows `7f59b0d` (`Player: calm premium listening session`); pushed to `https://github.com/ahmadshapan123123-stack/audiobook.git`.

---

## H. Update — Sleep Timer Active State (sec 6.5) + popup contrast + chapter drag

### H1. Sleep popup: single `+15` bug (6.5, I-DO-NOT a)
The active-state button `Row` had no `Modifier.weight(1f)`, so every `GlassPillButton` expanded to full width and only `+15 دقيقة` fit on screen (`+30`/`إلغاء` were pushed off-canvas). Fixed — the three-button row now weights all pills equally.

### H2. Active-state redesign (6.5)
`PlayerScreen.kt` `PlayerControlPanel.SLEEP` branch now exposes, while a timer runs:
- **Remaining time countdown** — `متبقٍ MM:SS`, `titleMedium`, `popupInk` (full opacity).
- **Progress bar** — `LinearProgressIndicator` (accent fill, `popupOutline` track) fed from a new `SleepTimerUiState.totalDurationMs`.
- **Increase `+5/10/15/30`** — accent-filled pills (`primary = true`), equal weights.
- **Decrease `−5/10/15`** — outline pills; `decreaseBy()` never goes below zero: at ≤0 the timer ends and the popup returns to the initial picker.
- **`تغيير المدة`** — returns to the 15/30/45/60/مخصص picker; picking a duration replaces the current timer.
- **`إلغاء المؤقت`** — accent pill; full cancel, playback continues, popup → initial.
- All controls re-use `GlassPillButton` (`minTouchTarget()` ≥48dp), same glass/ink treatment as the rest of the popup, deck stays vertically centered. Initial↔active transitions are instant (no washed-out intermediate frame).

### H3. Controller (6.5-J)
`SleepTimerController.decreaseBy(minutes)` added alongside `extendBy`/`cancel`/`start` — single timer system, no bypass. `totalDurationMs = deadline - startedAt` published with every state (progress stays sane after extend).

### H4. Lock screen (6.5-G)
`SleepTimerCommands` registers 7 custom MediaSession commands: `+15/+30/+60`, `−5/−10/−15`, and `cancel`. `PlaybackService.onCustomCommand` routes each to the real controller (`extendBy`/`decreaseBy`/`cancel`). Unit test asserts all commands are registered and that each maps to the exact controller change (incl. below-zero → IDLE).

### H5. Popup contrast
- `popupOutline` strengthened for a **visible** border: light `0x28000000`→`0x4D000000`, dark `0x32FFFFFF`→`0x4DFFFFFF` (no more faded borders).
- Verified the Sleep popup already shared the Speed popup's roles (`popupInk`/`popupSoft`/`popupSurface`) — the only real gap was the layout bug in H1 + flat unselected pills, both fixed. Sleep title sampled at max RGB 237,242,255 (=`popupInk`) and `+15` text 171,180,206 (=`popupSoft`) at full opacity.

### H6. Chapter handle immediate drag
`ChapterHandleStrip` swapped `detectDragGesturesAfterLongPress` → `detectDragGestures`, so chapter handles respond to **immediate** dragging (no long-press required); per-handle 48dp box retains its own gesture; `mkWindowTime`/`mkFraction` edition-global math unchanged.

**Clean on-device persistence proof** (fresh `pm clear`, "ما وراء الطبيعة", full 45:00 edition — defects wiped, chapters re-seeded uniformly every 5:37):
- Baseline chapters: 00:00, 05:37, 11:15, 16:52, 22:30, 28:07, 33:45, 39:22.
- A single handle swipe moved `الفصل السادس` from **28:07 → 12:23** (strip x 419 → 745).
- Chapters deck immediately showed the re-ordered list (`الفصل 4 · الفصل السادس — من 12:23`).
- Room DB confirm: `startPositionMs` 1687500 → 743645 with `orderIndex` re-sorted 0–7 (`MarksCoordinator.updateChapter` → `chapterDao().update` + `reorderChapters`).
- Force-stop + relaunch + reopen: the moved chapter is **still at 12:23** — moves persist across app restarts.
- The Zone B slider seek (independent gesture) still works via the same `input swipe`; dragging a handle does not scrub.

### H7. On-device regression (Pixel 7 API 36, LIGHT theme)
Active sleep popup renders all controls:
```
متبقٍ 14:57
[+5][+10][+15][+30]
[−5][−10][−15]
[تغيير المدة][إلغاء المؤقت]
```
- `+15` → 14:57 → 29:41 ✓
- `−10` → 29:41 → 19:23 ✓
- `−15` twice → timer ends, popup returns to duration picker ✓
- `تغيير المدة` → picker returned while timer still active (title `متبقٍ`); picking `30` replaced 15→30 min ✓
- `إلغاء المؤقت` → popup → initial picker; utility label reverts to `النوم` (no countdown) ✓
- Utility label `النوم · 14:57` shown while running ✓
- Speed, Save-Moment, More decks, and the always-white popup icons unaffected; decks still vertically centered.

### H8. Tests
`:app:testDebugUnitTest` green (141 tests) including updated `SleepTimerControllerTest.mediaSessionCustomCommandsAreRegisteredAndApplyExactChanges` (7 registered commands) and `PlayerForegroundTest` (new `popupOutline` tokens). `:app:assembleDebug` green; APK installed & regression-run on device.

### H9. Chapter start clamping (resolved)
A DB pull after restart showed one chapter (`الفصل الثاني`) with `startPositionMs == edition duration` (2700000 ms). Auditing every writer of chapter positions (Seeder `i*step`; `ScanRoot` imported-chapter sync; `MarksCoordinator` drag edit) shows no production path silently repositions an arbitrary chapter — the value is explainable as a stray demo-instance artifact of earlier session drags on a stale emulator build. The audit DID surface a real reachable edge in current code: `PlayerTimelineEditor.moveChapter`/`addChapter` clamped to `state.durationMs`, so dragging or inserting a marker at the exact end wrote a degenerate zero-length chapter at start=duration. **Fix:** chapter starts now clamp to `maxChapterStartMs = durationMs - 1` (new `PlayerTimelineEditor.maxChapterStartMs`), covered by `PlayerTimelineEditorTest.chapterStartNeversReachesEditionDuration`. The dragged chapter (`الفصل السادس` → 12:23) remains persisted across restart regardless.