# Контекст продукта (вкладки лаборатории 36)

`LoginTests` extends `TestBase`, поле `loginPage`, не `new LoginPage()` в методе.

`LoginPage`:

- `fillAndSubmitForm(user, pass)` → **HomePage** (happy path).
- `submitExpectingError()` → **LoginPage** (негатив, ошибка на форме).
- `shouldHaveErrorMessage(String)` на LoginPage.
- Текст ошибки: `Wrong login or password`.

Не вызывать `shouldHaveErrorMessage` на результате `fillAndSubmitForm`.
