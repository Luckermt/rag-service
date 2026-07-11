# RAG Service

Java-сервис, реализующий RAG-систему (Retrieval-Augmented Generation) с OpenAI-совместимым REST API.  
Подключается к любому клиенту с поддержкой формата OpenAI (Open WebUI, LibreChat и т.д.) без модификаций.

## Стек

- **Java 21** + **Spring Boot 3.5**
- **Spring AI 1.0.0-M4** (Ollama + Qdrant стартеры)
- **Qdrant** — векторное хранилище
- **Ollama Cloud** — LLM (`minimax-m2.5:cloud`) и эмбеддинги (`nomic-embed-text:v1.5`)
- **SearXNG** — веб-поиск (fallback)
- **Apache PDFBox / Apache POI / commonmark-java** — парсеры документов
- **Jsoup + Boilerpipe** — парсинг HTML
- **Resilience4j** — повторы, размыкатель цепи, таймауты
- **springdoc-openapi** — Swagger UI

## Архитектура

```
Controller ──> Service ──> Repository
              │           │
              │           ├─> VectorStore (Qdrant)
              │           ├─> EmbeddingModel (Ollama)
              │           ├─> ChatModel (Ollama)
              │           └─> DocumentMetadataRepository (H2)
              │
              ├─> WebSearchService (SearXNG)
              ├─> QueryClassifier (определение интента)
              ├─> ExactTermGuard (буквальный поиск технических токенов)
              ├─> Two‑level Filter (сильные и пограничные источники)
              └─> SystemPromptProvider (выбор стиля ответа)
```

## Структура проекта

```
src/main/java/com/rag/rag_service/
├── controller/           REST-эндпоинты
│   ├── OpenAiController      (/v1/*)
│   ├── DocumentController    (/api/documents/*)
│   └── DebugController       (/rag/debug)
├── service/              Бизнес-логика
│   ├── RagService            основной RAG-пайплайн
│   ├── DocumentService       загрузка/удаление документов
│   ├── WebSearchService      интеграция с SearXNG
│   ├── OllamaQuotaHandler    перехват 429 от Ollama
│   ├── SystemPromptProviderService  выбор промпта по стилю
│   ├── OllamaRetryService    повторы/размыкатель для Ollama
│   ├── QdrantRetryService    повторы для Qdrant
│   ├── VectorStoreRetryService
│   └── WebSearchRetryService
├── parser/               Парсеры документов
│   ├── TxtParser, MarkdownParser, PdfParser, DocxParser, HtmlParser
│   └── DocumentParserFactory
├── config/               Конфигурации
│   ├── AsyncConfig, QdrantConfig, WebClientConfig, OpenApiConfig
│   └── Resilience4jConfig
├── model/                DTO (OpenAI, документы)
├── repository/           DocumentMetadataRepository
├── exception/            QuotaExceededException, GlobalExceptionHandler
└── util/
    ├── ChunkUtil             разбиение по границам
    ├── QueryClassifier       классификация запроса
    ├── ExactTermGuard        бустинг точных технических терминов
    ├── SsrfProtection        защита от SSRF при загрузке по URL
    └── (EmbeddingCacheService удалён)
```

## Требования

- Docker 24+ и Docker Compose v2
- API-ключ Ollama
- API-ключ Qdrant

## Переменные окружения

| Переменная | Назначение | Обязательная |
|---|---|---|
| `OLLAMA_API_KEY` | API-ключ Ollama Cloud | да |
| `QDRANT_API_KEY` | API-ключ Qdrant | да |

## Запуск

### 1. Клонировать и перейти в каталог

```bash
git clone <repository-url>
cd rag-service/rag-service
```

### 2. Экспортировать переменные окружения

```bash
export OLLAMA_API_KEY=<ваш-ключ-ollama-cloud>
export QDRANT_API_KEY=<произвольный-секрет-для-qdrant>
```

### 3. Поднять стек

```bash
docker compose up -d
```

После запуска будут доступны:

| Сервис | URL | Назначение |
|---|---|---|
| RAG-сервис | http://localhost:8080 | API |
| Swagger UI | http://localhost:8080/swagger-ui.html | Документация API |
| Open WebUI | http://localhost:3000 | UI-клиент |
| Qdrant | http://localhost:6333 | Векторная БД |
| SearXNG | http://localhost:8888 | Веб-поиск |

