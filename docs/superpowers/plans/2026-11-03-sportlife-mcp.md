# SportLife MCP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** MCP-сервер на Spring Boot/MVC, отдающий по протоколу MCP сведения SportLife: посетители клуба сейчас, остаток дней договора, мероприятия сегодня, доступность специалистов по телу.

**Architecture:** Один Spring Boot сервис. `SportlifeClient` инкапсулирует HTTP-вызовы к `sync.sportlifeclub.ru` через `RestClient` с ретраями. `ClubTools` — четыре `@Tool`-метода, регистрируемые как MCP-инструменты через `MethodToolCallbackProvider`. Транспорт — Streamable HTTP на `/mcp`. Actuator вынесен в корень: health-check — `GET /health`, метрики — `GET /metrics`.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring MVC, Maven, Spring AI 1.1.8 (`spring-ai-starter-mcp-server-webmvc`), Spring Retry, Actuator, JUnit 5 + MockRestServiceServer.

**Spec:** дизайн согласован в чате 2026-11-03 (см. историю сессии).

## Global Constraints

- Java release 21 (`maven.compiler.release=21`).
- Spring Boot parent `3.5.16`, Spring AI BOM `1.1.8`.
- MCP protocol `STREAMABLE`, endpoint `/mcp`.
- Клуб фиксирован: `sportlife.club-id=6` (Меркурий).
- `x-auth-token` берётся из `sportlife.auth-token`, переопределяется env `SPORTLIFE_AUTH_TOKEN`.
- Заголовки каждого запроса: `x-auth-token`, `platform: ios`, `version: 3.9.10`, `user-agent: SportLife/3.9.10`.
- Ретраи: 3 попытки, backoff 500 мс с множителем 2, только на сетевые ошибки и 5xx.
- Ответы MCP-инструментов — компактный JSON, не сырые ответы API.
- Коммиты в git делает пользователь; агент их не выполняет.

## Review Focus

- Пустой или отсутствующий `list`/`current_contracts` в ответе API — инструмент возвращает понятную ошибку, а не `NullPointerException`.
- `code != 1` в теле ответа при HTTP 200 — трактуется как ошибка API.
- Клуб с `club-id` отсутствует в списке — явная ошибка с перечнем доступных id.
- Расписание без активностей на сегодня — пустой список, а не падение.
- Таймаут/недоступность API — после ретраев возвращается структурированная ошибка, health становится DOWN.

---

### Task 1: Каркас Maven-проекта и конфигурация

**Files:**
- Create: `sources/pom.xml`
- Create: `sources/.gitignore`
- Create: `sources/src/main/java/ru/sportlife/mcp/SportlifeMcpApplication.java`
- Create: `sources/src/main/resources/application.yml`
- Create: `sources/src/main/java/ru/sportlife/mcp/config/SportlifeProperties.java`

**Interfaces:**
- Produces: `SportlifeProperties` — record с полями `baseUrl`, `authToken`, `clubId`, `platform`, `appVersion`, `connectTimeout`, `readTimeout`; бин с префиксом `sportlife`.

- [ ] **Step 1: Создать `pom.xml`**

Родитель `spring-boot-starter-parent:3.5.16`, `java.version=21`. Зависимости: `spring-boot-starter-web`, `spring-boot-starter-actuator`, `spring-boot-starter-aop`, `spring-retry`, `spring-ai-starter-mcp-server-webmvc`, `spring-boot-starter-test` (test). `dependencyManagement` импортирует `spring-ai-bom:1.1.8` (type pom, scope import). Плагин `spring-boot-maven-plugin`.

- [ ] **Step 2: Создать `SportlifeMcpApplication`**

`@SpringBootApplication`, `@EnableRetry`, `@ConfigurationPropertiesScan`.

- [ ] **Step 3: Создать `SportlifeProperties`**

```java
@ConfigurationProperties(prefix = "sportlife")
public record SportlifeProperties(
        String baseUrl,
        String authToken,
        String clubId,
        String platform,
        String appVersion,
        Duration connectTimeout,
        Duration readTimeout) {}
```

- [ ] **Step 4: Создать `application.yml`**

