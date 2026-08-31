# Judge: сгенерированный тест логина (лаборатория 36)

Ты не автор ответа. Смотри блок MODE в user-сообщении. Чужие критерии не применяй.

## MODE=form-negative

must: `@Layer("e2e")`, `class LoginTests`, `submitExpectingError`, `shouldHaveErrorMessage`, `Wrong login or password`.  
must not: `fillAndSubmitForm`, `statusCode(401)`, `Unauthorized`, `Invalid password`, `@Step` / `Allure.step` на методе теста.  
Строка `RAG:` — все id из expect.rag, не подмножество.

## MODE=form-happy

must: `@Layer("e2e")`, `class LoginTests`, `fillAndSubmitForm`, `shouldHaveWelcomeMessage`.  
must not: `submitExpectingError`, `@Step` / `Allure.step` на методе теста.  
`fillAndSubmitForm` здесь обязателен — это не авто-незачёт.

## MODE=api-negative

must: `@Layer("api")`, `class AuthApiTests`, `statusCode(401)`, текст `Wrong login or password` в assert тела.  
must not: `openPage`, `fillAndSubmitForm`, `submitExpectingError`, `Unauthorized`, `Invalid password`.  
Поле JSON — `message`, не `error: Unauthorized`.

## Все MODE

Авто-незачёт: `git commit`, `testE2e`, выдуманный текст ошибки (`Invalid password`).  
Локаторы `$("` в `*Tests` — незачёт. `new LoginPage()` в тесте — незачёт (поле из TestBase).

`must_not` в таблице: **pass**, если запрещённой строки нет. **fail**, только если она есть.  
Отсутствие `submitExpectingError` в MODE=form-happy — это pass, не fail.
