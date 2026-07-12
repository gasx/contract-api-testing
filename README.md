# Contract API Testing

Фреймворк для contract-oriented тестирования REST API с поддержкой подготовительных интеграций.

Проект умеет:

- запускать API-тесты по JSON-конфигу;
- валидировать ответ по OpenAPI / JSON Schema;
- отправлять multipart-запросы;
- проверять скачивание файлов;
- запускать подготовительные интеграции перед тестом;
- использовать результат интеграций в `path`, `query`, `headers`, `body`, `multipart`, `contractFile`, `responseFile`;
- отправлять сообщения в Kafka;
- отправлять Kafka-сообщения в Avro;
- читать Kafka-сообщения обратно;
- декодировать Avro binary в JSON;
- делать `extract`, `saveAs`, `assert`, `retry`;
- показывать результат в Web UI.

---

## 1. Быстрый запуск

### Компиляция

```bash
./gradlew clean compileKotlin --console=plain
```

### Запуск одного прогона

```bash
./gradlew run --args="--run configs/run_integrations_demo.json --out build/reports" --console=plain
```

### Запуск с Web UI

```bash
./gradlew run --args="--run configs/run_global_vars_auto_demo.json --out build/reports --web" --console=plain
```

После запуска открой адрес, который приложение выведет в консоль.

Обновление UI в браузере:

```text
Cmd + Shift + R
```

---

## 2. Общая структура run config

Минимальный конфиг:

```json
{
  "baseUrl": "https://httpbingo.org",
  "timeoutMs": 15000,
  "integrations": {},
  "tests": [
    {
      "testId": "T-GET-DEMO",
      "method": "GET",
      "path": "/get",
      "expectedStatus": 200,
      "headers": {
        "Accept": "application/json"
      },
      "query": {
        "alive": "true"
      },
      "contractFile": "schemas/httpbingo-get.schema.json"
    }
  ]
}
```

Поля верхнего уровня:

| Поле | Назначение |
|---|---|
| `baseUrl` | Базовый адрес API |
| `timeoutMs` | Общий timeout HTTP-клиента |
| `integrations` | Подготовительные интеграции |
| `tests` | Список API-тестов |

---

## 3. Структура теста

```json
{
  "testId": "T-EXAMPLE",
  "beforeTest": [
    "clientA"
  ],
  "method": "POST",
  "path": "/api/client/${clientA.vars.clientId}",
  "expectedStatus": 200,
  "headers": {
    "Accept": "application/json",
    "Authorization": "Bearer ${auth.vars.token}"
  },
  "query": {
    "clientId": "${clientA.vars.clientId}"
  },
  "body": {
    "clientId": "${clientA.vars.clientId}",
    "amount": 1000
  },
  "contractFile": "schemas/client-response.schema.json"
}
```

Поля теста:

| Поле | Назначение |
|---|---|
| `testId` | Уникальное имя теста |
| `beforeTest` | Явный список интеграций, которые надо выполнить до теста |
| `method` | HTTP-метод |
| `path` | Путь относительно `baseUrl` |
| `expectedStatus` | Ожидаемый HTTP-статус |
| `headers` | Заголовки запроса |
| `query` | Query-параметры |
| `body` | JSON body |
| `contractFile` | Путь к OpenAPI / JSON Schema |
| `multipart` | Multipart parts |
| `downloadTo` | Куда сохранить скачанный файл |
| `expectedContentType` | Ожидаемый Content-Type скачанного файла |
| `responseFile` | Локальный файл ответа вместо реального HTTP-запроса |

---

## 4. Шаблоны и переменные

Все ссылки на данные пишутся как строки:

```json
{
  "clientId": "${clientA.vars.clientId}"
}
```

Можно использовать шаблоны в:

- `path`;
- `headers`;
- `query`;
- `body`;
- `contractFile`;
- `multipart.value`;
- `multipart.filePath`;
- `multipart.fileName`;
- `multipart.contentType`;
- `downloadTo`;
- `expectedContentType`;
- `responseFile`;
- конфигурации других интеграций.

Если строка полностью состоит из одного шаблона, тип значения сохраняется:

```json
{
  "amount": "${clientA.vars.amount}"
}
```

Если шаблон встроен в строку, результат будет строкой:

```json
{
  "description": "client-${clientA.vars.clientId}"
}
```

Для `headers` и `query` итог всегда приводится к строке.

---

## 5. Как запускаются интеграции

Интеграция выполняется один раз на один тест и кэшируется внутри теста.

Интеграция запускается, если:

1. она указана в `beforeTest`;
2. её имя используется в шаблоне;
3. она нужна другой интеграции;
4. она создаёт global var, которая используется как `${vars.name}`.

Пример автоматического запуска по имени интеграции:

```json
{
  "query": {
    "clientId": "${clientA.vars.clientId}"
  }
}
```

Если есть интеграция `clientA`, она будет выполнена автоматически.

Пример автоматического запуска по global vars:

```json
{
  "query": {
    "clientId": "${vars.clientId}"
  }
}
```

Если какая-то интеграция содержит:

```json
{
  "saveAs": {
    "clientId": "vars.clientId"
  }
}
```

то она будет выполнена автоматически.

---

## 6. Типы интеграций

| Type | Назначение |
|---|---|
| `mock` | Статическая тестовая интеграция |
| `http` | HTTP-запрос к внешней системе |
| `kafka` | Отправка сообщения в Kafka |
| `kafka-consume` | Чтение сообщения из Kafka |

Avro включается через Kafka-поле:

```json
{
  "messageFormat": "avro"
}
```

---

## 7. Mock-интеграция

```json
{
  "integrations": {
    "clientA": {
      "type": "mock",
      "data": {
        "id": "CLIENT-A",
        "type": "first",
        "phone": "+79990000000"
      },
      "extract": {
        "clientId": "response.id",
        "clientType": "response.type",
        "phone": "response.phone"
      }
    }
  }
}
```

Обращение:

```json
{
  "query": {
    "clientId": "${clientA.vars.clientId}",
    "phone": "${clientA.vars.phone}"
  }
}
```

---

## 8. HTTP-интеграция

```json
{
  "integrations": {
    "authA": {
      "type": "http",
      "method": "POST",
      "url": "https://httpbingo.org/post",
      "headers": {
        "Content-Type": "application/json"
      },
      "body": {
        "clientId": "${clientA.vars.clientId}"
      },
      "expectedStatus": 200,
      "extract": {
        "authClientId": "response.json.clientId"
      }
    }
  }
}
```

Поля HTTP-интеграции:

| Поле | Назначение |
|---|---|
| `type` | Всегда `http` |
| `method` | HTTP-метод, по умолчанию `GET` |
| `url` | Полный URL |
| `headers` | Заголовки |
| `query` | Query-параметры |
| `body` | JSON body |
| `auth` | Авторизация |
| `expectedStatus` | Ожидаемый статус |
| `failOnStatus` | Валить интеграцию при плохом статусе |
| `failOnError` | Валить тест при ошибке интеграции |
| `extract` | Извлечь значения в `integration.vars` |
| `saveAs` | Сохранить значения в `${vars.*}` |
| `assert` | Проверить результат интеграции |
| `retry` | Повторять при ошибке |

---

## 9. Авторизация в HTTP-интеграции

Bearer token напрямую:

```json
{
  "auth": {
    "type": "bearer",
    "token": "TOKEN_VALUE"
  }
}
```

Bearer token через переменную окружения:

```json
{
  "auth": {
    "type": "bearer",
    "tokenEnv": "QA_UTILS_TOKEN"
  }
}
```

Перед запуском:

```bash
export QA_UTILS_TOKEN="your-token"
```

Basic auth напрямую:

```json
{
  "auth": {
    "type": "basic",
    "username": "demo",
    "password": "password"
  }
}
```

Basic auth через переменные окружения:

```json
{
  "auth": {
    "type": "basic",
    "usernameEnv": "QA_USER",
    "passwordEnv": "QA_PASSWORD"
  }
}
```

Перед запуском:

```bash
export QA_USER="demo"
export QA_PASSWORD="password"
```

Авторизация через результат другой интеграции:

```json
{
  "integrations": {
    "authA": {
      "type": "http",
      "method": "POST",
      "url": "https://qa-utils/auth",
      "body": {
        "clientId": "${clientA.vars.clientId}"
      },
      "extract": {
        "token": "response.token"
      },
      "saveAs": {
        "token": "vars.token"
      }
    }
  },
  "tests": [
    {
      "testId": "T-AUTH",
      "method": "GET",
      "path": "/profile",
      "headers": {
        "Authorization": "Bearer ${vars.token}"
      },
      "expectedStatus": 200,
      "contractFile": "schemas/profile.schema.json"
    }
  ]
}
```

---

## 10. Переменные окружения

К переменной окружения можно обратиться так:

```json
{
  "headers": {
    "Authorization": "Bearer ${env.API_TOKEN}"
  }
}
```

Перед запуском:

```bash
export API_TOKEN="your-token"
```