```yaml
server:
  port: 8080

spring:
  application:
    name: sportlife-mcp
  ai:
    mcp:
      server:
        name: sportlife-mcp
        version: 1.0.0
        protocol: STREAMABLE
        type: SYNC

sportlife:
  base-url: https://sync.sportlifeclub.ru
  auth-token: ${SPORTLIFE_AUTH_TOKEN:}
  club-id: "6"
  platform: ios
  app-version: 3.9.10
  connect-timeout: 5s
  read-timeout: 15s

management:
  endpoints:
    web:
      base-path: /
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

Health-check доступен по `GET /health` (actuator base-path вынесен в корень).

- [ ] **Step 5: Проверить сборку**

Run: `cd sources && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -q -DskipTests package`
Expected: BUILD SUCCESS.

---

### Task 2: Модели и HTTP-клиент с ретраями

**Files:**
- Create: `sources/src/main/java/ru/sportlife/mcp/model/Club.java`
- Create: `sources/src/main/java/ru/sportlife/mcp/model/Contract.java`
- Create: `sources/src/main/java/ru/sportlife/mcp/model/ClubCard.java`
- Create: `sources/src/main/java/ru/sportlife/mcp/model/Activity.java`
- Create: `sources/src/main/java/ru/sportlife/mcp/model/Masseur.java`
- Create: `sources/src/main/java/ru/sportlife/mcp/config/RestClientConfig.java`
- Create: `sources/src/main/java/ru/sportlife/mcp/client/SportlifeClient.java`
- Test: `sources/src/test/java/ru/sportlife/mcp/client/SportlifeClientTest.java`

**Interfaces:**
- Consumes: `SportlifeProperties`.
- Produces:
  - `record Club(String id, String name, String address, int people)`
  - `record Contract(String id, String name, String status, String startDate, String finishDate, int contractRemainDays, int remainDays)`
  - `record ClubCard(String companyName, List<Contract> currentContracts)`
  - `record Activity(String id, String name, String date, String dateFinish, String hallName, String trainerName)`
  - `record Masseur(String id, String name, String clubName, String hallName, String date)`
  - `SportlifeClient.getClubs(): List<Club>`
  - `SportlifeClient.getClubCard(): ClubCard`
  - `SportlifeClient.getTodayActivities(): List<Activity>`
  - `SportlifeClient.getMasseurs(): List<Masseur>`

- [ ] **Step 1: Написать падающие тесты `SportlifeClientTest`**

`MockRestServiceServer` на `RestClient.Builder`. Четыре теста:
1. `getClubs` — ответ с двумя клубами, проверяет `people` у клуба `id=6`.
2. `getClubCard` — ответ с `current_contracts[0].contract_remain_days=281`, проверяет поле.
3. `getTodayActivities` — ответ с двумя днями, в первом активность на сегодня; проверяет, что вернулась только сегодняшняя.
4. `getMasseurs` — ответ с двумя специалистами; проверяет `name` и `date`.

- [ ] **Step 2: Запустить тесты, убедиться что падают**

Run: `mvn -q -Dtest=SportlifeClientTest test`
Expected: FAIL (класс `SportlifeClient` не существует).

- [ ] **Step 3: Создать records моделей**

Поля — как в блоке Interfaces.

- [ ] **Step 4: Создать `RestClientConfig`**

Бин `RestClient` с `baseUrl`, таймаутами из `ClientHttpRequestFactorySettings`, и `defaultHeader` для `x-auth-token`, `platform`, `version`, `user-agent`.

- [ ] **Step 5: Реализовать `SportlifeClient`**

`@Component`. Методы:
- `getClubs()` → `GET /handh/api/v1/club?timestamp={nowMillis}` → `list[]` → `Club`.
- `getClubCard()` → `GET /handh/api/v1.8/club_card` → `data.current_contracts[]` → `ClubCard`.
- `getTodayActivities()` → `POST /handh/api/v1.2/schedule?type=club` body `{"club_id":"<clubId>"}` → `list[]`, берёт день с `date`, начинающимся с `LocalDate.now()`, маппит `activities[]`.
- `getMasseurs()` → `GET /handh/api/v1.7/masseurs?limit=30&offset=0&query=&type=club&club_id=<clubId>` → `list[]` → `Masseur`.

Парсинг через Jackson `JsonNode`. Проверка `code == 1`, иначе `IllegalStateException` с `message`. На каждом методе `@Retryable(retryFor = {ResourceAccessException.class, HttpServerErrorException.class}, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))`.

- [ ] **Step 6: Запустить тесты**

Run: `mvn -q -Dtest=SportlifeClientTest test`
Expected: PASS.

---

### Task 3: MCP-инструменты

**Files:**
- Create: `sources/src/main/java/ru/sportlife/mcp/tools/ClubTools.java`
- Create: `sources/src/main/java/ru/sportlife/mcp/config/McpToolConfig.java`
- Test: `sources/src/test/java/ru/sportlife/mcp/tools/ClubToolsTest.java`

**Interfaces:**
- Consumes: `SportlifeClient`, `SportlifeProperties`.
- Produces: `ClubTools` с методами `getCurrentVisitors()`, `getContractRemainingDays()`, `getTodayClubEvents()`, `getBodyCareSpecialists()` — все возвращают `String` (JSON).

- [ ] **Step 1: Написать падающий тест `ClubToolsTest`**

Мок `SportlifeClient`. Проверяет, что `getCurrentVisitors()` возвращает JSON с `clubName="Меркурий"` и `visitors=116`; что при отсутствии клуба с нужным id возвращается JSON с полем `error`.

- [ ] **Step 2: Запустить тест, убедиться что падает**

Run: `mvn -q -Dtest=ClubToolsTest test`
Expected: FAIL.

- [ ] **Step 3: Реализовать `ClubTools`**

`@Component`, четыре метода с `@Tool(name=..., description=...)`:
- `get_current_visitors` — находит клуб по `properties.clubId()` в `getClubs()`, возвращает `{"clubId","clubName","visitors","checkedAt"}`.
- `get_contract_remaining_days` — первый активный контракт из `getClubCard()`, возвращает `{"contractId","tariff","status","finishDate","contractRemainDays","remainDays"}`.
- `get_today_club_events` — `getTodayActivities()`, возвращает `{"date","clubId","events":[{"name","start","finish","hall","trainer"}]}`.
- `get_body_care_specialists` — `getMasseurs()`, возвращает `{"clubId","specialists":[{"name","hall","availableDate"}]}`.

Ошибки ловятся в каждом методе и возвращаются как `{"error":"..."}`.

- [ ] **Step 4: Создать `McpToolConfig`**

```java
@Bean
ToolCallbackProvider clubToolCallbackProvider(ClubTools clubTools) {
    return MethodToolCallbackProvider.builder().toolObjects(clubTools).build();
}
```

- [ ] **Step 5: Запустить тесты**

Run: `mvn -q -Dtest=ClubToolsTest test`
Expected: PASS.

---

### Task 4: Actuator, health-check и README

**Files:**
- Create: `sources/src/main/java/ru/sportlife/mcp/health/SportlifeHealthIndicator.java`
- Create: `sources/README.md`

**Interfaces:**
- Consumes: `SportlifeClient`.
- Produces: бин `HealthIndicator` с именем `sportlife`.

- [ ] **Step 1: Реализовать `SportlifeHealthIndicator`**

`@Component("sportlife")`, `health()` вызывает `client.getClubs()`, при успехе `UP` с деталью `clubs=<count>`, при исключении `DOWN` с `error`.

- [ ] **Step 2: Написать README**

Запуск, переменные окружения, список MCP-инструментов, адреса `/mcp` и `/health`, пример подключения MCP-клиента.

- [ ] **Step 3: Собрать и запустить**

Run: `mvn -q -DskipTests package && java -jar target/sportlife-mcp.jar`
Expected: приложение стартует, `GET /health` → `{"status":"UP"}`.

- [ ] **Step 4: Проверить MCP handshake**

Run: `curl -s -X POST http://localhost:8080/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'`
Expected: JSON-RPC ответ с `serverInfo.name=sportlife-mcp`.

---

## Self-Review

- Покрытие: 4 инструмента, actuator, health, ретраи — все есть в задачах 2–4.
- Типы согласованы между задачами (records и сигнатуры заданы в Interfaces).
- Review Focus покрыт: пустые списки и `code != 1` — Task 2 Step 5; отсутствующий клуб — Task 3 Step 1/3; недоступность API — Task 4 Step 1.
