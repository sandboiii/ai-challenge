# MCP Weather Server

MCP (Model Context Protocol) сервер для получения данных о погоде через API Национальной метеорологической службы США (NWS).

## Описание

Этот проект предоставляет MCP сервер, который позволяет получать:
- **Прогноз погоды** (`get_forecast`) - прогноз погоды для указанных координат
- **Предупреждения о погоде** (`get_alerts`) - активные предупреждения для указанных координат

Сервер использует официальный API NWS (https://api.weather.gov) и работает только с координатами в пределах США.

## Требования

- Python 3.10 или выше
- Доступ в интернет для работы с NWS API

## Установка

### Вариант 1: Использование uv (рекомендуется)

```bash
# Установка uv (если еще не установлен)
curl -LsSf https://astral.sh/uv/install.sh | sh

# Создание виртуального окружения
uv venv

# Активация виртуального окружения
source .venv/bin/activate  # Linux/macOS
# или
.venv\Scripts\activate  # Windows

# Установка зависимостей
uv pip install -e ".[dev]"
```

### Вариант 2: Использование pip

```bash
# Создание виртуального окружения
python -m venv .venv

# Активация виртуального окружения
source .venv/bin/activate  # Linux/macOS
# или
.venv\Scripts\activate  # Windows

# Установка зависимостей
pip install -r requirements.txt

# Для разработки (включая тесты)
pip install -r requirements-dev.txt
```

## Использование

### Запуск сервера

Сервер работает в режиме STDIO и ожидает JSON-RPC сообщения через стандартный ввод/вывод:

```bash
python weather_server.py
```

Сервер будет ожидать подключения клиента. Для остановки используйте Ctrl+C.

### Запуск тестового клиента

В отдельном терминале запустите клиент для тестирования сервера:

```bash
python weather_client.py
```

Клиент подключится к серверу, выведет список доступных инструментов и выполнит тестовые запросы для координат Сан-Франциско (37.7749, -122.4194).

### Пример использования инструментов

#### get_forecast

Получает прогноз погоды для указанных координат:

```python
# Параметры:
# - latitude: широта (float)
# - longitude: долгота (float)

# Пример: Сан-Франциско, CA
latitude = 37.7749
longitude = -122.4194
```

#### get_alerts

Получает активные предупреждения о погоде для указанных координат:

```python
# Параметры:
# - latitude: широта (float)
# - longitude: долгота (float)

# Пример: Нью-Йорк, NY
latitude = 40.7128
longitude = -74.0060
```

## Тестирование

### Запуск unit тестов

Unit тесты используют моки и не требуют подключения к интернету:

```bash
pytest tests/test_weather_server.py -v
```

### Запуск интеграционных тестов

Интеграционные тесты делают реальные запросы к NWS API:

```bash
# Запуск всех тестов, включая интеграционные
pytest -v

# Запуск только интеграционных тестов
pytest -m integration -v

# Запуск только unit тестов (без интеграционных)
pytest -m "not integration" -v
```

### Запуск всех тестов

```bash
pytest -v
```

## Структура проекта

```
ai-challenge-mcp-py-2/
├── pyproject.toml              # Конфигурация проекта и зависимости
├── weather_server.py            # Основной файл MCP сервера
├── weather_client.py            # Клиент для тестирования сервера
├── tests/
│   ├── __init__.py
│   ├── test_weather_server.py   # Unit тесты с моками
│   └── test_integration.py      # Интеграционные тесты
├── README.md                    # Этот файл
└── .gitignore
```

## Интеграция с Claude Desktop

Для использования сервера в Claude Desktop добавьте следующую конфигурацию в файл `claude_desktop_config.json`:

**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
**Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "weather": {
      "command": "python",
      "args": ["/absolute/path/to/weather_server.py"],
      "env": null
    }
  }
}
```

**Важно:** Используйте абсолютный путь к файлу `weather_server.py`. После изменения конфигурации полностью перезапустите Claude Desktop (не просто закройте окно, а выйдите из приложения).

## Обработка ошибок

Сервер обрабатывает следующие типы ошибок:
- HTTP ошибки (404, 500 и т.д.)
- Таймауты запросов
- Неверные координаты (вне пределов США)
- Отсутствие данных

Все ошибки логируются в stderr и возвращаются в структурированном виде.

## Логирование

Сервер использует стандартный модуль `logging` Python и выводит логи в stderr (это важно для STDIO серверов). Уровень логирования можно изменить в файле `weather_server.py`.

## Ограничения

- API NWS работает только с координатами в пределах США
- Для работы требуется стабильное интернет-соединение
- API может иметь ограничения по частоте запросов

## Лицензия

Этот проект создан в образовательных целях.

## Ссылки

- [Документация MCP](https://modelcontextprotocol.io/)
- [NWS API Documentation](https://www.weather.gov/documentation/services-web-api)
- [MCP Python SDK](https://github.com/modelcontextprotocol/python-sdk)

