# cfg-stands

**id:** `cfg-stands`

Автотест один: стенд задаёт `-Denv=` и `config/*.properties`, не `if (prod)` в Java.

- URL и хаб только из properties. Запрет хардкода адреса стенда в тесте.
- Сид `user1` / `password1` есть на каждом стенде, куда поедет тест. В тесте — литералы.
- mock + `@Layer("ui")` = Chrome на stub. Живой `/api` в браузере = e2e.
