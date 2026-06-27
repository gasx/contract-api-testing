# Contract API Testing (Kotlin, console demo)

## Быстрый запуск (без сети, из файлов)
```bash
./gradlew run --args="--run configs/run_demo.json --out build/reports --web" --console=plain
```

Отчёты:
- `build/reports/report.json`
- `build/reports/junit.xml`


## Запуск с реальным API
```bash
./gradlew run --args="--run configs/run_presentation.json --out build/reports --web" --console=plain
```

## Важно про Java (Mac)
Если у вас стоит экспериментальная Java (например `valhalla-ea-23`), IDE/Gradle иногда запускают проект нестабильно.
Для защиты/демо рекомендуется обычная Temurin 17 или Temurin 21 и выбор этой Java как **Gradle JVM** в IntelliJ.
