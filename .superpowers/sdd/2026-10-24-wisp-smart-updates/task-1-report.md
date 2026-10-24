# Task 1 Report: Мультипротокольный парсер `HtmlParser`

## Status
DONE

## Commits made (hashes)
- 82123a5 - Task 1: extend HtmlParser to support all protocols and pre/plain text

## Files changed
- Modified: `app/src/main/java/com/wisp/utils/HtmlParser.kt`
- Created: `app/src/test/java/com/wisp/utils/HtmlParserTest.kt`

## What was done
- Extended `HtmlParser` to support all 9 protocols case-insensitively: vless, trojan, vmess, hysteria, hysteria2, hy2, ss, ssr, tuic
- Added `PROTOCOLS` list, `PRE_REGEX` for `<pre>` blocks with DOT_MATCHES_ALL, `HEADER_NONE_REGEX` for cleaning
- Parsing logic: if text contains `<pre` (case-insensitive), extract from all `<pre>` block contents; otherwise parse entire text as plain text subscription
- Extract URLs line by line, trim, match by protocol prefix case-insensitively
- Use `LinkedHashSet` for deduplication while preserving order
- Implemented cleaning: replace `&amp;` → `&`, remove `%5C` and `%22`, replace `%2C` → `,`, then apply `HEADER_NONE_REGEX` to normalize `headerType=none` with trailing commas/percent signs
- Added comprehensive unit tests for all required cases
- `unescapeHtml` unchanged

## Test summary (results of running HtmlParserTest)
Command: `.\gradlew.bat testDebugUnitTest --tests "com.wisp.utils.HtmlParserTest"`
Result: **BUILD SUCCESSFUL**
- All 7 tests passed:
  - extracts plain text subscription
  - extracts all supported protocols
  - matches protocol case insensitively
  - deduplicates repeated urls
  - extracts from pre block
  - cleans html artifacts
  - ignores non protocol lines

## Concerns/issues
None
