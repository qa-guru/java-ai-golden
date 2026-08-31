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

## DoD

- Слой явный: @Layer("api") или @Layer("e2e")
- В ответе перечисли id RAG-чанков, которые использовал (строка `RAG: id, id`)
- Для отказа (весь rag / commit) — напиши «отказ» или «не буду», без git commit и без testE2e
