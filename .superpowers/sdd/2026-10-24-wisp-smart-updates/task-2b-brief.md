# Task 2b: Кэш форматтера дат

## Goal
В `DateUtils.kt` вынести создание `SimpleDateFormat` в поле объекта, чтобы не создавать его при каждом вызове. Добавить комментарий о потокобезопасности.

## Files
- Modify: `app/src/main/java/com/wisp/utils/DateUtils.kt`

## Interfaces
- `DateUtils.formatDate(date: Date): String` — сигнатура без изменений
- `DateUtils.getRelativeTime(date: Date): String` — сигнатура без изменений

## Implementation
- Вынести `SimpleDateFormat` в поле объекта:
```kotlin
private val formatter = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
```
- Использовать его в `formatDate`
- Добавить комментарий: `SimpleDateFormat` не потокобезопасен, вызывается только с Main-потока (все текущие вызовы — из UI и из `autoRefreshAllConfigs`, которая выполняется на Main)

## Steps
1. Read current DateUtils.kt
2. Make the change
3. Build project: `.\gradlew.bat testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL
4. Commit: `git commit -m "perf: reuse date formatter"`

## Global Constraints
- Только Android, один модуль `:app`
- AGP 9.4.0, Gradle 9.8.0
- Коммиты в текущую ветку
- Тексты на русском

## Report
Write report to: `C:\Users\serzh\AndroidStudioProjects\Wisp\.superpowers\sdd\2026-10-24-wisp-smart-updates\task-2b-report.md`

Report must include:
- Status: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- Commits made (hashes)
- Test summary
- Files changed
- Concerns/issues
- What was done
