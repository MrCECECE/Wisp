# Task 0: Базовая проверка перед изменениями

## Goal
Проверить, что базовая сборка и тесты проходят. Зафиксировать базовый размер release APK.

## Files
- Нет изменений в коде (только проверка).

## Steps
- [ ] Step 1: Проверить базовую сборку и тесты
  Run: `.\gradlew.bat testDebugUnitTest assembleDebug`
  Expected: `BUILD SUCCESSFUL` (тестов пока нет)
- [ ] Step 2: Зафиксировать базовый размер release APK
  Проверить файл `app/release/Wisp.v4.0.apk`
  Expected: существует, размер ≈ 1,7 МБ
- [ ] Step 3: Уточнить про git-тег (без изменений в коде)
  `ForegroundUpdateManager` читает GitHub Releases, не git-теги. Теги не нужны для проверки.
- [ ] Step 4: Закоммитить дизайн и план
  ```bash
  git add docs/superpowers/specs/2026-10-24-wisp-smart-updates-design.md docs/superpowers/plans/2026-10-24-wisp-smart-updates.md
  git commit -m "docs: add smart updates design and implementation plan"
  ```

## Global Constraints
- Только Android, один модуль `:app`
- AGP 9.4.0, Gradle 9.8.0
- Release сохраняет `minifyEnabled = true` и `shrinkResources = true`
- Без веток: все коммиты в текущую ветку
- Тексты интерфейса на русском

## Report
Write report to: `C:\Users\serzh\AndroidStudioProjects\Wisp\.superpowers\sdd\2026-10-24-wisp-smart-updates\task-0-report.md`

Report must include:
- Status: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- Commits made (hashes)
- Test summary
- Files changed
- Concerns/issues
- What was done
