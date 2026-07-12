# Contract API Testing

Фреймворк для contract-oriented тестирования REST API с поддержкой подготовительных интеграций.

Проект позволяет описывать тесты в JSON: подготовить данные через интеграции, выполнить REST-запрос, проверить HTTP-статус, контракт ответа, файлы, Kafka-сообщения и результат подготовительных шагов.

---

## Возможности

- REST API тесты по JSON-конфигу
- Проверка ответа по JSON Schema / OpenAPI контракту
- Query, headers, body, path с шаблонами `${...}`
- Multipart upload
- Binary/file download
- Локальная проверка ответа через `responseFile`
- Подготовительные интеграции перед тестом
- Автоматический запуск нужных интеграций
- HTTP-интеграции
- Mock-интеграции
- Kafka send
- Kafka consume
- Avro encode/decode для Kafka
- `extract` значений из результата интеграции
- `saveAs` в глобальные переменные теста
- `assert` результата интеграции
- `retry` интеграций
- Optional-интеграции через `failOnError: false`
- Web UI отчёт

---

## Быстрый старт

Компиляция:

```bash
./gradlew clean compileKotlin --console=plain
```

Запуск тестового прогона:

```bash
./gradlew run --args="--run configs/run.json --out build/reports" --console=plain
```

Запуск с Web UI:

```bash
./gradlew run --args="--run configs/run.json --out build/reports --web" --console=plain
```

После запуска с `--web` открой адрес, который приложение выведет в консоль.

---

## Структура run config

```json
{
  "baseUrl": "https://api.example.test",
  "timeoutMs": 15000,
  "integrations": {},
  "tests": []
}
```

| Поле | Описание |
|---|---|
| `baseUrl` | Базовый URL основного API |
| `timeoutMs` | Timeout HTTP-клиента |
| `integrations` | Описание подготовительных интеграций |
| `tests` | Список тестов |

---

## Структура теста

```json
{
  "testId": "T-CLIENT-GET",
  "beforeTest": [
    "prepareClient"
  ],
  "method": "GET",
  "path": "/clients/${vars.clientId}",
  "expectedStatus": 200,
  "headers": {
    "Accept": "application/json",
    "Authorization": "Bearer ${env.API_TOKEN}"
  },
  "query": {
    "source": "autotest"
  },
  "contractFile": "schemas/client.schema.json"
}
```

| Поле | Описание |
|---|---|
| `testId` | Уникальное имя теста |
| `beforeTest` | Явный список интеграций перед тестом |
| `method` | HTTP-метод |
| `path` | Путь относительно `baseUrl` |
| `expectedStatus` | Ожидаемый HTTP-статус |
| `headers` | Заголовки |
| `query` | Query-параметры |
| `body` | JSON body |
| `contractFile` | Путь к контракту |
| `multipart` | Multipart parts |
| `downloadTo` | Путь для сохранения файла |
| `expectedContentType` | Ожидаемый Content-Type файла |
| `responseFile` | Локальный ответ вместо реального HTTP-запроса |

---

## Шаблоны и переменные

Шаблоны пишутся строками:

```json
{
  "clientId": "${vars.clientId}"
}
```

Поддерживаются источники:

| Синтаксис | Описание |
|---|---|
| `${env.NAME}` | Переменная окружения |
| `${integration.response.path}` | Данные из response интеграции |
| `${integration.vars.name}` | Данные из `extract` интеграции |
| `${integration.status}` | HTTP/Kafka статус интеграции |
| `${integration.headers.Name}` | Header из HTTP-интеграции |
| `${vars.name}` | Глобальная переменная текущего теста |

Если значение целиком состоит из одного шаблона, тип сохраняется:

```json
{
  "amount": "${vars.amount}"
}
```

Если шаблон встроен в строку, результат будет строкой:

```json
{
  "description": "client-${vars.clientId}"
}
```

---

## Как запускаются интеграции

Интеграция выполняется один раз на один тест.

Интеграция запускается, если:

- указана в `beforeTest`;
- её имя используется в шаблоне, например `${prepareClient.vars.clientId}`;
- она нужна другой интеграции;
- она создаёт global var, которая используется как `${vars.clientId}`.

Пример явного запуска:

```json
{
  "beforeTest": [
    "prepareClient"
  ]
}
```

Пример автоматического запуска по имени интеграции:

