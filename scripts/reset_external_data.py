import argparse
import os
from urllib.parse import urlparse

from minio import Minio
from pymongo import MongoClient
from redis import Redis


def env(name: str, default: str = "") -> str:
    return os.getenv(name, default)


def clear_mongo() -> None:
    uri = env("SIGNAL_MONGODB_URI", "mongodb://192.168.1.4:27017/ai_audit_signal")
    client = MongoClient(uri)
    database_name = urlparse(uri).path.lstrip("/") or "ai_audit_signal"
    database = client[database_name]
    database["fluorescence_curve"].delete_many({})
    client.close()


def clear_redis() -> None:
    client = Redis(
        host=env("REDIS_HOST", "192.168.1.4"),
        port=int(env("REDIS_PORT", "6379")),
        password=env("REDIS_PASSWORD") or None,
        decode_responses=True,
    )
    for pattern in ("ai-audit:statistics:v1:dashboard", "yo:audit:idempotency:*"):
        batch = []
        for key in client.scan_iter(match=pattern, count=500):
            batch.append(key)
            if len(batch) == 500:
                client.unlink(*batch)
                batch.clear()
        if batch:
            client.unlink(*batch)
    client.close()


def clear_minio() -> None:
    endpoint_value = env("MINIO_ENDPOINT", "http://192.168.1.4:9000")
    parsed = urlparse(endpoint_value)
    client = Minio(
        parsed.netloc or parsed.path,
        access_key=env("MINIO_ACCESS_KEY"),
        secret_key=env("MINIO_SECRET_KEY"),
        secure=parsed.scheme == "https",
    )
    targets = (
        (env("MINIO_INCOMING_BUCKET", "ai-audit-incoming"), ""),
        (env("TRACE_ARCHIVE_BUCKET", "ai-audit-archive"), "audit-manifest/"),
    )
    for bucket, prefix in targets:
        if not client.bucket_exists(bucket):
            continue
        for item in client.list_objects(bucket, prefix=prefix, recursive=True):
            client.remove_object(bucket, item.object_name)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    if not args.execute:
        raise SystemExit("Use --execute only through Reset-ImportedData.ps1")
    clear_mongo()
    clear_redis()
    clear_minio()


if __name__ == "__main__":
    main()
