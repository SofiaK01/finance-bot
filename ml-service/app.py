# -*- coding: utf-8 -*-
"""
REST-сервис распознавания категории транзакции.

Загружает обученную нейросеть и по HTTP-запросу от Telegram-бота
возвращает предсказанную категорию и уверенность модели.

Запуск:  uvicorn app:app --host 0.0.0.0 --port 8000
Проверка: POST /predict  {"text": "такси до дома 400"}
"""

import os
import re
import json

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel

import tensorflow as tf
from tensorflow.keras.preprocessing.text import tokenizer_from_json
from tensorflow.keras.preprocessing.sequence import pad_sequences

BASE = os.path.dirname(__file__)
MODEL_DIR = os.path.join(BASE, "model")
MAX_LEN = 10

# --- загрузка артефактов один раз при старте ---
model = tf.keras.models.load_model(os.path.join(MODEL_DIR, "transaction_cnn.keras"))
with open(os.path.join(MODEL_DIR, "tokenizer.json"), "r", encoding="utf-8") as f:
    tokenizer = tokenizer_from_json(f.read())
with open(os.path.join(MODEL_DIR, "labels.json"), "r", encoding="utf-8") as f:
    idx_to_cat = {int(k): v for k, v in json.load(f).items()}

app = FastAPI(title="FinTracker ML Service", version="1.0")


class PredictRequest(BaseModel):
    text: str


class PredictResponse(BaseModel):
    category: str
    confidence: float


def clean(text: str) -> str:
    text = str(text).lower()
    text = re.sub(r"[^а-яёa-z0-9 ]", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


@app.get("/health")
def health():
    return {"status": "ok", "classes": len(idx_to_cat)}


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    seq = tokenizer.texts_to_sequences([clean(req.text)])
    padded = pad_sequences(seq, maxlen=MAX_LEN, padding="post", truncating="post")
    probs = model.predict(padded, verbose=0)[0]
    idx = int(np.argmax(probs))
    return PredictResponse(category=idx_to_cat[idx], confidence=round(float(probs[idx]), 4))
