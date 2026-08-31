---
id: po-step
domain: testing
adr: 002
tags: [allure, step]
---
# Allure @Step на PO

**id:** `po-step`

Канон: методы `LoginPage` помечены `@Step("…")`. Класс `*Tests` шагов не дублирует.

```java
@Step("Type username: {username}")
public LoginPage typeUsername(String username) { … }
```

В тесте только вызов PO — Allure увидит шаги с страницы:

```java
loginPage.openPage()
        .typeUsername("user1")
        .typePassword("wrongpassword")
        .submitExpectingError()
        .shouldHaveErrorMessage("Wrong login or password");
```

## Do

- Глагол + параметры в тексте шага на методе PO.
- Один источник шагов: Page Object.

## Don't

- `@Step` или `Allure.step` в классе `*Tests`.
- Дублировать локаторы в smoke-тесте «чтобы Allure увидел».
