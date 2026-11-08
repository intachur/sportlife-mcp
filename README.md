# sportlife-mcp

MCP-сервер на Spring Boot, который отдаёт сведения о клубе SportLife (клуб «Меркурий») по протоколу MCP через Streamable HTTP.

Сервис обращается к неофициальному API `sync.sportlifeclub.ru` и дополнительно накапливает историю посещаемости в PostgreSQL, чтобы отдавать статистику.

## Возможности

MCP-инструменты:

| Инструмент | Назначение | Пишет в БД |
|---|---|---|
| `get_current_visitors` | Текущее количество посетителей в клубе | да |
| `get_contract_remaining_days` | Сколько дней осталось по клубному договору | нет |
| `get_today_club_events` | Клубные мероприятия и занятия на сегодня | да |
| `get_body_care_specialists` | Доступность специалистов по уходу за телом | нет |
| `get_visitor_statistics` | Статистика посещаемости за `hour`, `day` или `week` | нет |

## Архитектура

```
ClubTools (@Tool) → VisitorService / ClubEventService → SportlifeClient (RestClient + retry)
                                                       → Repository (JdbcTemplate) → PostgreSQL (Flyway)
```

## Требования

- Java 21
- Maven 3.9+
- PostgreSQL (нужен для запуска: Flyway прогоняет миграции при старте)

## Запуск

Maven-проект лежит в подкаталоге [`sources/`](sources) — команды выполняются оттуда.

```bash
export SPORTLIFE_AUTH_TOKEN=<токен>
mvn spring-boot:run
```

или из собранного jar:

```bash
mvn -DskipTests package
SPORTLIFE_AUTH_TOKEN=<токен> java -jar target/sportlife-mcp.jar
```

## Конфигурация

| Переменная | По умолчанию | Описание |
|---|---|---|
| `SPORTLIFE_AUTH_TOKEN` | — (обязательна) | `x-auth-token` для `sync.sportlifeclub.ru` |
| `SPORTLIFE_DB_URL` | `jdbc:postgresql://localhost:5432/sportlife` | JDBC URL PostgreSQL |
| `SPORTLIFE_DB_USER` | `sportlife` | пользователь БД |
| `SPORTLIFE_DB_PASSWORD` | `sportlife` | пароль БД |
| `sportlife.club-id` | `6` | id клуба (6 — Меркурий) |
| `sportlife.base-url` | `https://sync.sportlifeclub.ru` | базовый URL API |
| `sportlife.zone-id` | `Europe/Moscow` | часовой пояс расчёта «сегодня» |
| `sportlife.connect-timeout` / `read-timeout` | `5s` / `15s` | таймауты HTTP-клиента |

## База данных

Схема создаётся Flyway при старте (`sources/src/main/resources/db/migration`):

- `visitor_snapshot` — снимки количества посетителей (`club_id`, `visitors`, `captured_at`);
- `club_event` — мероприятия из расписания (`club_id`, `external_id`, `name`, `starts_at`, `ends_at`, `hall`, `trainer`, `event_date`).

Снимки пишутся по крону каждый час с 09:00 до 22:00 МСК (`0 0 9-22 * * *`) и при вызове `get_current_visitors`. Мероприятия — при вызове `get_today_club_events` (upsert по `club_id + external_id + starts_at`).

## Эндпоинты

- `POST /mcp` — MCP (Streamable HTTP)
- `GET /health` — health-check (включает проверку доступности SportLife API)
- `GET /metrics` — метрики Actuator

## Подключение MCP-клиента

```json
{
  "mcpServers": {
    "sportlife": {
      "type": "streamable-http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

## Проверка

```bash
curl -s http://localhost:8080/health
```

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
```

## Тесты

```bash
mvn test
```

Юнит-тесты на JUnit 5 + Mockito без реальной БД и сети, поэтому PostgreSQL для прогона не нужен.

## Развёртывание

Инструкция и systemd unit — в [`sources/deploy/README.md`](sources/deploy/README.md).

## Дисклеймер

Проект использует неофициальный API мобильного приложения SportLife и требует личного токена авторизации. Токен не хранится в репозитории.

## Лицензия

[MIT](LICENSE) © 2026 Sergey Churikov
