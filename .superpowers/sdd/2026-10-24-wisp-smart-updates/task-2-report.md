# Task 2 Report

- Status: DONE
- Commits made: 67c242cdd2862884b361da33d59e483be1e0fe84
- Test summary: AutoRefreshPolicyTest — 4 tests, all passed (BUILD SUCCESSFUL)
- Files changed:
  - app/src/main/java/com/wisp/update/AutoRefreshPolicy.kt (created)
  - app/src/test/java/com/wisp/update/AutoRefreshPolicyTest.kt (created)
- Concerns/issues: none
- What was done: Created `object AutoRefreshPolicy` with `MIN_INTERVAL_MS = 900_000L` and `isDue(lastAutoRefreshAt, now, minIntervalMs = MIN_INTERVAL_MS)` implementing the specified logic. Added unit tests (4) covering never-refreshed, within interval, after interval, and clock-moved-backwards cases. All tests pass.