### 4. Открыть Open WebUI и подключиться

Перейти на http://localhost:3000, создать учётную запись, в настройках подключения к OpenAI API указать:

- **URL**: `http://app:8080/v1` (для контейнера) или `http://localhost:8080/v1` (для хоста)
- **API-ключ**: любая непустая строка (сервис не проверяет)

## API

### GET /v1/models

Список доступных моделей в формате OpenAI.

```bash
curl http://localhost:8080/v1/models
```

Ответ:

```json
{
  "object": "list",
  "data": [
    { "id": "minimax-m2.5:cloud", "object": "model", "owned_by": "local" }
  ]
}
```

### POST /v1/chat/completions

Генерация ответа с использованием RAG. Поддерживает `stream=true` (SSE) и `stream=false`.

**Параметры запроса:**

- `model` – имя модели. Можно указать суффиксы для смены стиля:
  - `minimax-m2.5:cloud` – экспертный стиль (по умолчанию)
  - `minimax-m2.5:cloud-eli5` – упрощённый (объяснение простыми словами)
  - `minimax-m2.5:cloud-novice` – для новичков (максимально доступно)
- `messages` – массив сообщений с ролями `user` / `assistant` (поддерживается история диалога).
- `stream` – `true` или `false` (по умолчанию `false`).
- `temperature` – (опционально) температура генерации.
- `max_tokens` – (опционально) максимальное число токенов.

**Особенности:**

- Если запрос классифицирован как `CHIT_CHAT` (приветствие, благодарность), поиск в базе не выполняется, LLM отвечает напрямую.
- Для `FACTUAL` и `CURRENT` выполняется поиск по Qdrant и, при недостатке релевантных чанков, веб-поиск (если включён).
- Если после всех попыток контекст пуст, LLM **не вызывается**, а возвращается ответ: *"Недостаточно данных для ответа на ваш вопрос."* с `generation_ms = 0`. Это экономит квоту и предотвращает галлюцинации.

**Пример без стриминга:**

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "minimax-m2.5:cloud",
    "messages": [{"role": "user", "content": "Что такое Spring Boot?"}],
    "stream": false
  }'
```

**Пример со стримингом:**

```bash
curl -N http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "minimax-m2.5:cloud-eli5",
    "messages": [{"role": "user", "content": "Объясни, как работает REST"}],
    "stream": true
  }'
```

**Ответ (синхронный)** содержит дополнительные поля:

- `requestId` – уникальный идентификатор запроса (дублируется в логах).
- `sources` – список использованных фрагментов с `documentId`, `fileName`, `position`, `similarityScore`.
- `retrieval_ms`, `prompt_ms`, `generation_ms`, `total_ms` – временные метрики.

### POST /api/documents/upload

Загрузка документа (PDF, DOCX, Markdown, TXT, HTML). Максимальный размер — 50 МБ.

```bash
curl -F "file=@./guide.pdf" http://localhost:8080/api/documents/upload
```

Ответ:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "guide.pdf",
  "status": "PROCESSING",
  "chunkCount": 0
}
```

Обработка выполняется асинхронно; статус можно проверить через `GET /api/documents`.

### POST /api/documents/upload-from-url

Загрузка документа по URL (с защитой от SSRF).

```bash
curl -X POST "http://localhost:8080/api/documents/upload-from-url?url=https://example.com/doc.html"
```

Поддерживаются те же форматы, что и при прямой загрузке.

### GET /api/documents

Список всех загруженных документов с их метаданными и статусами.

```bash
curl http://localhost:8080/api/documents
```

### DELETE /api/documents/{id}

Удаление документа и всех его чанков из векторной БД (сначала Qdrant, затем запись в БД).

```bash
curl -X DELETE http://localhost:8080/api/documents/550e8400-e29b-41d4-a716-446655440000
```

### POST /rag/debug

Диагностический эндпоинт, возвращающий детализированный JSON-ответ без вызова LLM (если контекст пуст). Полезен для отладки и прозрачности.

