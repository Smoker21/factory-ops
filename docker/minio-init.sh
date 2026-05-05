#!/bin/sh
# MinIO bucket 初始化腳本
# 等待 MinIO 就緒後建立 factory-ops-attachments bucket

set -e

echo "Waiting for MinIO to be ready..."
until mc alias set local http://minio:9000 "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}" 2>/dev/null; do
  sleep 2
done

echo "Creating bucket: ${MINIO_BUCKET:-factory-ops-attachments}"
mc mb --ignore-existing "local/${MINIO_BUCKET:-factory-ops-attachments}"
mc anonymous set none "local/${MINIO_BUCKET:-factory-ops-attachments}"

echo "MinIO initialization complete."
