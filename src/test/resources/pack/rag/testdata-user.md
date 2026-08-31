---
id: testdata-user
domain: testing
adr: 002
tags: [testdata, seed, faker]
related: [po-fluent]
index: [user1, password1, testdata, сид, seed]
---
# Сид стенда vs фабрика

**id:** `testdata-user`

Канон: `tests/e2e/LoginTests` · `tests/e2e/RegisterTests` · `helpers/UserBuilder.java`.  
API: `tests/api/AuthApiTests` — логин сида литералами, throwaway через `DataFaker`.

`user1` / `password1` — **фикстура стенда**, не файл и не API. На каждом стенде, куда поедет тест, этот пользователь уже есть (`cfg-stands`).

```java
// сид — литералы
loginPage.openPage()
        .fillAndSubmitForm("user1", "password1")
        .shouldHaveWelcomeMessage("Welcome, user1!");

// throwaway — фабрика + teardown
User user = new UserBuilder().withUsername().withPassword().build();
registerPage.openPage()
        .fillAndSubmitForm(user.username(), user.password(), user.password())
        .shouldHaveWelcomeMessage(user.welcomeMessage());
```

Duplicate username на регистрации — снова литерал `"user1"`, не новый пользователь из билдера.

## Do

- Логин / логаут / session / API login сида — `"user1"`, `"password1"`, `"Welcome, user1!"`.
- Register happy-path и delete-account — `UserBuilder` (или `DataFaker` в API) + cleanup.
- Empty login: пароль валидной длины литералом (`"password1"`), без `User`.

## Don't

- `UserBuilder.withSeededUser()` / оборачивать сид в билдер «чтобы было красиво».
- Assert `Welcome, user1!` после логина пользователя из фабрики.
- Класть `UserBuilder` / faker в mill IR (`crystals/*.json`). `@crystal` на spec ≠ «пиши как JSON».