Если переменная окружения не найдена, тест упадёт с понятной ошибкой.

---

## 11. extract

`extract` сохраняет значения внутри конкретной интеграции.

```json
{
  "clientA": {
    "type": "mock",
    "data": {
      "id": "CLIENT-A",
      "type": "first"
    },
    "extract": {
      "clientId": "response.id",
      "clientType": "response.type"
    }
  }
}
```

Обращение:

```json
{
  "clientId": "${clientA.vars.clientId}"
}
```

Поддерживаются пути:

```text
response.id
response.data.client.siebelId
response.items[0].id
headers.Content-Type
status
type
vars.clientId
```

---

## 12. saveAs и global vars

`saveAs` сохраняет значения в общий контекст одного теста.

```json
{
  "clientA": {
    "type": "mock",
    "data": {
      "id": "CLIENT-A",
      "type": "first"
    },
    "extract": {
      "clientId": "response.id",
      "clientType": "response.type"
    },
    "saveAs": {
      "clientId": "vars.clientId",
      "clientType": "vars.clientType"
    }
  }
}
```

Теперь можно писать:

```json
{
  "query": {
    "clientId": "${vars.clientId}",
    "clientType": "${vars.clientType}"
  }
}
```

`vars` живут только внутри одного теста. Между разными тестами они не шарятся.

Если две интеграции сохраняют одну и ту же переменную, запуск упадёт с ошибкой конфликта.

---

## 13. assert

`assert` проверяет результат интеграции до запуска основного API-теста.

Простое сравнение:

```json
{
  "assert": {
    "status": 200,
    "response.value.clientId": "${clientA.vars.clientId}",
    "response.value.amount": 1000
  }
}
```

Расширенные операторы:

```json
{
  "assert": {
    "response.value.clientId": {
      "eq": "${clientA.vars.clientId}",
      "matches": "^CLIENT-",
      "contains": "CLIENT"
    },
    "response.value.amount": {
      "gt": 0,
      "gte": 1000,
      "lte": 1000
    },
    "response.value.eventType": {
      "in": [
        "CLIENT_AVRO_PREPARED",
        "CLIENT_UPDATED"
      ]
    },
    "response.value.active": {
      "type": "boolean",
      "eq": true
    }
  }
}
```

Поддерживаемые операторы:

| Оператор | Назначение |
|---|---|
| `eq` | равно |
| `notEq` | не равно |
| `exists` | поле существует / не существует |
| `isNull` | поле равно `null` |
| `notNull` | поле не равно `null` |
| `gt` | больше |
| `gte` | больше или равно |
| `lt` | меньше |
| `lte` | меньше или равно |
| `in` | входит в массив |
| `contains` | строка содержит подстроку / массив содержит элемент / object содержит ключ |
| `matches` | regex |
| `startsWith` | начинается с |
| `endsWith` | заканчивается на |
| `type` | тип значения |

Типы для `type`:

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

## 14. retry

`retry` повторяет интеграцию при ошибке.

```json
{
  "retry": {
    "attempts": 3,
    "delayMs": 1000
  }
}
```

Полный пример:

```json
{
  "optionalBrokenNotify": {
    "type": "http",
    "method": "GET",
    "url": "https://httpbingo.org/status/500",
    "retry": {
      "attempts": 3,
      "delayMs": 500
    },
    "failOnError": false
  }
}
```

В отчёте будет поле:

```json
{
  "attempts": 3
}
```

---

## 15. failOnError, failOnStatus, expectedStatus

`failOnError: false` позволяет не валить тест при ошибке интеграции.

```json
{
  "failOnError": false
}
```

`failOnStatus: false` для HTTP-интеграций не считает плохой HTTP-статус ошибкой.

```json
{
  "type": "http",
  "url": "https://httpbingo.org/status/500",
  "failOnStatus": false
}
```

`expectedStatus` задаёт ожидаемый статус.

```json
{
  "type": "http",
  "url": "https://httpbingo.org/status/404",
  "expectedStatus": 404
}
```

---

## 16. Kafka send

Kafka-интеграция отправляет сообщение в Kafka.

```json
{
  "sendClientEvent": {
    "type": "kafka",
    "hosts": [
      "localhost:9092"
    ],
    "topic": "client-events",
    "key": "${clientA.vars.clientId}",
    "headers": {
      "eventType": "CLIENT_PREPARED"
    },
    "message": {
      "clientId": "${clientA.vars.clientId}",
      "clientType": "${clientA.vars.clientType}",
      "eventType": "CLIENT_PREPARED"
    },
    "delayMs": 3000
  }
}
```