```json
{
  "query": {
    "clientId": "${prepareClient.vars.clientId}"
  }
}
```

Пример автоматического запуска по global vars:

```json
{
  "query": {
    "clientId": "${vars.clientId}"
  }
}
```

Для этого какая-то интеграция должна сохранять переменную:

```json
{
  "saveAs": {
    "clientId": "vars.clientId"
  }
}
```

---

## Общие поля интеграций

Эти поля можно использовать в разных типах интеграций:

| Поле | Описание |
|---|---|
| `type` | Тип интеграции |
| `extract` | Извлечь значения в `integration.vars` |
| `saveAs` | Сохранить значения в `${vars.*}` |
| `assert` | Проверить результат интеграции |
| `retry` | Повторить интеграцию при ошибке |
| `failOnError` | Валить тест при ошибке интеграции |
| `delayMs` | Пауза после действия, где поддерживается |

---

## extract

`extract` сохраняет значения внутри конкретной интеграции.

```json
{
  "extract": {
    "clientId": "response.client.id",
    "phone": "response.client.phone",
    "firstItemId": "response.items[0].id",
    "statusCode": "status"
  }
}
```

После этого можно обращаться так:

```json
{
  "clientId": "${prepareClient.vars.clientId}"
}
```

Поддерживаются пути:

```text
response.id
response.data.clientId
response.items[0].id
vars.clientId
headers.Content-Type
status
type
```

---

## saveAs

`saveAs` сохраняет значения в общий контекст текущего теста.

```json
{
  "extract": {
    "clientId": "response.client.id"
  },
  "saveAs": {
    "clientId": "vars.clientId"
  }
}
```

После этого можно использовать короткую запись:

```json
{
  "clientId": "${vars.clientId}"
}
```

`vars` живут только внутри одного теста. Между тестами они не шарятся.

---

## assert

`assert` проверяет результат интеграции.

Простое сравнение:

```json
{
  "assert": {
    "status": 200,
    "vars.clientId": "CLIENT-A"
  }
}
```

Расширенные операторы:

```json
{
  "assert": {
    "response.client.id": {
      "matches": "^CLIENT-"
    },
    "response.amount": {
      "gt": 0,
      "lte": 10000
    },
    "response.eventType": {
      "in": [
        "CREATED",
        "UPDATED"
      ]
    },
    "response.active": {
      "type": "boolean",
      "eq": true
    }
  }
}
```

Операторы:

| Оператор | Описание |
|---|---|
| `eq` | Равно |
| `notEq` | Не равно |
| `exists` | Существует / не существует |
| `isNull` | Равно `null` |
| `notNull` | Не равно `null` |
| `gt` | Больше |
| `gte` | Больше или равно |
| `lt` | Меньше |
| `lte` | Меньше или равно |
| `in` | Входит в список |
| `contains` | Содержит значение |
| `matches` | Regex |
| `startsWith` | Начинается с |
| `endsWith` | Заканчивается на |
| `type` | Тип значения |

Типы:

```text
null
object
array
boolean
int
long
number
string
```

---

## retry

```json
{
  "retry": {
    "attempts": 3,
    "delayMs": 1000
  }
}
```

Поведение:

```text
попытка 1
ошибка
пауза delayMs
попытка 2
ошибка
пауза delayMs
попытка 3
итоговый результат
```

В отчёте сохраняется:

```json
{
  "attempts": 3
}
```

---

## failOnError

По умолчанию ошибка интеграции валит тест.

```json
{
  "failOnError": true
}
```

Чтобы сделать интеграцию optional:

```json
{
  "failOnError": false
}
```

В этом случае ошибка будет видна в отчёте, но основной тест продолжит выполняться.

---

## HTTP integration

```json
{
  "loadClient": {
    "type": "http",
    "method": "GET",
    "url": "https://api.example.test/client",
    "query": {
      "id": "${vars.clientId}"
    },
    "headers": {
      "Accept": "application/json",
      "Authorization": "Bearer ${env.API_TOKEN}"
    },
    "expectedStatus": 200,
    "extract": {
      "clientId": "response.id",
      "clientName": "response.name"
    }
  }
}
```

Поля:

