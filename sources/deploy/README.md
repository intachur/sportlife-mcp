# Развёртывание на Ubuntu (systemd)

## Параметры

| Параметр | Значение |
|---|---|
| Пользователь ОС | `chur` |
| Java | `/usr/bin/java` |
| Каталог приложения | `/opt/sportlife` |
| Jar | `/opt/sportlife/sportlife-mcp.jar` |
| Переменные окружения | `/opt/sportlife/sportlife.env` |
| Журнал | `/opt/sportlife/logs/app.log` |
| systemd unit | `/etc/systemd/system/sportlife-mcp.service` |

## Установка

1. Собрать jar:

```bash
mvn package
```

2. Токен и параметры БД задаются в `deploy/sportlife.env` (файл в `.gitignore`). Если его нет, `install.sh` возьмёт `deploy/sportlife.env.example`.

3. Запустить установку:

```bash
sudo ./deploy/install.sh
```

Скрипт создаёт каталоги, копирует jar и unit, включает и запускает сервис.

## Управление

```bash
sudo systemctl status sportlife-mcp
sudo systemctl restart sportlife-mcp
sudo systemctl stop sportlife-mcp
```

## Журнал

```bash
tail -f /opt/sportlife/logs/app.log
```

## Проверка

```bash
curl -s http://localhost:8080/health
```
