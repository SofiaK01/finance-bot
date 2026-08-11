# -*- coding: utf-8 -*-
"""
Обучение свёрточной нейронной сети (CNN) для классификации категории
транзакции по её текстовому описанию.

Архитектура: Embedding -> Conv1D -> GlobalMaxPooling1D -> Dense -> Softmax.
Выбор CNN обоснован тем, что свёрточные фильтры хорошо выделяют
локальные признаки (n-граммы) в коротких текстах описаний.

Результат работы:
  model/transaction_cnn.keras   — обученная модель
  model/tokenizer.json          — словарь токенизатора
  model/labels.json             — соответствие индекс -> категория
  reports/metrics.json          — метрики качества
  reports/confusion_matrix.png  — матрица ошибок
  reports/training_history.png  — графики обучения
"""

import os
import re
import json
import numpy as np
import pandas as pd
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, f1_score,
    classification_report, confusion_matrix,
)

import tensorflow as tf
from tensorflow.keras.preprocessing.text import Tokenizer
from tensorflow.keras.preprocessing.sequence import pad_sequences
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import (
    Embedding, Conv1D, GlobalMaxPooling1D, Dense, Dropout,
)
from tensorflow.keras.callbacks import EarlyStopping

BASE = os.path.dirname(__file__)
DATA_PATH = os.path.join(BASE, "data", "transactions.csv")
MODEL_DIR = os.path.join(BASE, "model")
REPORTS_DIR = os.path.join(BASE, "reports")

# --- гиперпараметры ---
MAX_WORDS = 3000      # размер словаря
MAX_LEN = 10          # максимальная длина последовательности (токенов)
EMBED_DIM = 64        # размерность векторного представления слов
FILTERS = 128         # число свёрточных фильтров
KERNEL_SIZE = 3       # размер ядра свёртки
EPOCHS = 40
BATCH_SIZE = 32
SEED = 42

np.random.seed(SEED)
tf.random.set_seed(SEED)


