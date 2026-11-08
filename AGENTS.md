# AGENTS.md — проект sportlife

## Контекст

Репозиторий содержит сервис `sportlife-mcp` — MCP-сервер на Java/Spring Boot, который отдаёт сведения о клубе SportLife (клуб «Меркурий», id=6) по протоколу MCP.

**Структура репозитория:** сам Maven-проект лежит во вложенном каталоге `sources/` (там `pom.xml`, `src/`, `deploy/`). Все команды Maven запускай из него, а не из корня репозитория. В корне — `README.md`, `AGENTS.md`, `docs/`; каталог `mtproxy/` с дампом трафика существует локально, но в git не публикуется (см. `.gitignore`).

Исходные данные об API получены из дампа mitmproxy `mtproxy/flow-20261103` (42 запроса, 20 из них к `sync.sportlifeclub.ru`). Дамп разбирался через `mitmdump -nr <file> -s <addon.py>`. Дамп хранится только локально и не попадает в git (содержит токен авторизации в заголовках).

## API SportLife

Базовый URL: `https://sync.sportlifeclub.ru`.

Заголовки каждого запроса:

```
x-auth-token: <токен>
platform: ios
version: 3.9.10
user-agent: SportLife/3.9.10
```

Используемые эндпоинты:

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/handh/api/v1/club?timestamp=<ms>` | список клубов, поле `people` — людей в клубе сейчас |
| GET | `/handh/api/v1.8/club_card` | договор: `data.current_contracts[].contract_remain_days`, `remain_days`, `finish_date` |
| POST | `/handh/api/v1.2/schedule?type=club` | расписание на 7 дней, body `{"club_id":"6"}` |
| GET | `/handh/api/v1.7/masseurs?limit=30&offset=0&query=&type=club&club_id=6` | специалисты по уходу за телом |
| GET | `/handh/api/v1.9/dashboard` | сводка (дублирует договор, клубы, специалистов) |
| GET | `/handh/api/v1/profile` | профиль |
| POST | `/handh/api/v1.5/trainer?limit=30&offset=0` | персональные тренеры |

Ответы имеют конверт `{"code":1,"message":"","list":[...]}` или `{"code":1,"data":{...}}`. `code != 1` — ошибка API.

**Важно:** POST `/handh/api/v1.2/schedule` не принимает `Transfer-Encoding: chunked` — отвечает `{"code":0,"message":"Общая ошибка"}`. Тело нужно отправлять с `Content-Length` (в проекте — сериализация в `byte[]`).

## Архитектура sportlife-mcp

```
ClubTools (@Tool) → VisitorService / ClubEventService → SportlifeClient (RestClient + @Retryable)
                                                        → Repository (JdbcTemplate) → PostgreSQL (Flyway)
```

- `tools/ClubTools` — 5 MCP-инструментов, формируют компактный JSON, ошибки возвращают как `{"error": "..."}`.
- `service/VisitorService` — текущие посетители, сохранение снимков, статистика.
- `service/ClubEventService` — мероприятия на сегодня + upsert в БД.
- `client/SportlifeClient` — HTTP, парсинг `JsonNode`, проверка `code == 1`, ретраи.
- `repository/*` — SQL через `JdbcTemplate`.
- `schedule/VisitorSnapshotScheduler` — крон.
- `health/SportlifeHealthIndicator` — health через `ping()` без ретраев.

## MCP-инструменты

| Инструмент | Источник | Пишет в БД |
|---|---|---|
| `get_current_visitors` | `GET /handh/api/v1/club` | да, `visitor_snapshot` |
| `get_contract_remaining_days` | `GET /handh/api/v1.8/club_card` | нет |
| `get_today_club_events` | `POST /handh/api/v1.2/schedule` | да, `club_event` |
| `get_body_care_specialists` | `GET /handh/api/v1.7/masseurs` | нет |
| `get_visitor_statistics(period)` | PostgreSQL | нет |

`period`: `hour`, `day`, `week`. Возвращает `count`, `average`, `min`, `max`, `first`, `last`, `points[]`.

## База данных

PostgreSQL, миграции Flyway в `src/main/resources/db/migration`.

- `visitor_snapshot(id, club_id, visitors, captured_at TIMESTAMPTZ)` + индекс `(club_id, captured_at DESC)`.
- `club_event(id, club_id, external_id, name, starts_at, ends_at, hall, trainer, event_date)` + unique `(club_id, external_id, starts_at)`.

Снимки пишутся по крону `0 0 9-22 * * *` (зона `Europe/Moscow`) и при вызове `get_current_visitors`. Мероприятия — upsert при `get_today_club_events`.

## Конфигурация

| Переменная | По умолчанию |
|---|---|
| `SPORTLIFE_AUTH_TOKEN` | — (обязательна) |
| `SPORTLIFE_DB_URL` | `jdbc:postgresql://localhost:5432/sportlife` |
| `SPORTLIFE_DB_USER` | `sportlife` |
| `SPORTLIFE_DB_PASSWORD` | `sportlife` |
| `sportlife.club-id` | `6` |
| `sportlife.zone-id` | `Europe/Moscow` |
| `sportlife.base-url` | `https://sync.sportlifeclub.ru` |
| `sportlife.connect-timeout` / `read-timeout` | `5s` / `15s` |

Токен из дампа сохранён в `sources/deploy/sportlife.env` (файл в `.gitignore`, права 600).

## Принятые решения

- Java 21, Spring Boot 3.5.16, Spring AI 1.1.8, Maven, `finalName=sportlife-mcp` (jar без версии).
- MCP транспорт — Streamable HTTP, эндпоинт `/mcp`.
- Actuator вынесен в корень: `GET /health`, `GET /metrics`.
- Ретраи: 3 попытки, backoff 500 мс ×2, только `ResourceAccessException` и `HttpServerErrorException`.
- Сбой БД не ломает MCP-ответ: ошибка логируется, данные возвращаются.
- «Сегодня» считается в `Europe/Moscow` один раз и передаётся в клиент.
- `JdbcTemplate`, а не Spring Data JDBC — две простые таблицы.
- Коммиты в git агент не делает (правило проекта).

## Развёртывание

`deploy/`:

- `sportlife-mcp.service` — systemd unit (user `chur`, `/usr/bin/java`, `/opt/sportlife`, лог `/opt/sportlife/logs/app.log`).
- `install.sh` — создаёт пользователя, каталоги, копирует jar и unit, `systemctl enable` + `restart`.
- `sportlife.env` — реальные секреты (gitignored), `sportlife.env.example` — шаблон.

## Команды

```bash
cd sources
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn clean package
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test

# один тест-класс
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn test -Dtest=SportlifeClientTest
```

`JAVA_HOME` указывает на Homebrew JDK 21 (системный `java` может быть другой версии).

Тесты — быстрые юнит-тесты на JUnit 5 + Mockito, без Spring-контекста и без реальной БД/сети (клиент и репозитории мокаются), поэтому Postgres для `mvn test` не нужен. Всего 29 тестов (клиент, инструменты, сервисы, планировщик, health, заголовки).

## Отложенные улучшения

- Литерал `"active"` для статуса договора.
- Различать отсутствующее `people` и `0`.
- `@Validated` на `SportlifeProperties`.
- `isError: true` вместо `{"error": ...}` в MCP-ответах.
- logrotate для `/opt/sportlife/logs/app.log`.
- `@SpringBootTest` на загрузку контекста.