| Поле | Описание |
|---|---|
| `type` | `http` |
| `method` | HTTP-метод |
| `url` | Полный URL |
| `headers` | Заголовки |
| `query` | Query-параметры |
| `body` | JSON body |
| `auth` | Авторизация |
| `expectedStatus` | Ожидаемый статус |
| `failOnStatus` | Считать плохой статус ошибкой |
| `extract` | Извлечение данных |
| `saveAs` | Сохранение global vars |
| `assert` | Проверки |
| `retry` | Повторы |

### expectedStatus

```json
{
  "expectedStatus": 404
}
```

Тогда `404` считается успешным статусом.

### failOnStatus

```json
{
  "failOnStatus": false
}
```

Тогда не-2xx статус не будет считаться ошибкой интеграции.

---

## Авторизация в HTTP integration

### Bearer token

```json
{
  "auth": {
    "type": "bearer",
    "tokenEnv": "API_TOKEN"
  }
}
```

Перед запуском:

```bash
export API_TOKEN="token-value"
```

### Basic auth

```json
{
  "auth": {
    "type": "basic",
    "usernameEnv": "API_USER",
    "passwordEnv": "API_PASSWORD"
  }
}
```

Перед запуском:

```bash
export API_USER="user"
export API_PASSWORD="password"
```

Можно передать значения напрямую, но для CI/CD лучше использовать переменные окружения:

```json
{
  "auth": {
    "type": "basic",
    "username": "user",
    "password": "password"
  }
}
```

---

## Mock integration

```json
{
  "prepareClient": {
    "type": "mock",
    "data": {
      "id": "CLIENT-A",
      "type": "first",
      "amount": 1000
    },
    "extract": {
      "clientId": "response.id",
      "clientType": "response.type",
      "amount": "response.amount"
    },
    "saveAs": {
      "clientId": "vars.clientId",
      "amount": "vars.amount"
    }
  }
}
```

Используется для локальных и демонстрационных тестов, а также для быстрой проверки цепочек.

---

## Kafka send integration

```json
{
  "sendEvent": {
    "type": "kafka",
    "hosts": [
      "localhost:9092"
    ],
    "topic": "client-events",
    "key": "${vars.clientId}",
    "headers": {
      "eventType": "CREATED"
    },
    "messageFormat": "json",
    "message": {
      "clientId": "${vars.clientId}",
      "amount": "${vars.amount}"
    },
    "delayMs": 1000,
    "extract": {
      "topic": "response.topic",
      "partition": "response.partition",
      "offset": "response.offset"
    },
    "saveAs": {
      "eventTopic": "vars.topic",
      "eventPartition": "vars.partition",
      "eventOffset": "vars.offset"
    }
  }
}
```

Поля:

| Поле | Описание |
|---|---|
| `type` | `kafka` |
| `hosts` | Bootstrap servers |
| `topic` | Topic |
| `key` | Message key |
| `headers` | Kafka headers |
| `messageFormat` | `json`, `string`, `avro` |
| `message` | Payload |
| `avroSchemaFile` | Avro schema file |
| `properties` | Kafka producer properties |
| `delayMs` | Пауза после отправки |

---

## Kafka consume integration

```json
{
  "readEvent": {
    "type": "kafka-consume",
    "hosts": [
      "localhost:9092"
    ],
    "topic": "${vars.eventTopic}",
    "partition": "${vars.eventPartition}",
    "offset": "${vars.eventOffset}",
    "key": "${vars.clientId}",
    "headers": {
      "eventType": "CREATED"
    },
    "messageFormat": "json",
    "timeoutMs": 10000,
    "extract": {
      "clientId": "response.value.clientId",
      "amount": "response.value.amount"
    },
    "assert": {
      "response.value.clientId": {
        "eq": "${vars.clientId}"
      }
    }
  }
}
```

Поля:

| Поле | Описание |
|---|---|
| `type` | `kafka-consume` |
| `hosts` | Bootstrap servers |
| `topic` | Topic |
| `partition` | Partition |
| `offset` | Offset |
| `key` | Ожидаемый key |
| `headers` | Ожидаемые headers |
| `messageFormat` | `json`, `string`, `avro` |
| `avroSchemaFile` | Avro schema file |
| `timeoutMs` | Время ожидания сообщения |
| `properties` | Kafka consumer properties |

---

## Kafka properties и авторизация

Kafka properties передаются напрямую:

```json
{
  "properties": {
    "security.protocol": "SASL_SSL",
    "sasl.mechanism": "PLAIN",
    "sasl.jaas.config": "org.apache.kafka.common.security.plain.PlainLoginModule required username="${env.KAFKA_USER}" password="${env.KAFKA_PASSWORD}";"
  }
}
```