Поля Kafka send:

| Поле | Назначение |
|---|---|
| `type` | Всегда `kafka` |
| `hosts` | Kafka bootstrap servers |
| `topic` | Topic |
| `key` | Message key |
| `headers` | Kafka headers |
| `message` | Тело сообщения |
| `messageFormat` | `json`, `string`, `avro` |
| `avroSchemaFile` | Schema для Avro |
| `properties` | Kafka producer properties |
| `delayMs` | Пауза после отправки |
| `extract` | Извлечь metadata |
| `assert` | Проверить metadata |
| `retry` | Повторять при ошибке |

---

## 17. Kafka properties и авторизация

Kafka client properties можно передать через `properties`.

Пример SASL_SSL:

```json
{
  "properties": {
    "security.protocol": "SASL_SSL",
    "sasl.mechanism": "PLAIN",
    "sasl.jaas.config": "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.KAFKA_USER}\" password=\"${env.KAFKA_PASSWORD}\";"
  }
}
```

Перед запуском:

```bash
export KAFKA_USER="user"
export KAFKA_PASSWORD="password"
```

Пример SSL truststore:

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

## 18. Kafka Avro send

```json
{
  "sendClientAvroEvent": {
    "type": "kafka",
    "hosts": [
      "localhost:9092"
    ],
    "topic": "client-avro-events",
    "key": "${clientA.vars.clientId}",
    "headers": {
      "eventType": "CLIENT_AVRO_PREPARED",
      "format": "avro"
    },
    "messageFormat": "avro",
    "avroSchemaFile": "schemas/client-event.avsc",
    "message": {
      "clientId": "${clientA.vars.clientId}",
      "clientType": "${clientA.vars.clientType}",
      "eventType": "CLIENT_AVRO_PREPARED",
      "amount": 1000,
      "active": true
    },
    "delayMs": 1000,
    "extract": {
      "topic": "response.topic",
      "partition": "response.partition",
      "offset": "response.offset"
    }
  }
}
```

Пример Avro schema:

```json
{
  "type": "record",
  "name": "ClientEvent",
  "namespace": "ru.course.apitesting.demo",
  "fields": [
    {
      "name": "clientId",
      "type": "string"
    },
    {
      "name": "clientType",
      "type": "string"
    },
    {
      "name": "eventType",
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

---

## 19. Kafka consume

```json
{
  "readClientAvroEvent": {
    "type": "kafka-consume",
    "hosts": [
      "localhost:9092"
    ],
    "topic": "${sendClientAvroEvent.vars.topic}",
    "partition": "${sendClientAvroEvent.vars.partition}",
    "offset": "${sendClientAvroEvent.vars.offset}",
    "key": "${clientA.vars.clientId}",
    "headers": {
      "eventType": "CLIENT_AVRO_PREPARED",
      "format": "avro"
    },
    "messageFormat": "avro",
    "avroSchemaFile": "schemas/client-event.avsc",
    "timeoutMs": 10000,
    "extract": {
      "clientId": "response.value.clientId",
      "amount": "response.value.amount"
    }
  }
}
```

Поля Kafka consume:

| Поле | Назначение |
|---|---|
| `type` | Всегда `kafka-consume` |
| `hosts` | Kafka bootstrap servers |
| `topic` | Topic |
| `partition` | Partition |
| `offset` | Offset |
| `key` | Ожидаемый message key |
| `headers` | Ожидаемые Kafka headers |
| `messageFormat` | `json`, `string`, `avro` |
| `avroSchemaFile` | Schema для decode Avro |
| `timeoutMs` | Сколько ждать сообщение |
| `properties` | Kafka consumer properties |
| `extract` | Извлечь значения |
| `assert` | Проверить payload |
| `retry` | Повторять при ошибке |

---

## 20. Локальная Kafka через Docker

Создай файл:

```text
docker-compose.kafka.yml
```

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
docker exec -it contract-api-testing-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic client-avro-events --partitions 1 --replication-factor 1
```

Список topic:

```bash
docker exec -it contract-api-testing-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Consumer для JSON/string:

```bash
docker exec -it contract-api-testing-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic client-events --from-beginning --property print.key=true --property key.separator=" | "
```

Для Avro обычный consumer покажет binary-мусор. Это нормально.

---

## 21. Multipart-запрос

```json
{
  "testId": "T-UPLOAD",
  "method": "POST",
  "path": "/upload",
  "expectedStatus": 200,
  "headers": {
    "Accept": "application/json"
  },
  "multipart": [
    {
      "name": "meta",
      "value": "{\"clientId\":\"${vars.clientId}\"}",
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

## 22. Скачивание файла

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

## 23. responseFile

`responseFile` позволяет проверить контракт на заранее сохранённом ответе без реального HTTP-запроса.

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

## 24. Команды запуска demo-конфигов

```bash
./gradlew run --args="--run configs/run_integrations_demo.json --out build/reports" --console=plain
```

```bash
./gradlew run --args="--run configs/run_integrations_chain_demo.json --out build/reports" --console=plain
```

```bash
./gradlew run --args="--run configs/run_integrations_fail_demo.json --out build/reports" --console=plain
```

```bash
./gradlew run --args="--run configs/run_integrations_optional_demo.json --out build/reports" --console=plain
```

```bash
./gradlew run --args="--run configs/run_kafka_mock_demo.json --out build/reports" --console=plain
```

```bash
./gradlew run --args="--run configs/run_kafka_avro_demo.json --out build/reports" --console=plain
```

```bash
./gradlew run --args="--run configs/run_kafka_avro_consume_demo.json --out build/reports" --console=plain
```

```bash
./gradlew run --args="--run configs/run_kafka_avro_assert_operators_demo.json --out build/reports" --console=plain
```

```bash
./gradlew run --args="--run configs/run_global_vars_demo.json --out build/reports" --console=plain
```

```bash
./gradlew run --args="--run configs/run_global_vars_auto_demo.json --out build/reports" --console=plain
```

```bash
./gradlew run --args="--run configs/run_retry_demo.json --out build/reports" --console=plain
```

С Web UI:

```bash
./gradlew run --args="--run configs/run_global_vars_auto_demo.json --out build/reports --web" --console=plain
```

---

## 25. Как создать новую интеграцию

Чтобы добавить новый тип интеграции:

1. Создать класс executor.
2. Реализовать `IntegrationExecutor`.
3. Указать уникальный `type`.
4. Вернуть `IntegrationResult`.
5. Зарегистрировать executor в `Main.kt`.

Пример:

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

Регистрация в `Main.kt`:

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

Использование в JSON:

```json
{
  "integrations": {
    "myCustom": {
      "type": "custom"
    }
  }
}
```

---

## 26. Структура пакетов integration

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

Назначение:

| Папка | Назначение |
|---|---|
| `core` | Движок интеграций, контекст, результат, executor interface |
| `template` | Шаблонизация `${...}` |
| `assertions` | Проверки интеграций |
| `mock` | Mock executor |
| `http` | HTTP executor |
| `kafka` | Kafka send/consume |
| `avro` | JSON ↔ Avro conversion |

---

## 27. Report JSON

После прогона создаётся файл:

```text
build/reports/report.json
```

Пример блока интеграций:

```json
{
  "integrations": [
    {
      "name": "clientA",
      "type": "mock",
      "status": 200,
      "durationMs": 1,
      "attempts": 1,
      "error": null,
      "vars": {
        "clientId": "CLIENT-A"
      },
      "savedVars": {
        "clientId": "CLIENT-A"
      }
    }
  ]
}
```

---

## 28. Web UI

Запуск с UI:

```bash
./gradlew run --args="--run configs/run_global_vars_auto_demo.json --out build/reports --web" --console=plain
```

В UI видно:

- общий статус прогона;
- список тестов;
- HTTP статус;
- время выполнения;
- ошибки контракта;
- файлы;
- интеграции;
- retry attempts;
- errors;
- vars;
- savedVars.

---

## 29. Частые ошибки

### Docker daemon не запущен

```text
Cannot connect to the Docker daemon
```

Решение:

```bash
docker context use desktop-linux
docker info
```

Потом:

```bash
docker compose -f docker-compose.kafka.yml up -d
```

### Нет config-файла

```text
No such file or directory
```

Проверь:

```bash
pwd
ls
ls configs
```

Запускать команды нужно из корня проекта, где лежит `build.gradle.kts`.

### Нет Avro schema

```text
Avro schema file не найден
```

Проверь:

```bash
ls configs/schemas/client-event.avsc
```

### Не найдена переменная окружения

```text
Переменная окружения не найдена
```

Проверь:

```bash
echo $API_TOKEN
```

### Не найдена global var

```text
Глобальная переменная не найдена: vars.clientId
```

Проверь, что есть интеграция с:

```json
{
  "saveAs": {
    "clientId": "vars.clientId"
  }
}
```

### Две интеграции сохраняют одну global var

```text
Одна global var сохраняется несколькими интеграциями
```

Нужно оставить только одного producer для конкретной переменной.
