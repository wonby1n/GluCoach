#!/bin/sh
# 볼륨에 base 모델이 없을 때 이미지에서 복사
for item in scaler.pkl lstm_meal.pt lstm_now.pt stage2_meal; do
    if [ ! -e "/app/models/$item" ]; then
        echo "[entrypoint] copying $item from image to volume..."
        cp -r "/app/models_bak/$item" "/app/models/"
    fi
done

exec uvicorn app.main:app --host 0.0.0.0 --port 8000
