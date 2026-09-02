---
id: po-locators
domain: testing
adr: 002
tags: [selenide, locators]
related: []
index: [локатор, селектор, data-testid]
---
# Селекторы только в PO

**id:** `po-locators`

Канон формы: `pages/LoginPage.java` — `data-testid`.

```java
private final SelenideElement loginInput = $("[data-testid='login-input']");
```

Канон хедера: `pages/components/HeaderComponent` (`header`, `header-burger`, lang/theme). Страницы ходят через `BasePage.header`, не дублируют селекторы.

## Do

- `private final` локаторы в Page Object.
- В приложении — стабильный `data-testid`.
- Общий chrome (header) — один компонент, не копия в каждом `*Page`.

## Don't

- `$(".btn")` / xpath в `*Tests`.
- Копировать локатор в два PO (`LoginPage` + `HomePage` с тем же `header-lang-toggle`).