Перед запуском:

```bash
export KAFKA_USER="user"
export KAFKA_PASSWORD="password"
```

SSL пример:

```json
{
  "properties": {
    "security.protocol": "SSL",
    "ssl.truststore.location": "/path/to/truststore.jks",
    "ssl.truststore.password": "${env.KAFKA_TRUSTSTORE_PASSWORD}"
  }
}
```

---

## Avro для Kafka

Для Avro нужно указать:

```json
{
  "messageFormat": "avro",
  "avroSchemaFile": "schemas/event.avsc"
}
```

Пример schema:

```json
{
  "type": "record",
  "name": "ClientEvent",
  "namespace": "example.events",
  "fields": [
    {
      "name": "clientId",
      "type": "string"
    },
    {
      "name": "amount",
      "type": "long"
    },
    {
      "name": "active",
      "type": "boolean"
    }
  ]
}
```

Пример message:

```json
{
  "message": {
    "clientId": "${vars.clientId}",
    "amount": "${vars.amount}",
    "active": true
  }
}
```

Обычный Kafka console consumer может показывать Avro как binary-мусор. Это нормально. Для проверки Avro используй `kafka-consume` с тем же `avroSchemaFile`.

---

## Multipart upload

```json
{
  "testId": "T-UPLOAD",
  "method": "POST",
  "path": "/upload",
  "expectedStatus": 200,
  "multipart": [
    {
      "name": "meta",
      "value": "{"clientId":"${vars.clientId}"}",
      "contentType": "application/json"
    },
    {
      "name": "file",
      "filePath": "files/example.pdf",
      "fileName": "example.pdf",
      "contentType": "application/pdf"
    }
  ],
  "contractFile": "schemas/upload-response.schema.json"
}
```

---

## File download

```json
{
  "testId": "T-DOWNLOAD",
  "method": "GET",
  "path": "/file",
  "expectedStatus": 200,
  "headers": {
    "Accept": "application/pdf"
  },
  "downloadTo": "downloads/result.pdf",
  "expectedContentType": "application/pdf",
  "contractFile": "schemas/download-response.schema.json"
}
```

---

## responseFile

`responseFile` позволяет проверить контракт на локальном файле без HTTP-запроса.

```json
{
  "testId": "T-LOCAL-RESPONSE",
  "method": "GET",
  "path": "/demo",
  "expectedStatus": 200,
  "responseFile": "responses/demo-response.json",
  "contractFile": "schemas/demo.schema.json"
}
```

---

## Локальная Kafka через Docker

Файл `docker-compose.kafka.yml`:

```yaml
services:
  kafka:
    image: apache/kafka:4.1.0
    container_name: contract-api-testing-kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

Запуск:

```bash
docker compose -f docker-compose.kafka.yml up -d
```

Проверка:

```bash
docker ps
```

Создать topic:

```bash
docker exec -it contract-api-testing-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic client-events --partitions 1 --replication-factor 1
```

Список topic:

```bash
docker exec -it contract-api-testing-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

---

## Отчёты

После запуска создаётся:

```text
build/reports/report.json
```

В отчёте по тесту есть:

- `testId`
- `method`
- `target`
- `expectedStatus`
- `actualStatus`
- `passed`
- `violations`
- `durationMs`
- `fileTransfers`
- `integrations`

Пример интеграции в отчёте:

```json
{
  "name": "prepareClient",
  "type": "mock",
  "status": 200,
  "durationMs": 2,
  "attempts": 1,
  "error": null,
  "vars": {
    "clientId": "CLIENT-A"
  },
  "savedVars": {
    "clientId": "CLIENT-A"
  }
}
```

---

## Web UI

Запуск:

```bash
./gradlew run --args="--run configs/run.json --out build/reports --web" --console=plain
```

В UI отображается:

- общий статус;
- список тестов;
- HTTP-статус;
- время выполнения;
- ошибки;
- файлы;
- интеграции;
- retry attempts;
- `vars`;
- `savedVars`.

---

## Как добавить новый тип интеграции

Создай executor:

```kotlin
package ru.course.apitesting.integration.custom

import kotlinx.serialization.json.JsonObject
import ru.course.apitesting.integration.core.IntegrationContext
import ru.course.apitesting.integration.core.IntegrationExecutor
import ru.course.apitesting.integration.core.IntegrationResult

class CustomIntegrationExecutor : IntegrationExecutor {
    override val type: String = "custom"

    override fun execute(
        name: String,
        config: JsonObject,
        context: IntegrationContext
    ): IntegrationResult {
        return IntegrationResult(
            name = name,
            type = type,
            status = 200
        )
    }
}
```

Зарегистрируй executor в `Main.kt`:

```kotlin
val integrationEngine = IntegrationEngine(
    integrations = runConfig.integrations,
    executors = listOf(
        MockIntegrationExecutor(),
        HttpIntegrationExecutor(httpClient),
        KafkaIntegrationExecutor(runFileDir),
        KafkaConsumeIntegrationExecutor(runFileDir),
        CustomIntegrationExecutor()
    )
)
```

Используй в JSON:

```json
{
  "integrations": {
    "customStep": {
      "type": "custom"
    }
  }
}
```

---

## Рекомендуемая структура integration package

```text
src/main/kotlin/ru/course/apitesting/integration/
├── core
├── template
├── assertions
├── mock
├── http
├── kafka
└── avro
```

| Папка | Назначение |
|---|---|
| `core` | Движок интеграций |
| `template` | Шаблоны `${...}` |
| `assertions` | Проверки интеграций |
| `mock` | Mock executor |
| `http` | HTTP executor |
| `kafka` | Kafka send/consume |
| `avro` | JSON ↔ Avro |

---

## Частые ошибки

### Config file not found

```text
No such file or directory
```

Проверь, что команда запускается из корня проекта:

```bash
pwd
ls
```

В корне должен быть `build.gradle.kts`.

### Docker daemon не запущен

```text
Cannot connect to the Docker daemon
```

Проверь Docker:

```bash
docker info
```

На Mac иногда нужно переключить context:

```bash
docker context use desktop-linux
```

### Avro schema file не найден

Проверь путь:

```bash
ls configs/schemas/event.avsc
```

### Переменная окружения не найдена

Проверь:

```bash
echo $API_TOKEN
```

### Global var не найдена

Проверь, что переменная создаётся через `saveAs`:

```json
{
  "saveAs": {
    "clientId": "vars.clientId"
  }
}
```

### Конфликт global vars

Если две интеграции сохраняют одну и ту же переменную, запуск упадёт. Нужно оставить одного producer для конкретной `vars.*`.

---

## Минимальный полный пример

```json
{
  "baseUrl": "https://httpbingo.org",
  "timeoutMs": 15000,
  "integrations": {
    "prepareClient": {
      "type": "mock",
      "data": {
        "id": "CLIENT-A",
        "amount": 1000
      },
      "extract": {
        "clientId": "response.id",
        "amount": "response.amount"
      },
      "saveAs": {
        "clientId": "vars.clientId",
        "amount": "vars.amount"
      }
    },
    "sendEvent": {
      "type": "kafka",
      "hosts": [
        "localhost:9092"
      ],
      "topic": "client-events",
      "key": "${vars.clientId}",
      "messageFormat": "json",
      "message": {
        "clientId": "${vars.clientId}",
        "amount": "${vars.amount}"
      },
      "extract": {
        "topic": "response.topic",
        "partition": "response.partition",
        "offset": "response.offset"
      },
      "saveAs": {
        "eventTopic": "vars.topic",
        "eventPartition": "vars.partition",
        "eventOffset": "vars.offset"
      }
    },
    "readEvent": {
      "type": "kafka-consume",
      "hosts": [
        "localhost:9092"
      ],
      "topic": "${vars.eventTopic}",
      "partition": "${vars.eventPartition}",
      "offset": "${vars.eventOffset}",
      "key": "${vars.clientId}",
      "messageFormat": "json",
      "timeoutMs": 10000,
      "assert": {
        "response.value.clientId": {
          "eq": "${vars.clientId}"
        },
        "response.value.amount": {
          "eq": 1000
        }
      }
    }
  },
  "tests": [
    {
      "testId": "T-FULL-FLOW",
      "method": "GET",
      "path": "/get",
      "expectedStatus": 200,
      "query": {
        "clientId": "${vars.clientId}",
        "amount": "${vars.amount}"
      },
      "headers": {
        "Accept": "application/json"
      },
      "contractFile": "schemas/httpbingo-get.schema.json"
    }
  ]
}
```