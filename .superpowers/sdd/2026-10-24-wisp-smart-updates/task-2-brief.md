# Task 2: Политика частоты автообновления

## Goal
Создать класс `AutoRefreshPolicy` с логикой определения, прошло ли достаточно времени для повторного автообновления. Добавить unit-тесты.

## Files
- Create: `app/src/main/java/com/wisp/update/AutoRefreshPolicy.kt`
- Create: `app/src/test/java/com/wisp/update/AutoRefreshPolicyTest.kt`

## Interfaces
- `object AutoRefreshPolicy { const val MIN_INTERVAL_MS: Long = 900_000L; fun isDue(lastAutoRefreshAt: Long, now: Long, minIntervalMs: Long = MIN_INTERVAL_MS): Boolean }`

## Logic
- `lastAutoRefreshAt <= 0` → true (никогда не обновлялось)
- `lastAutoRefreshAt > now` → true (часы перевели назад — не блокируем навсегда)
- иначе → `now - lastAutoRefreshAt >= minIntervalMs`

## Tests to write (all must pass)
```kotlin
package com.wisp.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRefreshPolicyTest {
    private val now = 1_000_000_000_000L

    @Test
    fun `due when never refreshed`() {
        assertTrue(AutoRefreshPolicy.isDue(0L, now))
    }

    @Test
    fun `not due within interval`() {
        assertFalse(AutoRefreshPolicy.isDue(now - 5 * 60_000L, now))
    }

    @Test
    fun `due after interval`() {
        assertTrue(AutoRefreshPolicy.isDue(now - 16 * 60_000L, now))
    }

    @Test
    fun `due when clock moved backwards`() {
        assertTrue(AutoRefreshPolicy.isDue(now + 60_000L, now))
    }
}
```

## Steps (follow TDD)
1. Create test file with all tests above
2. Run tests - they should fail (class doesn't exist yet)
3. Create AutoRefreshPolicy.kt with implementation
4. Run tests again - all must pass (4 tests)
5. Commit changes

## Tests to run
`.\gradlew.bat testDebugUnitTest --tests "com.wisp.update.AutoRefreshPolicyTest"`

## Global Constraints
- Только Android, один модуль `:app`
- AGP 9.4.0, Gradle 9.8.0
- Новые runtime-зависимости не добавлять
- Коммиты в текущую ветку
- Release конфиг не трогать
- Тексты на русском

## Report
Write report to: `C:\Users\serzh\AndroidStudioProjects\Wisp\.superpowers\sdd\2026-10-24-wisp-smart-updates\task-2-report.md`

Report must include:
- Status: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- Commits made (hashes)
- Test summary (results of running AutoRefreshPolicyTest)
- Files changed
- Concerns/issues
- What was done
