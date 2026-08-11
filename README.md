# FinTracker — Telegram-бот учёта финансов с интеллектуальной категоризацией

Система автоматизирует ведение финансового учёта: пользователь пишет трату
в свободной форме в Telegram, а нейросеть автоматически определяет категорию
транзакции по её текстовому описанию.

## Архитектура

- **bot-service** (Java 21, Spring Boot) — Telegram-бот: приём сообщений,
  разбор суммы и описания, сохранение операций в PostgreSQL, статистика,
  исправление категории через inline-кнопки.
- **ml-service** (Python, TensorFlow/Keras + FastAPI) — свёрточная нейросеть
  (CNN), классифицирующая описание транзакции в одну из 18 категорий.

```
Пользователь → Telegram-бот (Java) → REST → Нейросеть (Python) → категория
                     ↓
                PostgreSQL
```

## Запуск

### 1. Сервис нейросети
```bash
cd ml-service
pip install -r requirements.txt
python dataset.py        # генерация набора данных
python train.py          # обучение модели (создаёт model/ и reports/)
uvicorn app:app --host 0.0.0.0 --port 8000
```

### 2. Telegram-бот
Получите токен у @BotFather, затем:
```bash
cd bot-service
docker compose up -d     # PostgreSQL (из корня проекта)
export BOT_USERNAME=your_bot_username
export BOT_TOKEN=your_token
mvn spring-boot:run
```

Быстрый запуск без Docker (встроенная БД H2):
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

## Метрики модели
Accuracy 93%, Precision 94%, Recall 93%, F1 93% на 18 категориях
(см. `ml-service/reports/`).