```bash
curl -X POST http://localhost:8080/rag/debug \
  -H "Content-Type: application/json" \
  -d '{
    "model": "minimax-m2.5:cloud",
    "messages": [{"role": "user", "content": "На основе контекста напиши общую информацию о Spring Boot"}]
  }'
```

Ответ содержит `sources` с полной информацией, все тайминги и финальный ответ (или сообщение о недостаточности данных).

## Как работает RAG-пайплайн

1. Пользователь отправляет запрос через OpenAI-совместимый клиент.
2. **Классификация запроса** (`QueryClassifier`):
   - `CHIT_CHAT` – приветствия, благодарности → поиск пропускается.
   - `CURRENT` – запросы с упоминанием времени (сегодня, новости) → приоритет веб-поиску.
   - `FACTUAL` – остальные запросы → поиск в Qdrant.
3. **Поиск в Qdrant** (для `FACTUAL` и `CURRENT` при отсутствии веб-результатов):
   - Эмбеддинг запроса (через `nomic-embed-text:v1.5`).
   - Возврат `topK` чанков с порогом `similarityThreshold`.
   - **Exact‑term guard** – извлечение технических токенов (пути, имена файлов, `CONSTANT_CASE`, `CamelCase`) и бустинг чанков, содержащих эти токены (устанавливается `score = 1.0`).
   - **Двухуровневый фильтр** – проверка наличия хотя бы одного чанка с `score >= strongThreshold` и суммарной оценки всех чанков `>= minTotalScore`. Если условие не выполнено – поиск считается неудачным.
4. **Веб-поиск (fallback)**: если поиск в Qdrant не дал результатов (пустой список или общий контекст < 100 символов) и `web-search.enabled=true`, выполняется запрос к SearXNG. Результаты добавляются в контекст.
5. **Проверка наличия контекста**:
   - Если после всех шагов контекст пуст (и запрос не `CHIT_CHAT`) – LLM **не вызывается**, возвращается сообщение *"Недостаточно данных для ответа на ваш вопрос."* с `generation_ms = 0`.
6. **Формирование промпта**:
   - Системный промпт выбирается на основе стиля ответа (определяется по суффиксу модели).
   - Контекст помещается в тег `<context>` и экранируется (удаляются опасные фразы, ограничивается длина).
   - Добавляется история диалога (до `max-history-messages` сообщений).
7. **Генерация ответа** через Ollama (`minimax-m2.5:cloud`) с повторными попытками и размыкателем цепи (Resilience4j).
8. **Ответ** возвращается в формате OpenAI с дополнительными полями (`sources`, тайминги, `requestId`).

## Конфигурация

Параметры настраиваются через `src/main/resources/application.yml`:

```yaml
rag:
  chunk:
    size: 1000                     # размер чанка в символах
    overlap: 200                   # перекрытие между соседними чанками
  retrieval:
    top-k: 5
    similarity-threshold: 0.0      # порог (0.0 – отключён, т.к. используется двухуровневый фильтр)
    strong-threshold: 0.8          # минимальная оценка для "сильного" чанка
    min-total-score: 1.2           # минимальная сумма оценок всех чанков
  documents:
    max-file-size: 52428800        # 50 МБ
  web-search:
    enabled: true
    searxng-url: http://searxng:8080
  dialog:
    max-history-messages: 20       # максимальное число сообщений в истории
```

## Квоты Ollama

Ollama Cloud тарифицирует по GPU-времени и имеет лимиты. Если лимит исчерпан:

- `OllamaQuotaHandler` перехватывает HTTP 429 от Ollama.
- Возвращается ошибка `429 Too Many Requests` с полем `reset_time` (если сервер его прислал).

Пример ответа:

```json
{
  "error": "Ollama quota exceeded",
  "reset_time": "2026-06-27T12:00:00Z"
}
```

## Локальная разработка (без Docker)

### Требования

- JDK 21
- Локально запущенные Qdrant и Ollama

### Запуск

```bash
cd rag-service
./gradlew bootRun
```

Переменные окружения можно передать через IDE или через `.env`-файл.

## Сборка

```bash
./gradlew clean build
```

JAR-артефакт будет создан в `build/libs/`.

## Тестирование

```bash
./gradlew test
```

## Лицензия

MIT