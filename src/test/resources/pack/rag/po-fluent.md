---
id: po-fluent
domain: testing
adr: 002
tags: [selenide, pageobject]
related: [testdata-user]
index: [успешный, e2e, loginpage, user1, fluent, welcome]
---
# Fluent Page Object

**id:** `po-fluent`

Канон e2e: `pages/LoginPage.java` + `tests/e2e/LoginTests.shouldLoginWithValidCredentials`.

```java
loginPage.openPage()
        .fillAndSubmitForm("user1", "password1")
        .shouldHaveWelcomeMessage("Welcome, user1!");
```

Канон ui (chrome на стабе): `pages/BasePage.header` → `pages/components/HeaderComponent` + `tests/ui/HeaderTests`.

```java
loginPage.openPage()
        .shouldHaveFormTitle("Login Form")
        .header.shouldHaveLangLabel("EN")
        .shouldHaveHtmlLang("en");
```

Литералы в сниппете — сид стенда, не `UserBuilder` (чанк `testdata-user`).

## Do

- Методы PO возвращают `this` или следующую страницу (`HomePage`).
- `open*` / `reload` ждут оболочку страницы (`shouldBeOpen`: `login-form` / `register-form` / `multistack-layout`) — не URL и не полный набор полей.
- Структура формы/лейаута — `shouldShowLoginForm` / `shouldShowRegisterForm` / `shouldShowLayout` в mount-тесте.
- Assert видимости — `should*` с понятным именем.

## Don't

- `void clickLogin()` без возврата страницы.
- Assert в PO без `@Step`.
- Sad path логина через `fillAndSubmitForm` — он ждёт `HomePage`. Чанк `test-negative`.
- Кастомный timeout на `shouldBe` / `waitFor` (`PAGE_READY`, `Duration.ofSeconds(10)`, `{ timeout: 10_000 }`). UI-wait канон — **5s** (Selenide `Configuration.timeout`, Playwright `expect`/`actionTimeout`, Python `WebDriverWait`). Не pageLoad, не длина теста, не сессия хаба.
