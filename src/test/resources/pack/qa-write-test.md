# Skill qa-write-test

Написать один автотест: слой, PO, tags.

## Do not

- Селекторы в классе *Tests
- URL стенда / секреты в Java
- E2e на JSON-контракт, если место в tests/api (401 логина — api)
- Gradle-task testE2e
- git commit

## Якоря

| Слой | Когда | Образец |
|------|--------|---------|
| api | JSON-контракт, статус 401 | tests/api/AuthApiTests |
| e2e | живой браузер через /api | tests/e2e/LoginTests |
| ui | chrome на mock, без живого /api | tests/ui/LoginFormTests |

Неверный пароль: API-проверка тела/статуса → api. Текст ошибки на форме на живом стенде → e2e.

Негатив e2e (не `fillAndSubmitForm` — он возвращает HomePage):

```java
loginPage.openPage()
        .typeUsername("user1")
        .typePassword("wrongpassword")
        .submitExpectingError()
        .shouldHaveErrorMessage("Wrong login or password");
```


API 401 (не клики):

```java
given()
        .body(new LoginRequest("user1", "wrongpassword"))
        .when()
        .post("/api/auth/login")
        .then()
        .statusCode(401)
        .body("message", equalTo("Wrong login or password"));
```

Не `Unauthorized` и не поле `error`.

## DoD

- Слой явный: @Layer("api") или @Layer("e2e")
- Первая строка генерации: `RAG:` и все выданные id, не подмножество
- Для отказа (весь rag / commit) — первая строка `Отказ.`, без git commit и без testE2e
