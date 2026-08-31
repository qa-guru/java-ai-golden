# ADR 009: UI-текст e2e, 401 JSON — api

Статус: принято.

Неверный пароль — два объекта: форма и JSON.

- Текст на форме → `LoginTests#shouldShowErrorWhenPasswordIsWrong` (`submitExpectingError`).
- 401 JSON → `AuthApiTests#loginWithInvalidPassword`.
- Новый браузерный тест под HTTP-статус / `$("pre")` — не писать.

Селекторы и точная строка ошибки — RAG (`po-locators`, `test-negative`), не этот ADR.
