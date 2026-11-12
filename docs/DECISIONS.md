D-001: контейнер = JSON + base64, UTF-8/LF (паритет платформ, отлаживаемость)
D-002: KDF = Argon2id через BouncyCastle (чистая Java, без JNI)
D-003: миграций нет; старые базы не читаем; перенос руками
D-004: single Activity + Compose Navigation; гейт по состоянию сессии
D-005: синк = общий файл в синхронизируемой папке + детерминированный merge по updatedAt
D-006: JSON-либа ядра = kotlinx.serialization (строгая схема, KMP-совместимость на будущее)
