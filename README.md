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
              └─> WebSearchService (SearXNG)
```

## Структура проекта

```
src/main/java/com/rag/rag_service/
├── controller/        REST-эндпоинты (/v1/* и /api/documents/*)
├── service/           RagService, DocumentService, EmbeddingCacheService,
│                      WebSearchService, OllamaQuotaHandler
├── parser/            TxtParser, MarkdownParser, PdfParser, DocxParser
├── config/            AsyncConfig, QdrantConfig, WebClientConfig, OpenApiConfig
├── model/             OpenAI DTO и DTO документов
├── repository/        DocumentMetadataRepository
├── exception/         QuotaExceededException, GlobalExceptionHandler
└── util/              ChunkUtil
```

## Требования

- Docker 24+ и Docker Compose v2
- API-ключ Ollama
- API-ключ Qdrant

## Переменные окружения

| Переменная | Назначение | Обязательная |
|---|---|---|
| `OLLAMA_API_KEY` | API-ключ Ollama Cloud | да |
| `QDRANT_API_KEY` | API-ключ Qdrant| да |

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

**Без потоковой передачи:**

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "minimax-m2.5:cloud",
    "messages": [{"role": "user", "content": "Что такое Spring Boot?"}],
    "stream": false
  }'
```

**С потоковой передачей (SSE):**

```bash
curl -N http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "minimax-m2.5:cloud",
    "messages": [{"role": "user", "content": "Что такое Spring Boot?"}],
    "stream": true
  }'
```

**Обязательные поля запроса:** `model`, `messages[]` (с `role` и `content`).
**Опциональные:** `stream`, `temperature`, `max_tokens`.

### POST /api/documents/upload

Загрузка документа (PDF, DOCX, Markdown, TXT). Максимальный размер файла — 50 MB.

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

### GET /api/documents

Список всех загруженных документов с их метаданными и статусами.

```bash
curl http://localhost:8080/api/documents
```

### DELETE /api/documents/{id}

Удаление документа и всех его чанков из векторной БД.

```bash
curl -X DELETE http://localhost:8080/api/documents/550e8400-e29b-41d4-a716-446655440000
```

## Как работает RAG-пайплайн

1. Пользователь отправляет запрос через OpenAI-совместимый клиент.
2. Запрос векторизуется через `nomic-embed-text:v1.5` (с кэшированием).
3. В Qdrant ищутся top-5 наиболее релевантных чанков (порог сходства — 0.7).
4. Если релевантных чанков нет — выполняется веб-поиск через SearXNG.
5. Системный промпт формируется по шаблону:

   ```
   Ты — помощник, отвечающий на основе контекста.
   Отвечай только на основе контекста. Если ответа нет — скажи об этом.
   Не придумывай факты. Указывай источник (имя файла), если возможно.

   Контекст:
   <чанки через \n\n---\n\n>
   ```

6. LLM (`minimax-m2.5:cloud`) генерирует ответ.
7. Ответ возвращается клиенту в формате OpenAI.

## Конфигурация

Параметры настраиваются через `src/main/resources/application.yml`:

```yaml
rag:
  chunk:
    size: 1000           # размер чанка в символах
    overlap: 200         # перекрытие между соседними чанками
  retrieval:
    top-k: 5             # количество возвращаемых чанков
    similarity-threshold: 0.7   # порог косинусного сходства
  documents:
    max-file-size: 52428800   # 50 MB
  web-search:
    enabled: true
    searxng-url: http://searxng:8080
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
