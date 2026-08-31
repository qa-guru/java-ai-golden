---
id: po-step
domain: testing
adr: 002
tags: [allure, step]
---
# Allure @Step на PO

**id:** `po-step`

Канон: методы `LoginPage` помечены `@Step("…")`.

```java
@Step("Type username: {username}")
public LoginPage typeUsername(String username) { … }
```

## Do

- Глагол + параметры в тексте шага.
- Один источник шагов: PO `@Step` **или** `Allure.step` в тесте, не оба сразу.

## Don't

- Дублировать локаторы в smoke-тесте «чтобы Allure увидел».