def clean(text: str) -> str:
    """Приведение к нижнему регистру и удаление лишних символов."""
    text = str(text).lower()
    text = re.sub(r"[^а-яёa-z0-9 ]", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def main():
    os.makedirs(MODEL_DIR, exist_ok=True)
    os.makedirs(REPORTS_DIR, exist_ok=True)

    df = pd.read_csv(DATA_PATH)
    df["text"] = df["text"].apply(clean)
    df = df[df["text"].str.len() > 0].reset_index(drop=True)

    categories = sorted(df["category"].unique())
    cat_to_idx = {c: i for i, c in enumerate(categories)}
    idx_to_cat = {i: c for c, i in cat_to_idx.items()}
    y = df["category"].map(cat_to_idx).values

    # Токенизация
    tokenizer = Tokenizer(num_words=MAX_WORDS, oov_token="<OOV>")
    tokenizer.fit_on_texts(df["text"])
    sequences = tokenizer.texts_to_sequences(df["text"])
    X = pad_sequences(sequences, maxlen=MAX_LEN, padding="post", truncating="post")

    # Разбиение 70/15/15 со стратификацией
    X_train, X_temp, y_train, y_temp = train_test_split(
        X, y, test_size=0.30, random_state=SEED, stratify=y
    )
    X_val, X_test, y_val, y_test = train_test_split(
        X_temp, y_temp, test_size=0.50, random_state=SEED, stratify=y_temp
    )

    num_classes = len(categories)
    y_train_c = tf.keras.utils.to_categorical(y_train, num_classes)
    y_val_c = tf.keras.utils.to_categorical(y_val, num_classes)

    # Архитектура CNN
    model = Sequential([
        Embedding(input_dim=MAX_WORDS, output_dim=EMBED_DIM, input_length=MAX_LEN),
        Conv1D(filters=FILTERS, kernel_size=KERNEL_SIZE, activation="relu"),
        GlobalMaxPooling1D(),
        Dense(64, activation="relu"),
        Dropout(0.5),
        Dense(num_classes, activation="softmax"),
    ])
    model.compile(optimizer="adam", loss="categorical_crossentropy", metrics=["accuracy"])
    model.summary()

    early = EarlyStopping(monitor="val_loss", patience=5, restore_best_weights=True)
    history = model.fit(
        X_train, y_train_c,
        validation_data=(X_val, y_val_c),
        epochs=EPOCHS, batch_size=BATCH_SIZE,
        callbacks=[early], verbose=2,
    )

    # Оценка на отложенной тестовой выборке
    y_pred = model.predict(X_test, verbose=0).argmax(axis=1)
    acc = accuracy_score(y_test, y_pred)
    prec = precision_score(y_test, y_pred, average="macro", zero_division=0)
    rec = recall_score(y_test, y_pred, average="macro", zero_division=0)
    f1 = f1_score(y_test, y_pred, average="macro", zero_division=0)

    print("\n=== Метрики на тестовой выборке ===")
    print(f"Accuracy : {acc:.4f}")
    print(f"Precision: {prec:.4f}")
    print(f"Recall   : {rec:.4f}")
    print(f"F1-score : {f1:.4f}\n")
    report = classification_report(
        y_test, y_pred, target_names=categories, zero_division=0
    )
    print(report)

    # Сохранение метрик
    metrics = {
        "accuracy": round(float(acc), 4),
        "precision_macro": round(float(prec), 4),
        "recall_macro": round(float(rec), 4),
        "f1_macro": round(float(f1), 4),
        "train_size": int(len(X_train)),
        "val_size": int(len(X_val)),
        "test_size": int(len(X_test)),
        "num_classes": num_classes,
        "epochs_trained": len(history.history["loss"]),
    }
    with open(os.path.join(REPORTS_DIR, "metrics.json"), "w", encoding="utf-8") as f:
        json.dump(metrics, f, ensure_ascii=False, indent=2)
    with open(os.path.join(REPORTS_DIR, "classification_report.txt"), "w", encoding="utf-8") as f:
        f.write(report)

    # Матрица ошибок
    cm = confusion_matrix(y_test, y_pred)
    fig, ax = plt.subplots(figsize=(11, 9))
    im = ax.imshow(cm, cmap="Blues")
    ax.set_xticks(range(num_classes))
    ax.set_yticks(range(num_classes))
    ax.set_xticklabels(categories, rotation=90, fontsize=8)
    ax.set_yticklabels(categories, fontsize=8)
    ax.set_xlabel("Предсказанная категория")
    ax.set_ylabel("Истинная категория")
    ax.set_title("Матрица ошибок классификатора транзакций")
    thresh = cm.max() / 2.0
    for i in range(num_classes):
        for j in range(num_classes):
            if cm[i, j] > 0:
                ax.text(j, i, cm[i, j], ha="center", va="center",
                        color="white" if cm[i, j] > thresh else "black", fontsize=7)
    fig.colorbar(im, ax=ax, fraction=0.046, pad=0.04)
    fig.tight_layout()
    fig.savefig(os.path.join(REPORTS_DIR, "confusion_matrix.png"), dpi=150)
    plt.close(fig)

    # Графики обучения
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 4))
    ax1.plot(history.history["accuracy"], label="train")
    ax1.plot(history.history["val_accuracy"], label="val")
    ax1.set_title("Точность (accuracy)")
    ax1.set_xlabel("Эпоха"); ax1.set_ylabel("Accuracy"); ax1.legend()
    ax2.plot(history.history["loss"], label="train")
    ax2.plot(history.history["val_loss"], label="val")
    ax2.set_title("Функция потерь (loss)")
    ax2.set_xlabel("Эпоха"); ax2.set_ylabel("Loss"); ax2.legend()
    fig.tight_layout()
    fig.savefig(os.path.join(REPORTS_DIR, "training_history.png"), dpi=150)
    plt.close(fig)

    # Сохранение артефактов модели
    model.save(os.path.join(MODEL_DIR, "transaction_cnn.keras"))
    with open(os.path.join(MODEL_DIR, "tokenizer.json"), "w", encoding="utf-8") as f:
        f.write(tokenizer.to_json())
    with open(os.path.join(MODEL_DIR, "labels.json"), "w", encoding="utf-8") as f:
        json.dump(idx_to_cat, f, ensure_ascii=False, indent=2)

    print("Артефакты сохранены в model/ и reports/")


if __name__ == "__main__":
    main()
