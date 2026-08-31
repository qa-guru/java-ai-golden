---
id: test-negative
domain: testing
adr: 002
tags: [negative, login]
related: [po-fluent, testdata-user]
---
# Negative login

**id:** `test-negative`

Канон: `tests/e2e/LoginTests` — методы `shouldShowValidationErrorWhenUsernameIsEmpty`, `…PasswordIsEmpty`, `shouldShowErrorWhenPasswordIsWrong`.

Действия через PO (`typeUsername`, `submitExpectingError`), текст ошибки — константа в тесте + `shouldHaveErrorMessage`. Empty username: `typePassword("password1")` — литерал длины, не `UserBuilder`.

## Do

- Один ожидаемый текст на сценарий.
- `@Tag("negative")` рядом с `@Tag("e2e")`.

## Don't

- Inline `$("input")` в negative-тесте.
- «Починить» селектор, не воспроизведя ошибку трижды (это уже flaky-skill).
- Собирать `User` для кейса, где пользователя нет (чанк `testdata-user`).
