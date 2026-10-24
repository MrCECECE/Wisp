# Task 2b Report

- Status: DONE
- Commits made: fa4faf6d5a7b75cf421e065750dbf5637e4efe32
- Test summary: BUILD SUCCESSFUL in 29s; tests passed, assembleDebug ok
- Files changed: app/src/main/java/com/wisp/utils/DateUtils.kt
- Concerns/issues: none
- What was done: вынесен SimpleDateFormat в поле объекта DateUtils; добавлен комментарий о потокобезопасности (используется только с Main-потока). Сигнатуры formatDate/getRelativeTime без изменений.
