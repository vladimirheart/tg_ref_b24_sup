# panel/app.py
from flask import Flask, render_template, request, jsonify, redirect, url_for, session, Response, send_file
import sqlite3
import datetime
import requests
import json
import os
import logging
import time, sqlite3
def exec_with_retry(fn, retries=5, base_delay=0.15):
    for i in range(retries):
        try:
            return fn()
        except sqlite3.OperationalError as e:
            if "database is locked" in str(e).lower() and i < retries - 1:
                time.sleep(base_delay * (i + 1))  # backoff
                continue
            raise
from zoneinfo import ZoneInfo
from apscheduler.schedulers.background import BackgroundScheduler
from threading import Timer
from datetime import datetime as dt, timedelta, timezone
from werkzeug.security import generate_password_hash, check_password_hash
import io
import mimetypes
from werkzeug.utils import secure_filename

BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ATTACHMENTS_DIR = os.path.join(BASE_DIR, "attachments")
TICKETS_DB_PATH = os.path.join(BASE_DIR, "tickets.db")
USERS_DB_PATH = os.path.join(BASE_DIR, "users.db")
LOCATIONS_PATH = os.path.join(BASE_DIR, "locations.json")

app = Flask(__name__)
app.secret_key = os.getenv("SECRET_KEY")

# === НАСТРОЙКИ ===
from dotenv import load_dotenv
import os

load_dotenv()

TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
GROUP_CHAT_ID = int(os.getenv("GROUP_CHAT_ID"))

PARAMETER_TYPES = {
    "business": "Бизнес",
    "partner_type": "Тип партнёра",
    "country": "Страна",
    "legal_entity": "ЮЛ",
    "department": "Департамент",
}

# Подключение к базе
def get_db():
    # autocommit (isolation_level=None) + увеличенный timeout
    conn = sqlite3.connect(TICKETS_DB_PATH, timeout=30, isolation_level=None)
    conn.row_factory = sqlite3.Row
    # режим WAL и адекватный busy_timeout на всякий случай
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA busy_timeout=30000;")
    conn.execute("PRAGMA synchronous=NORMAL;")
    return conn

def get_users_db():
    conn = sqlite3.connect(USERS_DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def _init_sqlite():
    with get_db() as conn:
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA busy_timeout=5000;")
_init_sqlite()

def ensure_tasks_schema():
    with get_db() as conn:
        conn.execute("""
        CREATE TABLE IF NOT EXISTS tasks(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            seq INTEGER NOT NULL,               -- порядковый номер (целый, инкремент)
            source TEXT,                        -- 'DL' или NULL
            title TEXT,
            body_html TEXT,
            creator TEXT,
            assignee TEXT,
            tag TEXT,
            status TEXT DEFAULT 'Новая',
            due_at TEXT,                        -- ISO
            created_at TEXT DEFAULT (datetime('now')),
            closed_at TEXT,                     -- ISO
            last_activity_at TEXT DEFAULT (datetime('now'))
        )
        """)
        conn.execute("""
        CREATE TABLE IF NOT EXISTS task_people(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL,
            role TEXT NOT NULL,                 -- 'co' или 'watcher'
            identity TEXT NOT NULL,
            UNIQUE(task_id, role, identity),
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
        )
        """)
        conn.execute("""
        CREATE TABLE IF NOT EXISTS task_comments(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL,
            author TEXT,
            html TEXT,
            created_at TEXT DEFAULT (datetime('now')),
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
        )
        """)
        conn.execute("""
        CREATE TABLE IF NOT EXISTS task_history(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            task_id INTEGER NOT NULL,
            at TEXT DEFAULT (datetime('now')),
            text TEXT,
            FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE
        )
        """)
        conn.execute("""
        CREATE TABLE IF NOT EXISTS ticket_active(
            ticket_id TEXT PRIMARY KEY,
            user      TEXT NOT NULL,
            last_seen TEXT DEFAULT (datetime('now'))
        )
        """)
        conn.execute("""
        CREATE TABLE IF NOT EXISTS task_links(
            task_id   INTEGER NOT NULL,
            ticket_id TEXT    NOT NULL,
            PRIMARY KEY(task_id, ticket_id)
        )
        """)
        conn.execute("""
        CREATE TABLE IF NOT EXISTS notifications(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user TEXT NOT NULL,
            text TEXT NOT NULL,
            url TEXT,
            is_read INTEGER DEFAULT 0,
            created_at TEXT DEFAULT (datetime('now'))
        )
        """)
        # автоинкремент для seq через служебную таблицу
        conn.execute("""
        CREATE TABLE IF NOT EXISTS task_seq(
            id INTEGER PRIMARY KEY CHECK (id=1),
            val INTEGER NOT NULL
        )
        """)
        cur = conn.execute("SELECT val FROM task_seq WHERE id=1").fetchone()
        if not cur:
            conn.execute("INSERT INTO task_seq(id,val) VALUES(1,0)")

ensure_tasks_schema()

def ensure_client_blacklist_schema():
    """
    Таблица блэклиста клиентов:
      - user_id (PK)
      - is_blacklisted: 0/1
      - reason: причина
      - added_at: ISO
      - added_by: оператор
      - unblock_requested: 0/1 (клиент нажал «запросить разблокировку»)
      - unblock_requested_at: ISO
    """
    try:
        with get_db() as conn:
            conn.execute("""
            CREATE TABLE IF NOT EXISTS client_blacklist (
                user_id TEXT PRIMARY KEY,
                is_blacklisted INTEGER NOT NULL DEFAULT 0,
                reason TEXT,
                added_at TEXT,
                added_by TEXT,
                unblock_requested INTEGER NOT NULL DEFAULT 0,
                unblock_requested_at TEXT
            )
            """)
    except Exception as e:
        print(f"ensure_client_blacklist_schema: {e}")

ensure_client_blacklist_schema()


def ensure_settings_parameters_schema():
    try:
        with get_db() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS settings_parameters (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    param_type TEXT NOT NULL,
                    value TEXT NOT NULL,
                    created_at TEXT DEFAULT (datetime('now')),
                    UNIQUE(param_type, value)
                )
                """
            )
    except Exception as e:
        print(f"ensure_settings_parameters_schema: {e}")


ensure_settings_parameters_schema()

def _collect_departments_from_locations() -> tuple[list[str], set[str]]:
    """
    Возвращает:
    - отсортированный список названий департаментов (четвёртая ветка дерева
      locations.json: бизнес → тип → город → точка);
    - множество названий третьего уровня (городов/веток) для последующей
      очистки устаревших значений.
    """

    try:
        with open(LOCATIONS_PATH, "r", encoding="utf-8") as f:
            locations = json.load(f)
    except Exception:
        return [], set()

    departments: set[str] = set()
    third_level_names: set[str] = set()

    for brand in locations.values():
        if not isinstance(brand, dict):
            continue

        for partner_type in brand.values():
            if not isinstance(partner_type, dict):
                continue

            for city_name, city_departments in partner_type.items():
                if isinstance(city_name, str) and city_name.strip():
                    third_level_names.add(city_name.strip())

                if isinstance(city_departments, dict):
                    iterable = city_departments.values()
                elif isinstance(city_departments, list):
                    iterable = city_departments
                else:
                    continue

                for department_name in iterable:
                    if isinstance(department_name, str) and department_name.strip():
                        departments.add(department_name.strip())

    return (
        sorted(departments, key=lambda name: name.lower()),
        third_level_names,
    )


def ensure_departments_seeded():
    """Добавляет значения департаментов на основе locations.json, если их нет."""

    departments, obsolete_candidates = _collect_departments_from_locations()
    if not departments:
        return

    department_set = set(departments)

    try:
        with get_db() as conn:
            existing_rows = conn.execute(
                "SELECT value FROM settings_parameters WHERE param_type = ?",
                ("department",),
            ).fetchall()
            existing = {row["value"] for row in existing_rows if row["value"]}

            obsolete = [
                value
                for value in existing
                if value in obsolete_candidates and value not in department_set
            ]
            if obsolete:
                conn.executemany(
                    "DELETE FROM settings_parameters WHERE param_type = ? AND value = ?",
                    [("department", value) for value in obsolete],
                )

            missing = [dep for dep in departments if dep not in existing]
            if not missing:
                return

            conn.executemany(
                "INSERT OR IGNORE INTO settings_parameters (param_type, value) VALUES (?, ?)",
                [("department", dep) for dep in missing],
            )
            conn.commit()
    except Exception as e:
        logging.warning("Не удалось заполнить департаменты: %s", e)


ensure_departments_seeded()

def create_unblock_request_task(user_id: str, reason: str = ""):
    """
    Создаёт задачу оператору о запросе разблокировки клиента.
    """
    try:
        with get_db() as conn:
            cur = conn.cursor()
            # аккуратно вычислим следующий seq
            row = cur.execute("SELECT COALESCE(MAX(seq),0)+1 AS n FROM tasks").fetchone()
            seq = (row["n"] if row and "n" in row.keys() else 1)
            title = f"Запрос разблокировки клиента {user_id}"
            body_html = f"<p>Клиент {user_id} запросил разблокировку.</p>" + (f"<p>Комментарий: {reason}</p>" if reason else "")
            cur.execute("""
                INSERT INTO tasks (seq, source, title, body_html, creator, tag, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, (seq, "DL", title, body_html, "system", "unblock_request", "Новая"))
            conn.commit()
    except Exception as e:
        print(f"create_unblock_request_task: {e}")

def _next_task_seq(conn):
    row = conn.execute("SELECT val FROM task_seq WHERE id=1").fetchone()
    cur = int(row["val"])
    conn.execute("UPDATE task_seq SET val = ? WHERE id=1", (cur+1,))
    return cur + 1

def _display_no(source, seq):
    return f"{source}_{seq}" if source else str(seq)

def _touch_activity(conn, task_id):
    conn.execute("UPDATE tasks SET last_activity_at = datetime('now') WHERE id=?", (task_id,))

def ensure_channels_schema():
    """Добавляет недостающие колонки в channels, если база старая."""
    try:
        conn = get_db()
        cur = conn.cursor()
        cols = {r['name'] for r in cur.execute("PRAGMA table_info(channels)").fetchall()}
        # ожидаемые поля: id, token, bot_name, channel_name, questions_cfg, max_questions, is_active
        if 'bot_name' not in cols:
            cur.execute("ALTER TABLE channels ADD COLUMN bot_name TEXT")
        if 'channel_name' not in cols:
            cur.execute("ALTER TABLE channels ADD COLUMN channel_name TEXT")
        if 'questions_cfg' not in cols:
            cur.execute("ALTER TABLE channels ADD COLUMN questions_cfg TEXT DEFAULT '{}'")
        if 'max_questions' not in cols:
            cur.execute("ALTER TABLE channels ADD COLUMN max_questions INTEGER DEFAULT 0")
        if 'is_active' not in cols:
            cur.execute("ALTER TABLE channels ADD COLUMN is_active INTEGER DEFAULT 1")
        conn.commit()
    except Exception as e:
        print(f"ensure_channels_schema: {e}")
    finally:
        try: conn.close()
        except: pass
ensure_channels_schema()

def ensure_feedback_requests_table():
    try:
        conn = get_db()
        conn.execute("""
            CREATE TABLE IF NOT EXISTS pending_feedback_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                channel_id INTEGER NOT NULL,
                ticket_id TEXT,
                source TEXT,
                created_at TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                UNIQUE(user_id, channel_id, ticket_id, source)
            )
        """)
        conn.commit()
    except Exception as e:
        print(f"ensure_feedback_requests_table: {e}")
    finally:
        try: conn.close()
        except: pass

ensure_feedback_requests_table()

def ensure_client_profile_schema():
    try:
        conn = get_db()
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE IF NOT EXISTS client_usernames (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                username TEXT NOT NULL,
                seen_at TEXT NOT NULL,
                UNIQUE(user_id, username)
            )
        """)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS client_phones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                phone TEXT NOT NULL,
                label TEXT,
                source TEXT NOT NULL CHECK (source IN ('telegram','manual')),
                is_active INTEGER DEFAULT 1,
                created_at TEXT NOT NULL,
                created_by TEXT
            )
        """)
        conn.commit()
    except Exception as e:
        print(f"ensure_client_profile_schema: {e}")
    finally:
        try: conn.close()
        except: pass

ensure_client_profile_schema()

def ensure_history_mark_columns():
    try:
        conn = get_db()
        cur = conn.cursor()
        cur.execute("PRAGMA table_info(chat_history)")
        cols = {r['name'] for r in cur.fetchall()}
        if 'edited_at' not in cols:
            cur.execute("ALTER TABLE chat_history ADD COLUMN edited_at TEXT")
        if 'deleted_at' not in cols:
            cur.execute("ALTER TABLE chat_history ADD COLUMN deleted_at TEXT")
        conn.commit()
    except Exception as e:
        print(f"ensure_history_mark_columns: {e}")
    finally:
        try: conn.close()
        except: pass

# === Миграция: столбцы для reply/thread ===
def ensure_history_reply_columns():
    try:
        conn = get_db()
        cur = conn.cursor()
        cols = {r['name'] for r in cur.execute("PRAGMA table_info(chat_history)").fetchall()}
        if "tg_message_id" not in cols:
            cur.execute("ALTER TABLE chat_history ADD COLUMN tg_message_id INTEGER")
        if "reply_to_tg_id" not in cols:
            cur.execute("ALTER TABLE chat_history ADD COLUMN reply_to_tg_id INTEGER")
        conn.commit()
    except Exception as e:
        print(f"ensure_history_reply_columns: {e}")
    finally:
        try: conn.close()
        except: pass
        commit()

    ensure_history_mark_columns()
    ensure_history_reply_columns()

def ensure_ticket_time_schema():
    """ticket_spans + счётчики reopen/closed в tickets."""
    try:
        conn = get_db()
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE IF NOT EXISTS ticket_spans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ticket_id TEXT NOT NULL,
                span_no INTEGER NOT NULL,
                started_at TEXT NOT NULL,
                ended_at   TEXT,
                duration_seconds INTEGER,
                UNIQUE(ticket_id, span_no)
            )
        """)
        # Добавляем счётчики в tickets
        cols = {r['name'] for r in cur.execute("PRAGMA table_info(tickets)").fetchall()}
        if 'reopen_count' not in cols:
            cur.execute("ALTER TABLE tickets ADD COLUMN reopen_count INTEGER DEFAULT 0")
        if 'closed_count' not in cols:
            cur.execute("ALTER TABLE tickets ADD COLUMN closed_count INTEGER DEFAULT 0")
        conn.commit()
    except Exception as e:
        print(f"ensure_ticket_time_schema: {e}")
    finally:
        try: conn.close()
        except: pass

ensure_ticket_time_schema()

def _active_span(conn, ticket_id: str):
    return conn.execute(
        "SELECT id, span_no, started_at FROM ticket_spans WHERE ticket_id=? AND ended_at IS NULL",
        (ticket_id,)
    ).fetchone()

def _next_span_no(conn, ticket_id: str) -> int:
    row = conn.execute("SELECT MAX(span_no) AS mx FROM ticket_spans WHERE ticket_id=?", (ticket_id,)).fetchone()
    return (row['mx'] or 0) + 1

def start_span_if_missing(ticket_id: str):
    conn = get_db()
    try:
        cur = conn.cursor()
        if not _active_span(conn, ticket_id):
            span_no = _next_span_no(conn, ticket_id)
            cur.execute(
                "INSERT INTO ticket_spans(ticket_id, span_no, started_at) VALUES(?,?,?)",
                (ticket_id, span_no, dt.now().isoformat())
            )
            conn.commit()
    finally:
        try: conn.close()
        except: pass

def close_active_span(ticket_id: str):
    conn = get_db()
    try:
        cur = conn.cursor()
        sp = _active_span(conn, ticket_id)
        if sp:
            ended = dt.now()
            dur = int((ended - datetime.datetime.fromisoformat(sp['started_at'])).total_seconds())
            cur.execute(
                "UPDATE ticket_spans SET ended_at=?, duration_seconds=? WHERE id=?",
                (ended.isoformat(), dur, sp['id'])
            )
            conn.commit()
    finally:
        try: conn.close()
        except: pass

def open_new_span(ticket_id: str):
    conn = get_db()
    try:
        cur = conn.cursor()
        span_no = _next_span_no(conn, ticket_id)
        cur.execute(
            "INSERT INTO ticket_spans(ticket_id, span_no, started_at) VALUES(?,?,?)",
            (ticket_id, span_no, dt.now().isoformat())
        )
        conn.commit()
    finally:
        try: conn.close()
        except: pass

def get_latest_client_name(user_id):
    """Получить последнее имя клиента из таблицы messages"""
    try:
        conn = get_db()
        cur = conn.cursor()
        cur.execute("""
            SELECT client_name FROM messages
            WHERE user_id = ? AND client_name IS NOT NULL AND client_name != ''
            ORDER BY created_at DESC
            LIMIT 1
        """, (user_id,))
        row = cur.fetchone()
        conn.close()
        return row['client_name'] if row else None
    except Exception as e:
        print(f"Ошибка при получении имени клиента: {e}")
        return None

# Функция для загрузки настроек
def load_settings():
    settings = {"auto_close_hours": 24, "categories": ["Консультация"], "client_statuses": ["Новый", "Постоянный", "VIP"]}
    if os.path.exists("../settings.json"):
        try:
            with open("../settings.json", "r", encoding="utf-8") as f:
                settings.update(json.load(f))
        except:
            pass
    return settings

# Функция для сжатия изображений на лету
import io
import mimetypes
def resize_image(image_path, max_size=600):
    """Сжимает изображение до максимального размера"""
    try:
        from PIL import Image
            
        with Image.open(image_path) as img:
            # Конвертируем в RGB если нужно
            if img.mode in ('RGBA', 'P'):
                img = img.convert('RGB')
            
            # Проверяем, нужно ли изменять размер
            if img.width <= max_size and img.height <= max_size:
                # Если изображение уже меньше максимального размера, возвращаем оригинал
                buffer = io.BytesIO()
                img.save(buffer, format='JPEG', quality=85, optimize=True)
                buffer.seek(0)
                return buffer
            
            # Изменяем размер сохраняя пропорции
            img.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)
            
            # Сохраняем в буфер
            buffer = io.BytesIO()
            img.save(buffer, format='JPEG', quality=85, optimize=True)
            buffer.seek(0)
            
            return buffer
    except ImportError:
        # Pillow не установлен — просто вернём оригинал, без ресайза
        return None
    except Exception as e:
        print(f"Ошибка при обработке изображения: {e}")
        return None

# Функция для сохранения настроек
def save_settings(settings):
    with open("../settings.json", "w", encoding="utf-8") as f:
        json.dump(settings, f, ensure_ascii=False, indent=2)

# Функция для загрузки локаций
def load_locations():
    locations = {}
    if os.path.exists(LOCATIONS_PATH):
        try:
            with open(LOCATIONS_PATH, "r", encoding="utf-8") as f:
                locations = json.load(f)
        except Exception:
            pass
    return locations

# Функция для сохранения локаций
def save_locations(locations):
    with open(LOCATIONS_PATH, "w", encoding="utf-8") as f:
        json.dump(locations, f, ensure_ascii=False, indent=2)

def format_time_duration(minutes):
    """
    Форматирует время в минутах в читаемый формат (часы и минуты)
    
    Args:
        minutes (int): количество минут
        
    Returns:
        str: отформатированная строка времени
    """
    if minutes is None:
        return "0 ч 0 мин"
    
    try:
        minutes = int(minutes)
    except (ValueError, TypeError):
        return "0 ч 0 мин"
    
    hours = minutes // 60
    mins = minutes % 60
    
    if hours == 0:
        return f"{mins} мин"
    elif mins == 0:
        return f"{hours} ч"
    else:
        return f"{hours} ч {mins} мин" 
    
# === Авторизация ===
@app.route("/login", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        username = request.form["username"]
        password = request.form["password"]
        with get_users_db() as conn:
            user = conn.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
        if user and (user["password"] == password or check_password_hash(user["password"], password)):
            # унифицируем ключи сессии, чтобы их видели и страницы, и API
            session.clear()
            session["user"] = username                 # твой текущий ключ, на нём завязан login_required
            session["role"] = user["role"]

            # универсальные ключи для API и шаблонов (tasks.html берёт user_email)
            session["logged_in"] = True
            session["user_id"] = user.get("id") if isinstance(user, dict) else user["id"]
            session["username"] = username
            session["user_email"] = username  # если реальной почты нет — используем username

            return redirect(url_for("index"))
        else:
            return "Неверный логин или пароль", 401
    return render_template("login.html")

@app.route("/logout")
def logout():
    session.clear()
    return redirect(url_for("login"))

# === Декоратор для защиты маршрутов ===
def login_required(f):
    def decorated(*args, **kwargs):
        if "user" not in session:
            return redirect(url_for("login"))
        return f(*args, **kwargs)
    decorated.__name__ = f.__name__
    return decorated

from functools import wraps
from flask import jsonify, session

def _is_authenticated():
    # считаем авторизованным, если присутствует ЛЮБОЙ из признаков
    return bool(
        session.get('user') or
        session.get('logged_in') or
        session.get('user_id') or
        session.get('username') or
        session.get('user_email')
    )

def login_required_api(f):
    @wraps(f)
    def wrapper(*args, **kwargs):
        if not _is_authenticated():
            return jsonify({'error': 'auth', 'message': 'unauthorized'}), 401
        return f(*args, **kwargs)
    return wrapper

# === Отправка сообщения через Telegram API ===
def send_telegram_message(chat_id, text, parse_mode='HTML', **extra):
    """
    Отправка текста, поддерживает reply_to_message_id и пр. через **extra.
    Возвращает (ok: bool, result_or_error: dict|str)
    """
    try:
        # аккуратный фикс суррогатов (эмодзи)
        if isinstance(text, str):
            text = text.encode('utf-16', 'surrogatepass').decode('utf-16')

        url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
        payload = {
            'chat_id': chat_id,
            'text': text,
            'parse_mode': parse_mode,
            'disable_web_page_preview': True
        }
        if extra:
            payload.update(extra)

        r = requests.post(url, json=payload, timeout=20)
        j = r.json()
        if j.get('ok'):
            return True, j['result']  # здесь есть message_id
        return False, j.get('description', 'Unknown Telegram error')
    except Exception as e:
        return False, str(e)

# --- Починка "ломаных" эмодзи из браузера (lone surrogates) ---
def _fix_surrogates(s: str) -> str:
    if not isinstance(s, str):
        return s
    try:
        # перекодируем через UTF-16 с surrogatepass, чтобы склеить пары
        return s.encode('utf-16', 'surrogatepass').decode('utf-16')
    except Exception:
        return s
# --- Лимит 3 и запуск нового «спана» ---
def reopen_ticket_if_needed(ticket_id: str) -> bool | str:
    """
    Если тикет закрыт — переоткрываем (pending), увеличиваем reopen_count и открываем новый «спан».
    Лимит на переоткрытия: 3. Возвращает True | "LIMIT_EXCEEDED" | False.
    """
    try:
        conn = get_db()
        cur = conn.cursor()
        row = cur.execute(
            "SELECT status, COALESCE(reopen_count,0) AS rc FROM tickets WHERE ticket_id = ?",
            (ticket_id,)
        ).fetchone()

        if row and (row["status"] == "resolved"):
            if row["rc"] >= 3:
                return "LIMIT_EXCEEDED"
            cur.execute("""
                UPDATE tickets
                   SET status='pending',
                       resolved_by=NULL,
                       resolved_at=NULL,
                       reopen_count=COALESCE(reopen_count,0)+1
                 WHERE ticket_id=?
            """, (ticket_id,))
            conn.commit()
            # новый спан
            open_new_span(ticket_id)
            return True
        return False
    except Exception as e:
        app.logger.error(f"reopen_ticket_if_needed error: {e}")
        return False
    finally:
        try:
            conn.close()
        except Exception:
            pass

# === МАРШРУТЫ ===
from datetime import datetime as dt, timedelta, timezone
@app.route("/")
@login_required
def index():
    settings = {"categories": ["Консультация", "Другое"]}
    if os.path.exists("../settings.json"):
        with open("../settings.json", "r", encoding="utf-8") as f:
            settings = json.load(f)

    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
    SELECT 
        m.ticket_id,
        m.user_id,
        m.username as username,
        m.client_name as client_name,
        m.business,
        m.city,
        m.location_name,
        m.problem,
        m.created_at,
        t.status,
        t.resolved_by,
        t.resolved_at,
        m.created_date,
        m.created_time,
        cs.status as client_status
    FROM messages m
    LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
    LEFT JOIN client_statuses cs 
        ON cs.user_id = m.user_id
        AND cs.updated_at = (
            SELECT MAX(updated_at) 
            FROM client_statuses 
            WHERE user_id = m.user_id
 )
    ORDER BY m.created_at DESC
    """)
    tickets = cur.fetchall()
    conn.close()

    total = len(tickets)
    resolved = len([t for t in tickets if t['status'] == 'resolved'])
    pending = total - resolved

    return render_template(
        "index.html",
        tickets=tickets,
        total=total,
        resolved=resolved,
        pending=pending,
        settings=settings,
    )

# serve_media для обработки изображений:
@app.route("/media/<ticket_id>/<path:filename>")
@login_required
def serve_media(ticket_id, filename):
    try:
        # Нормализуем идентификаторы и отбрасываем подкаталоги
        safe_ticket = secure_filename(ticket_id)
        base_name = os.path.basename(filename)  # режем "attachments/<id>/" и любую вложенность
        safe_name = secure_filename(base_name)

        file_path = os.path.join(ATTACHMENTS_DIR, safe_ticket, safe_name)

        if not (file_path.startswith(os.path.join(ATTACHMENTS_DIR, safe_ticket)) and os.path.isfile(file_path)):
            return "Файл не найден", 404

        mime_type, _ = mimetypes.guess_type(file_path)
        return send_file(file_path, mimetype=mime_type or 'application/octet-stream', as_attachment=False)
    except Exception as e:
        print(f"Ошибка при обслуживании медиафайла: {e}")
        return "Ошибка загрузки файла", 500

@app.route("/avatar/<int:user_id>")
@login_required
def avatar(user_id):
    """
    Отдаёт аватар клиента.
    - По умолчанию: маленькая версия (кэш: <user_id>.jpg)
    - При ?full=1: максимальная версия (кэш: <user_id>_full.jpg)
    Если фото нет — прозрачный пиксель.
    """
    import os, requests
    from flask import send_file, request

    AVA_DIR = os.path.join(ATTACHMENTS_DIR, "avatars")
    os.makedirs(AVA_DIR, exist_ok=True)

    want_full = (request.args.get("full", "").lower() in ("1", "true", "yes"))
    cache_name = f"{user_id}_full.jpg" if want_full else f"{user_id}.jpg"
    cache_path = os.path.join(AVA_DIR, cache_name)

    # 1) Есть в кэше — отдаём
    if os.path.exists(cache_path):
        return send_file(cache_path, mimetype="image/jpeg")

    # 2) Тянем из Telegram
    try:
        resp = requests.get(
            f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/getUserProfilePhotos",
            params={"user_id": user_id, "limit": 1},
            timeout=10
        ).json()
        photos = (resp.get("result") or {}).get("photos") or []
        if not photos:
            # прозрачный пиксель
            from io import BytesIO
            from PIL import Image
            img = Image.new("RGBA", (1, 1), (0, 0, 0, 0))
            bio = BytesIO()
            img.save(bio, format="PNG")
            bio.seek(0)
            return send_file(bio, mimetype="image/png")

        # маленькая или максимальная версия
        size_idx = -1 if want_full else 0
        file_id = photos[0][size_idx]["file_id"]

        resp2 = requests.get(
            f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/getFile",
            params={"file_id": file_id},
            timeout=10
        ).json()
        file_path = (resp2.get("result") or {}).get("file_path")
        if not file_path:
            raise RuntimeError("file_path not found")

        url = f"https://api.telegram.org/file/bot{TELEGRAM_BOT_TOKEN}/{file_path}"
        binresp = requests.get(url, timeout=10)
        binresp.raise_for_status()
        with open(cache_path, "wb") as f:
            f.write(binresp.content)

        return send_file(cache_path, mimetype="image/jpeg")
    except Exception:
        # прозрачный пиксель на любой сбой
        from io import BytesIO
        from PIL import Image
        img = Image.new("RGBA", (1, 1), (0, 0, 0, 0))
        bio = BytesIO()
        img.save(bio, format="PNG")
        bio.seek(0)
        return send_file(bio, mimetype="image/png")


# === Обновление имени клиента ===
@app.route("/update_client_name", methods=["POST"])
@login_required
def update_client_name():
    data = request.json
    user_id = data["user_id"]
    client_name = data["client_name"]
    
    try:
        conn = get_db()
        # Обновляем имя во всех записях messages для этого пользователя
        conn.execute(
            "UPDATE messages SET client_name = ? WHERE user_id = ?",
            (client_name, user_id)
        )
        conn.commit()
        conn.close()
        return jsonify({"success": True})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

# === Обновление категории обращения ===
@app.route("/update_ticket_category", methods=["POST"])
@login_required
def update_ticket_category():
    data = request.json
    ticket_id = data["ticket_id"]
    category = data["category"]
    
    try:
        conn = get_db()
        conn.execute(
            "UPDATE messages SET category = ? WHERE ticket_id = ?",
            (category, ticket_id)
        )
        conn.commit()
        conn.close()
        return jsonify({"success": True})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

# === временная проверка можно удалить ===
@app.route("/api/whoami")
def whoami():
    # покажет, что реально лежит в session во время XHR
    from flask import jsonify, session, request
    return jsonify({
        "path": request.path,
        "cookies_sent": bool(request.cookies),
        "session_keys": list(session.keys()),
        "session": {
            k: session.get(k) for k in [
                "user", "role",
                "logged_in", "user_id", "username", "user_email"
            ]
        }
    })

# === временная проверка можно удалить ===
@app.route("/api/ping_auth")
def ping_auth():
    # быстро проверим, видит ли сервер вообще куку
    from flask import jsonify, session
    return jsonify({"ok": True, "has_user": bool(session.get("user"))})

# === Получение статуса клиента для карточки ===
@app.route("/client/<int:user_id>/status_info")
@login_required
def get_client_status(user_id):
    try:
        conn = get_db()
        cur = conn.cursor()
        cur.execute(
            "SELECT status FROM client_statuses WHERE user_id = ?",
            (user_id,)
        )
        status_row = cur.fetchone()
        conn.close()
        
        if status_row:
            return jsonify({"status": status_row['status']})
        else:
            return jsonify({"status": None})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route("/client/<int:user_id>/phones", methods=["GET"])
@login_required
def get_client_phones(user_id):
    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
        SELECT id, phone, label, source, is_active, created_at, created_by
        FROM client_phones WHERE user_id=?
        ORDER BY source DESC, created_at DESC
    """, (user_id,))
    rows = [dict(r) for r in cur.fetchall()]
    conn.close()
    return jsonify(rows)

@app.route("/client/<int:user_id>/phones", methods=["POST"])
@login_required
def add_client_phone(user_id):
    data = request.get_json(force=True)
    phone = (data.get("phone") or "").strip()
    label = (data.get("label") or "").strip() or None
    if not phone:
        return jsonify({"success": False, "error": "phone required"}), 400
    # простая нормализация
    phone_norm = ''.join(ch for ch in phone if ch.isdigit() or ch == '+')
    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
        INSERT INTO client_phones(user_id, phone, label, source, is_active, created_at, created_by)
        VALUES (?,?,?,?,1,?,?)
    """, (user_id, phone_norm, label, 'manual', dt.now().isoformat(), session.get("user","panel")))
    conn.commit()
    new_id = cur.lastrowid
    conn.close()
    return jsonify({"success": True, "id": new_id})

@app.route("/client/<int:user_id>/phones/<int:phone_id>", methods=["PATCH"])
@login_required
def update_client_phone(user_id, phone_id):
    data = request.get_json(force=True)
    label = data.get("label")
    is_active = data.get("is_active")
    sets, params = [], []
    if label is not None:
        sets.append("label = ?"); params.append(label)
    if is_active is not None:
        sets.append("is_active = ?"); params.append(1 if is_active else 0)
    if not sets:
        return jsonify({"success": False, "error": "nothing to update"}), 400
    params.extend([user_id, phone_id])
    conn = get_db()
    conn.execute(f"UPDATE client_phones SET {', '.join(sets)} WHERE user_id=? AND id=?", params)
    conn.commit(); conn.close()
    return jsonify({"success": True})

# === Обновление клиентской информации ===
@app.route("/update_client", methods=["POST"])
@login_required
def update_client():
    data = request.json
    user_id = data["user_id"]
    client_name = data.get("client_name", "")
    username = data.get("username", "")
    
    try:
        conn = get_db()
        # Обновляем имя и username во всех записях messages для этого пользователя
        conn.execute(
            "UPDATE messages SET client_name = ?, username = ? WHERE user_id = ?",
            (client_name, username, user_id)
        )
        conn.commit()
        conn.close()
        return jsonify({"success": True})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

@app.post("/message_edit")
@login_required
def message_edit():
    try:
        user_id = int(request.form["user_id"])
        ticket_id = request.form["ticket_id"]
        tg_message_id = int(request.form["tg_message_id"])
        new_text = _fix_surrogates(request.form.get("text", ""))

        # 1) пытаемся отредактировать сообщение у клиента (если это было сообщение поддержки)
        ok = False
        try:
            url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/editMessageText"
            payload = {
                "chat_id": user_id,
                "message_id": tg_message_id,
                "text": new_text,
                "parse_mode": "HTML",
                "disable_web_page_preview": True
            }
            r = requests.post(url, json=payload, timeout=15).json()
            ok = bool(r.get("ok"))
        except Exception:
            ok = False

        # 2) помечаем в истории (в базе панели)
        conn = get_db()
        from datetime import datetime, timezone
        edited_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00","Z")
        conn.execute(
          "UPDATE chat_history SET message=?, edited_at=? WHERE ticket_id=? AND tg_message_id=?",
          (new_text, edited_at, ticket_id, tg_message_id)
        )
        conn.commit(); conn.close()

        return jsonify({"success": True, "edited_on_client": ok})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

@app.post("/message_delete")
@login_required
def message_delete():
    try:
        user_id = int(request.form["user_id"])
        ticket_id = request.form["ticket_id"]
        tg_message_id = int(request.form["tg_message_id"])

        # узнаем, кто отправил (support/user)
        conn = get_db()
        conn.row_factory = sqlite3.Row
        row = conn.execute(
            "SELECT sender FROM chat_history WHERE ticket_id=? AND tg_message_id=?",
            (ticket_id, tg_message_id)
        ).fetchone()
        conn.close()

        deleted_on_client = False
        if row and row["sender"] == "support":
            try:
                url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/deleteMessage"
                payload = {"chat_id": user_id, "message_id": tg_message_id}
                r = requests.post(url, json=payload, timeout=15).json()
                deleted_on_client = bool(r.get("ok"))
            except Exception:
                pass

        # мягкое удаление в истории — ВСЕГДА
        conn = get_db()
        deleted_at = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
        conn.execute(
            "UPDATE chat_history SET deleted_at=? WHERE ticket_id=? AND tg_message_id=?",
            (deleted_at, ticket_id, tg_message_id)
        )
        conn.commit(); conn.close()

        return jsonify({"success": True, "deleted_on_client": deleted_on_client})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500

@app.route("/client_statuses")
@login_required
def get_client_statuses():
    try:
        conn = get_db()
        cur = conn.cursor()
        cur.execute("SELECT user_id, status FROM client_statuses")
        statuses = {row['user_id']: row['status'] for row in cur.fetchall()}
        conn.close()
        return jsonify(statuses)
    except Exception as e:
        print(f"❌ Ошибка в /client_statuses: {e}")
        return jsonify({})

@app.route("/client/<int:user_id>/status", methods=["POST"])
@login_required
def set_client_status(user_id):
    data = request.json
    status = data.get("client_status")  # Изменено с "status" на "client_status"
    try: 
        conn = get_db()
        conn.execute(
            "INSERT OR REPLACE INTO client_statuses (user_id, status, updated_at) VALUES (?, ?, ?)",
            (user_id, status, datetime.datetime.now().isoformat())
        )
        conn.commit()
        conn.close()
        return jsonify({"success": True})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})
        
@app.route("/tickets_list")
@login_required
def tickets_list():
    try:
        conn = get_db()
        cur = conn.cursor()
        cur.execute("""
            SELECT
                m.ticket_id,
                m.user_id,
                m.username as username,
                COALESCE(
                    (SELECT client_name
                     FROM messages
                     WHERE user_id = m.user_id
                       AND client_name IS NOT NULL AND client_name != ''
                     ORDER BY created_at DESC LIMIT 1),
                m.client_name) AS client_name,
                m.username,
                m.business,
                m.city,
                m.location_name,
                m.problem,
                m.created_at,
                t.status AS raw_status,
                t.resolved_by,
                t.resolved_at,
                m.created_date,
                m.created_time,
                t.channel_id,
                (SELECT channel_name FROM channels c WHERE c.id = t.channel_id) AS channel_name,
                -- связанная задача (если создана из этого диалога)
                (SELECT tl.task_id FROM task_links tl WHERE tl.ticket_id = m.ticket_id LIMIT 1) AS linked_task_id,

                -- кто последний писал (с учётом канала)
                (SELECT sender
                 FROM chat_history h
                 WHERE h.ticket_id = m.ticket_id AND (h.channel_id = t.channel_id OR h.channel_id IS NULL OR t.channel_id IS NULL)
                 ORDER BY
                   substr(timestamp,1,19) DESC,
                   COALESCE(tg_message_id, 0) DESC,
                   h.rowid DESC
                 LIMIT 1
                ) AS last_sender,

                -- количество сообщений клиента (для подсчёта непрочитанных)
                (
                  SELECT COUNT(*)
                  FROM chat_history h
                  WHERE h.ticket_id = m.ticket_id
                    AND (h.channel_id = t.channel_id OR h.channel_id IS NULL OR t.channel_id IS NULL)
                    AND LOWER(COALESCE(h.sender, '')) = 'user'
                ) AS user_msg_count,

                -- время последнего сообщения клиента (может пригодиться на фронтенде)
                (
                  SELECT MAX(timestamp)
                  FROM chat_history h
                  WHERE h.ticket_id = m.ticket_id
                    AND (h.channel_id = t.channel_id OR h.channel_id IS NULL OR t.channel_id IS NULL)
                    AND LOWER(COALESCE(h.sender, '')) = 'user'
                ) AS last_user_message_at,

                -- был ли ответ поддержки (с учётом канала)
                EXISTS (
                  SELECT 1 FROM chat_history h2
                  WHERE h2.ticket_id = m.ticket_id
                    AND (h2.channel_id = t.channel_id OR h2.channel_id IS NULL OR t.channel_id IS NULL)
                    AND h2.sender != 'user'   -- 👈 ключевой момент: любой не user считается ответом панели
                  LIMIT 1
                ) AS has_support_reply

            FROM messages m
            JOIN tickets t ON t.ticket_id = m.ticket_id
            WHERE 1=1
            GROUP BY m.ticket_id
            ORDER BY m.created_at DESC
        """)
        tickets = cur.fetchall()

        result = []
        for t in tickets:
            # человеческий статус
            if not t['has_support_reply']:
                status_human = "Новая"
            else:
                # Если последнее сообщение от клиента
                if t['last_sender'] and t['last_sender'].lower() == 'user':
                    status_human = "Ожидает реакции"
                # Если последнее сообщение от кого-либо из панели (оператора, администратора и т.д.)
                elif t['last_sender']:
                    status_human = "Ожидает клиента"
                # Если нет информации о последнем отправителе
                else:
                    status_human = "Неизвестно"

            # (опционально) имя первого ответившего администратора
            cur.execute("""
                SELECT message FROM chat_history
                WHERE ticket_id = ? AND channel_id = ? AND sender = 'support'
                ORDER BY timestamp ASC LIMIT 1
            """, (t['ticket_id'], t['channel_id']))
            first_reply = cur.fetchone()
            admin_name = "Поддержка"
            if first_reply:
                import re
                m = re.search(r"от поддержки \(([^)]+)\)", first_reply['message'] or "")
                if m:
                    admin_name = m.group(1)

            row = dict(t)
            row['avatar_url'] = url_for('avatar', user_id=t['user_id'])
            row['status'] = status_human
            row['responsible'] = admin_name
            result.append(row)

        conn.close()
        return jsonify(result)
    except Exception as e:
        print(f"❌ Ошибка в /tickets_list: {e}")
        return jsonify([]), 500

@app.route("/tickets/<ticket_id>")
@login_required
def get_ticket(ticket_id):
    conn = get_db()
    cur = conn.cursor()
    cur.execute("SELECT status, resolved_by, resolved_at FROM tickets WHERE ticket_id = ?", (ticket_id,))
    row = cur.fetchone()
    conn.close()
    if row:
        return jsonify(dict(row))
    return jsonify({})

@app.route("/tickets/<ticket_id>/category")
@login_required
def get_ticket_category(ticket_id):
    conn = get_db()
    cur = conn.cursor()
    cur.execute("SELECT category FROM messages WHERE ticket_id = ? LIMIT 1", (ticket_id,))
    row = cur.fetchone()
    conn.close()
    
    if row and row['category']:
        return jsonify({"category": row['category']})
    return jsonify({"category": ""})

@app.route("/clients")
@login_required
def clients_list():
    # фильтр по блэклисту: '1' (только в блэклисте), '0' (только не в блэклисте), '' или None (все)
    bl_filter = (request.args.get("blacklist") or "").strip()

    conn = get_db()
    cur = conn.cursor()

    # Базовый список клиентов
    cur.execute("""
        SELECT 
            m.user_id,
            m.username,
            (SELECT client_name FROM messages 
             WHERE user_id = m.user_id AND client_name IS NOT NULL AND client_name != ''
             ORDER BY created_at DESC LIMIT 1) as client_name,
            COUNT(*) as ticket_count,
            MIN(m.created_at) as first_contact,
            MAX(m.created_at) as last_contact
        FROM messages m
        GROUP BY m.user_id
        ORDER BY last_contact DESC
    """)
    clients = cur.fetchall()

    # Подтянем статусы блэклиста разом в словарь
    cur.execute("SELECT user_id, is_blacklisted, unblock_requested FROM client_blacklist")
    blmap = {r["user_id"]: (int(r["is_blacklisted"]), int(r["unblock_requested"])) for r in cur.fetchall()}

    clients_with_time = []
    for client in clients:
        user_id = client['user_id']

        # Расчёт времени (как у вас было)
        time_query = """
            SELECT
                SUM(
                    CASE 
                        WHEN t.status = 'resolved' AND t.resolved_at IS NOT NULL 
                             AND ch.first_response_time IS NOT NULL
                        THEN CAST(
                            (julianday(t.resolved_at)
                             - julianday(replace(substr(ch.first_response_time,1,19),'T',' ')))
                             * 24 * 60
                             AS INTEGER
                        )
                        ELSE 0
                    END
                ) as total_minutes
            FROM messages m
            JOIN tickets t ON m.ticket_id = t.ticket_id
            LEFT JOIN (
                SELECT ticket_id, MIN(timestamp) as first_response_time
                FROM chat_history 
                WHERE sender = 'support'
                GROUP BY ticket_id
            ) ch ON t.ticket_id = ch.ticket_id
            WHERE m.user_id = ?
        """
        cur.execute(time_query, (user_id,))
        time_result = cur.fetchone()
        total_minutes = time_result['total_minutes'] or 0

        client_dict = dict(client)
        client_dict['total_minutes'] = total_minutes
        client_dict['formatted_time'] = format_time_duration(total_minutes)

        # статусы блэклиста
        is_bl, unb = blmap.get(user_id, (0, 0))
        client_dict["blacklist"] = is_bl
        client_dict["unblock_requested"] = unb

        clients_with_time.append(client_dict)

    conn.close()

    # применим фильтр
    if bl_filter in ("0", "1"):
        clients_with_time = [c for c in clients_with_time if str(c["blacklist"]) == bl_filter]

    return render_template("clients.html", clients=clients_with_time, blacklist_filter=bl_filter)

# === Карточка клиента ===
@app.route("/client/<int:user_id>")
@login_required
def client_profile(user_id):
    conn = get_db()
    cur = conn.cursor()

    # Основная информация
    cur.execute("""
        SELECT user_id, username, client_name FROM messages WHERE user_id = ? LIMIT 1
    """, (user_id,))
    info = cur.fetchone()

    # Все заявки
    cur.execute("""
        SELECT 
            m.ticket_id,
            m.business,
            m.city,
            m.location_name,
            m.problem,
            m.created_at,
            t.status,
            t.resolved_by,
            CASE 
                WHEN t.resolved_at IS NOT NULL THEN datetime(t.resolved_at)
                ELSE NULL 
            END AS resolved_at,
            m.created_date,
            m.created_time,
            m.category,
            m.client_status
        FROM messages m
        LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
        WHERE m.user_id = ?
        ORDER BY m.created_at DESC
    """, (user_id,))
    tickets = cur.fetchall()  # ✅ tickets — всегда определён

    # Добавляем время активности
    ticket_list = []
    for t in tickets:
        row = dict(t)
        if row['status'] == 'resolved' and row['resolved_at'] and row['created_date'] and row['created_time']:
            try:
                start = dt.fromisoformat(f"{row['created_date']} {row['created_time']}")
                end = dt.fromisoformat(row['resolved_at'])
                row['duration_minutes'] = int((end - start).total_seconds() // 60)
            except Exception as e:
                print(f"Ошибка расчёта времени: {e}")
                row['duration_minutes'] = None
        else:
            row['duration_minutes'] = None
        ticket_list.append(row)

    # Статистика
    cur.execute("""
        SELECT 
            COUNT(*) as total,
            SUM(CASE WHEN t.status = 'resolved' THEN 1 ELSE 0 END) as resolved,
            SUM(CASE WHEN t.status != 'resolved' THEN 1 ELSE 0 END) as pending
        FROM messages m
        LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
        WHERE m.user_id = ?
    """, (user_id,))
    stats = cur.fetchone()

    # Оценки (если есть таблица feedbacks)
    try:
        cur.execute("""
            SELECT rating, timestamp FROM feedbacks WHERE user_id = ? ORDER BY timestamp DESC
        """, (user_id,))
        feedbacks = cur.fetchall()
    except:
        feedbacks = []

    # Получаем статус клиента из базы данных
    cur.execute("SELECT status FROM client_statuses WHERE user_id = ?", (user_id,))
    status_row = cur.fetchone()
    client_status = status_row['status'] if status_row else None
        # История username и телефоны
    cur.execute("SELECT username, seen_at FROM client_usernames WHERE user_id=? ORDER BY seen_at DESC", (user_id,))
    username_history = [dict(r) for r in cur.fetchall()]

    cur.execute("""
        SELECT id, phone, label, source, is_active, created_at, created_by
        FROM client_phones
        WHERE user_id=? ORDER BY source DESC, created_at DESC
    """, (user_id,))
    phones_all = [dict(r) for r in cur.fetchall()]

    phones_telegram = [p for p in phones_all if p['source'] == 'telegram' and p['is_active']]
    phones_manual   = [p for p in phones_all if p['source'] == 'manual' and p['is_active']]
        # История username
    cur.execute(
        "SELECT username, seen_at FROM client_usernames WHERE user_id=? ORDER BY seen_at DESC",
        (user_id,),
    )
    username_history = [dict(r) for r in cur.fetchall()]

    # Телефоны
    cur.execute("""
        SELECT id, phone, label, source, is_active, created_at, created_by
        FROM client_phones
        WHERE user_id=? ORDER BY source DESC, created_at DESC
    """, (user_id,))
    phones_all = [dict(r) for r in cur.fetchall()]

    phones_telegram = [p for p in phones_all if p["source"] == "telegram" and p["is_active"]]
    phones_manual   = [p for p in phones_all if p["source"] == "manual" and p["is_active"]]

    conn.close()

    settings = {"categories": ["Консультация", "Другое"]}
    if os.path.exists("./settings.json"):
        with open("./settings.json", "r", encoding="utf-8") as f:
            settings = json.load(f)

    return render_template(
        "client_profile.html",
        client=dict(info),
        tickets=ticket_list,
        stats=dict(stats),
        feedbacks=[dict(f) for f in feedbacks],
        datetime=dt,
        settings=settings,
        client_status=client_status,
        username_history=username_history,
        phones_telegram=phones_telegram,
        phones_manual=phones_manual
    )
       
# === API для дашборда с фильтрами ===
@app.route("/api/dashboard/data", methods=["POST"])
@login_required
def api_dashboard_data():
    try:
        data = request.get_json()
        start_date = data.get('startDate')
        end_date = data.get('endDate')
        restaurants = data.get('restaurants', [])
        
        # Если restaurants пустая строка, преобразуем в пустой список
        if restaurants == '':
            restaurants = []
        
        conn = get_db()
        cur = conn.cursor()
        
        # Базовый запрос для статистики
        query = """
            SELECT 
                COUNT(*) as total,
                SUM(CASE WHEN t.status = 'new' THEN 1 ELSE 0 END) as new,
                SUM(CASE WHEN t.status = 'in_progress' THEN 1 ELSE 0 END) as in_progress,
                SUM(CASE WHEN t.status = 'resolved' THEN 1 ELSE 0 END) as resolved
            FROM tickets t
            JOIN messages m ON t.ticket_id = m.ticket_id
            WHERE 1=1
        """
        
        params = []
        
        # Фильтр по дате
        if start_date and end_date:
            query += " AND DATE(m.created_at) BETWEEN ? AND ?"
            params.extend([start_date, end_date])
                
        # Фильтр по ресторанам
        if restaurants:
            if isinstance(restaurants, list) and restaurants:
                placeholders = ','.join(['?'] * len(restaurants))
                query += f" AND m.location_name IN ({placeholders})"
                params.extend(restaurants)
            elif isinstance(restaurants, str) and restaurants:
                query += " AND m.location_name = ?"
                params.append(restaurants)
                
        # Выполняем запрос для текущего периода
        cur.execute(query, params)
        current_result = cur.fetchone()
        
        # Безопасное преобразование результата в словарь
        current_stats = {}
        if current_result:
            current_stats = {
                'total': current_result['total'] or 0,
                'new': current_result['new'] or 0,
                'in_progress': current_result['in_progress'] or 0,
                'resolved': current_result['resolved'] or 0
            }
                
        # Запрос для предыдущего периода (месяц назад)
        prev_query = query
        prev_params = params.copy()
                
        if start_date and end_date:
            # Вычисляем предыдущий период такой же длительности
            start_dt = dt.strptime(start_date, '%Y-%m-%d')
            end_dt = dt.strptime(end_date, '%Y-%m-%d')
            period_days = (end_dt - start_dt).days
                    
            prev_start = (start_dt - timedelta(days=period_days + 1)).strftime('%Y-%m-%d')
            prev_end = (start_dt - timedelta(days=1)).strftime('%Y-%m-%d')
                    
            # Заменяем даты в параметрах
            if ' AND DATE(m.created_at) BETWEEN ? AND ?' in prev_query:
                date_index = prev_params.index(start_date)
                prev_params[date_index] = prev_start
                prev_params[date_index + 1] = prev_end
            
        cur.execute(prev_query, prev_params)
        previous_result = cur.fetchone()
        
        # Безопасное преобразование результата в словарь
        previous_stats = {}
        if previous_result:
            previous_stats = {
                'total': previous_result['total'] or 0,
                'new': previous_result['new'] or 0,
                'in_progress': previous_result['in_progress'] or 0,
                'resolved': previous_result['resolved'] or 0
            }

        # ВРЕМЕННАЯ СТАТИСТИКА - восстановленная логика
        time_query = """
            SELECT 
                SUM(
                    CASE 
                        WHEN t.status = 'resolved' AND t.resolved_at IS NOT NULL 
                        AND m.created_date IS NOT NULL AND m.created_time IS NOT NULL
                        THEN (
                            (julianday(t.resolved_at) - julianday(m.created_date || ' ' || m.created_time)) 
                            * 24 * 60  -- время в минутах
                        )
                        ELSE 0
                    END
                ) as total_minutes,
                COUNT(
                    CASE 
                        WHEN t.status = 'resolved' AND t.resolved_at IS NOT NULL 
                        THEN 1
                    END
                ) as resolved_count
            FROM tickets t
            JOIN messages m ON t.ticket_id = m.ticket_id
            WHERE 1=1
        """
        
        time_params = []
        
        # Фильтр по дате для временной статистики
        if start_date and end_date:
            time_query += " AND DATE(m.created_at) BETWEEN ? AND ?"
            time_params.extend([start_date, end_date])
            
        # Фильтр по ресторанам для временной статистики
        if restaurants:
            if isinstance(restaurants, list) and restaurants:
                placeholders = ','.join(['?'] * len(restaurants))
                time_query += f" AND m.location_name IN ({placeholders})"
                time_params.extend(restaurants)
            elif isinstance(restaurants, str) and restaurants:
                time_query += " AND m.location_name = ?"
                time_params.append(restaurants)
        
        # Выполняем запрос времени
        cur.execute(time_query, time_params)
        time_result = cur.fetchone()
        
        total_minutes = time_result['total_minutes'] or 0 if time_result else 0
        resolved_count = time_result['resolved_count'] or 0 if time_result else 0
        
        # Форматируем время для отображения
        avg_minutes = total_minutes / resolved_count if resolved_count > 0 else 0
        
        time_stats = {
            'total_minutes': total_minutes,
            'total_hours': total_minutes / 60,
            'resolved_count': resolved_count,
            'avg_minutes': avg_minutes,
            'formatted_total': format_time_duration(total_minutes),
            'formatted_avg': format_time_duration(avg_minutes) if resolved_count > 0 else "0 мин"
        }
        
        # СТАТИСТИКА ПО СОТРУДНИКАМ - восстановленная логика
        staff_time_query = """
            SELECT 
                t.resolved_by as staff_name,
                SUM(
                    CASE 
                        WHEN t.status = 'resolved' AND t.resolved_at IS NOT NULL 
                        AND m.created_date IS NOT NULL AND m.created_time IS NOT NULL
                        THEN (
                            (julianday(t.resolved_at) - julianday(m.created_date || ' ' || m.created_time)) 
                            * 24 * 60  -- время в минутах
                        )
                        ELSE 0
                    END
                ) as total_minutes,
                COUNT(
                    CASE 
                        WHEN t.status = 'resolved' AND t.resolved_at IS NOT NULL 
                        THEN 1
                    END
                ) as resolved_count,
                AVG(
                    CASE 
                        WHEN ch.first_response_time IS NOT NULL 
                        AND m.created_date IS NOT NULL AND m.created_time IS NOT NULL
                        THEN (
                            (julianday(ch.first_response_time) - julianday(m.created_date || ' ' || m.created_time)) 
                            * 24 * 60  -- время первого ответа в минутах
                        )
                    END
                ) as avg_first_response_minutes
            FROM tickets t
            JOIN messages m ON t.ticket_id = m.ticket_id
            LEFT JOIN (
                SELECT ticket_id, MIN(timestamp) as first_response_time
                FROM chat_history 
                WHERE sender = 'support'
                GROUP BY ticket_id
            ) ch ON t.ticket_id = ch.ticket_id
            WHERE t.resolved_by IS NOT NULL AND t.resolved_by != ''
        """
        
        staff_time_params = []
        
        if start_date and end_date:
            staff_time_query += " AND DATE(m.created_at) BETWEEN ? AND ?"
            staff_time_params.extend([start_date, end_date])
        
        if restaurants:
            if isinstance(restaurants, list) and restaurants:
                placeholders = ','.join(['?'] * len(restaurants))
                staff_time_query += f" AND m.location_name IN ({placeholders})"
                staff_time_params.extend(restaurants)
            elif isinstance(restaurants, str) and restaurants:
                staff_time_query += " AND m.location_name = ?"
                staff_time_params.append(restaurants)
        
        staff_time_query += " GROUP BY t.resolved_by"
        
        cur.execute(staff_time_query, staff_time_params)
        staff_time_results = cur.fetchall()
        
        staff_stats = []
        for row in staff_time_results:
            if row and row['staff_name'] and row['resolved_count'] > 0:
                staff_minutes = row['total_minutes'] or 0
                staff_resolved = row['resolved_count'] or 0
                staff_avg = staff_minutes / staff_resolved if staff_resolved > 0 else 0
                staff_avg_response = row['avg_first_response_minutes'] or 0
        
                staff_stats.append({
                    'name': row['staff_name'],
                    'total_minutes': staff_minutes,
                    'resolved_count': staff_resolved,
                    'avg_minutes': staff_avg,
                    'avg_response_minutes': staff_avg_response,
                    'formatted_total': format_time_duration(staff_minutes),
                    'formatted_avg': format_time_duration(staff_avg) if staff_resolved > 0 else "0 мин",
                    'formatted_avg_response': format_time_duration(staff_avg_response)
                })
                
        # Данные для графиков (остальная часть кода остается без изменений)
        charts_data = {}
                
        # Статусы заявок
        status_query = """
            SELECT t.status, COUNT(*) as count
            FROM tickets t
            JOIN messages m ON t.ticket_id = m.ticket_id
            WHERE 1=1
        """
                
        status_params = []
                
        if start_date and end_date:
            status_query += " AND DATE(m.created_at) BETWEEN ? AND ?"
            status_params.extend([start_date, end_date])
                
        if restaurants:
            if isinstance(restaurants, list) and restaurants:
                placeholders = ','.join(['?'] * len(restaurants))
                status_query += f" AND m.location_name IN ({placeholders})"
                status_params.extend(restaurants)
            elif isinstance(restaurants, str) and restaurants:
                status_query += " AND m.location_name = ?"
                status_params.append(restaurants)
                
        status_query += " GROUP BY t.status"
                
        cur.execute(status_query, status_params)
        status_rows = cur.fetchall()
        status_data = {row['status']: row['count'] for row in status_rows} if status_rows else {}
        charts_data['status'] = status_data
                
        # Категории обращений
        category_query = """
            SELECT category, COUNT(*) as count
            FROM messages
            WHERE 1=1
        """

        category_params = []
        if start_date and end_date:
            category_query += " AND DATE(created_at) BETWEEN ? AND ?"
            category_params.extend([start_date, end_date])
            
        if restaurants:
            if isinstance(restaurants, list) and restaurants:
                placeholders = ','.join(['?'] * len(restaurants))
                category_query += f" AND location_name IN ({placeholders})"
                category_params.extend(restaurants)
            elif isinstance(restaurants, str) and restaurants:
                category_query += " AND location_name = ?"
                category_params.append(restaurants)
            
        category_query += " GROUP BY category"
                
        cur.execute(category_query, category_params)
        category_rows = cur.fetchall()
        category_data = {row['category'] or 'без категории': row['count'] for row in category_rows} if category_rows else {}
        charts_data['category'] = category_data
                
        # По бизнесу
        business_query = """
            SELECT business, COUNT(*) as count
            FROM messages
            WHERE 1=1
        """
                
        business_params = []
                
        if start_date and end_date:
            business_query += " AND DATE(created_at) BETWEEN ? AND ?"
            business_params.extend([start_date, end_date])
                
        if restaurants:
            if isinstance(restaurants, list) and restaurants:
                placeholders = ','.join(['?'] * len(restaurants))
                business_query += f" AND location_name IN ({placeholders})"
                business_params.extend(restaurants)
            elif isinstance(restaurants, str) and restaurants:
                business_query += " AND location_name = ?"
                business_params.append(restaurants)
                
        business_query += " GROUP BY business"
                
        cur.execute(business_query, business_params)
        business_rows = cur.fetchall()
        business_data = {row['business'] or 'не указан': row['count'] for row in business_rows} if business_rows else {}
        charts_data['business'] = business_data

        # По сетям (Корпоративная сеть vs Партнёры-франчайзи)
        network_query = """
            SELECT 
                CASE 
                    WHEN m.location_type = 'Корпоративная сеть' AND m.business LIKE '%БлинБери%' THEN 'Корпоративная сеть (БлинБери)'
                    WHEN m.location_type = 'Корпоративная сеть' AND m.business LIKE '%СушиВесла%' THEN 'Корпоративная сеть (СушиВесла)'
                    WHEN m.location_type = 'Партнёры-франчайзи' AND m.business LIKE '%БлинБери%' THEN 'Партнёры-франчайзи (БлинБери)'
                    WHEN m.location_type = 'Партнёры-франчайзи' AND m.business LIKE '%СушиВесла%' THEN 'Партнёры-франчайзи (СушиВесла)'
                    WHEN m.location_type = 'Корпоративная сеть' THEN 'Корпоративная сеть (Другое)'
                    WHEN m.location_type = 'Партнёры-франчайзи' THEN 'Партнёры-франчайзи (Другое)'
                    ELSE 'Другое'
                END as network_type,
                COUNT(*) as count
            FROM messages m
            WHERE 1=1
        """

        network_params = []

        if start_date and end_date:
            network_query += " AND DATE(m.created_at) BETWEEN ? AND ?"
            network_params.extend([start_date, end_date])

        if restaurants:
            if isinstance(restaurants, list) and restaurants:
                placeholders = ','.join(['?'] * len(restaurants))
                network_query += f" AND m.location_name IN ({placeholders})"
                network_params.extend(restaurants)
            elif isinstance(restaurants, str) and restaurants:
                network_query += " AND m.location_name = ?"
                network_params.append(restaurants)

        network_query += " GROUP BY network_type"

        cur.execute(network_query, network_params)
        network_rows = cur.fetchall()
        network_data = {row['network_type']: row['count'] for row in network_rows} if network_rows else {}
        charts_data['network'] = network_data

        # По городам
        city_query = """
            SELECT city, COUNT(*) as count
            FROM messages
            WHERE 1=1
        """
                
        city_params = []
                
        if start_date and end_date:
            city_query += " AND DATE(created_at) BETWEEN ? AND ?"
            city_params.extend([start_date, end_date])
                
        if restaurants:
            if isinstance(restaurants, list) and restaurants:
                placeholders = ','.join(['?'] * len(restaurants))
                city_query += f" AND location_name IN ({placeholders})"
                city_params.extend(restaurants)
            elif isinstance(restaurants, str) and restaurants:
                city_query += " AND location_name = ?"
                city_params.append(restaurants)
                
        city_query += " GROUP BY city"
                
        cur.execute(city_query, city_params)
        city_rows = cur.fetchall()
        city_data = {row['city'] or 'не указан': row['count'] for row in city_rows} if city_rows else {}
        charts_data['city'] = city_data
                
        # По ресторанам (топ-10)
        restaurant_query = """
            SELECT location_name, COUNT(*) as count
            FROM messages
            WHERE 1=1
        """
                
        restaurant_params = []
                
        if start_date and end_date:
            restaurant_query += " AND DATE(created_at) BETWEEN ? AND ?"
            restaurant_params.extend([start_date, end_date])
                
        if restaurants:
            if isinstance(restaurants, list) and restaurants:
                placeholders = ','.join(['?'] * len(restaurants))
                restaurant_query += f" AND location_name IN ({placeholders})"
                restaurant_params.extend(restaurants)
            elif isinstance(restaurants, str) and restaurants:
                restaurant_query += " AND location_name = ?"
                restaurant_params.append(restaurants)
                
        restaurant_query += " GROUP BY location_name ORDER BY count DESC LIMIT 10"
                
        cur.execute(restaurant_query, restaurant_params)
        restaurant_rows = cur.fetchall()
        restaurant_data = {row['location_name'] or 'не указан': row['count'] for row in restaurant_rows} if restaurant_rows else {}
        charts_data['restaurant'] = restaurant_data
                
        conn.close()
                
        return jsonify({
            'stats': {
                'current': current_stats,
                'previous': previous_stats
            },
            'charts': charts_data,
            'time_stats': time_stats,
            'staff_time_stats': staff_stats
        })
        
    except Exception as e:
        print(f"Ошибка в API дашборда: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

# === API: уведомления (колокольчик) ===
@app.get('/api/notifications')
@login_required_api
def api_notifications():
    user = session.get('user') or session.get('username') or session.get('user_email') or ''
    since = request.args.get('since')
    params = [user,]
    where_since = ''
    if since:
        try:
            since_id = int(since)
            where_since = 'AND id > ?'
            params.append(since_id)
        except:
            pass
    conn = get_db()
    rows = conn.execute(f"""
        SELECT id, text, url, created_at
          FROM notifications
         WHERE (user = ? OR user = 'all') AND is_read = 0 {where_since}
         ORDER BY id DESC
         LIMIT 100
    """, params).fetchall()
    conn.close()
    return jsonify([dict(r) for r in rows])

@app.post('/api/notifications/mark_read')
@login_required_api
def api_notifications_mark():
    data = request.get_json(force=True, silent=True) or {}
    ids = data.get('ids') or []
    if not ids:
        return jsonify({'ok': True})
    try:
        conn = get_db()
        q = 'UPDATE notifications SET is_read=1 WHERE id IN ({})'.format(','.join(['?']*len(ids)))
        conn.execute(q, ids)
        conn.commit(); conn.close()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500

# === Аналитика по клиентам (ДЕТАЛЬНАЯ ГРУППИРОВКА) ===
@app.route("/analytics/clients")
@login_required
def analytics_clients():
    conn = get_db()
    cur = conn.cursor()
    try:
        # Берём разрез по клиентам, чтобы строки были «про клиента», а не агрегаты без user_id
        cur.execute("""
            SELECT 
                m.user_id,
                COALESCE(
                    (SELECT client_name
                     FROM messages
                     WHERE user_id = m.user_id
                       AND client_name IS NOT NULL AND client_name != ''
                     ORDER BY created_at DESC LIMIT 1),
                    m.client_name
                ) AS client_name,
                m.username,
                m.business,
                m.location_type,
                m.city,
                m.location_name,
                m.category,
                t.status,
                COUNT(*) AS tickets
            FROM messages m
            JOIN tickets t ON m.ticket_id = t.ticket_id
            GROUP BY 
                m.user_id,
                client_name,
                m.username,
                m.business,
                m.location_type,
                m.city,
                m.location_name,
                m.category,
                t.status
            ORDER BY tickets DESC
        """)
        rows = cur.fetchall()

        # Разбиваем мультикатегории "кат1, кат2, кат3" на отдельные строки
        expanded_rows = []
        for row in rows:
            cats = (row['category'] or '').split(',') if row['category'] else ['без категории']
            for cat in cats:
                new_row = dict(row)
                new_row['category'] = cat.strip()
                expanded_rows.append(new_row)

        rows = expanded_rows

        # Итоговое количество «заявок» в таблице (сумма tickets по всем строкам)
        total_entries = sum(r['tickets'] for r in rows)

        # Данные для фильтров (как и раньше)
        cur.execute("""
            SELECT DISTINCT 
                business,
                location_type,
                city,
                location_name,
                category
            FROM messages
            WHERE business IS NOT NULL
        """)
        filter_data_rows = cur.fetchall()
        businesses = sorted({r['business'] for r in filter_data_rows if r['business']})
        location_types = sorted({r['location_type'] for r in filter_data_rows if r['location_type']})
        cities = sorted({r['city'] for r in filter_data_rows if r['city']})
        locations = sorted({r['location_name'] for r in filter_data_rows if r['location_name']})
        categories = sorted({r['category'] for r in filter_data_rows if r['category']})

        cur.execute("SELECT DISTINCT status FROM tickets WHERE status IS NOT NULL")
        statuses = [r['status'] for r in cur.fetchall()]

        conn.close()

        return render_template(
            "analytics_clients.html",
            clients=rows,
            total_tickets=total_entries,
            businesses=businesses,
            location_types=location_types,
            cities=cities,
            locations=locations,
            categories=categories,
            statuses=statuses
        )

    except Exception as e:
        print(f"❌ Ошибка в аналитике клиентов: {e}")
        import traceback; traceback.print_exc()
        conn.close()
        return render_template(
            "analytics_clients.html",
            clients=[],
            total_tickets=0,
            businesses=[],
            location_types=[],
            cities=[],
            locations=[],
            categories=[],
            statuses=[]
        )

@app.route("/analytics")
@login_required
def analytics():
    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
        SELECT business, location_type, city, location_name, category, status, COUNT(*) as cnt
        FROM messages JOIN tickets USING(ticket_id)
        GROUP BY business, location_type, city, location_name, category, status
    """)
    rows = cur.fetchall()
    print(f"DEBUG: Found {len(rows)} analytics rows")
    print(f"DEBUG: Sample row: {rows[0] if rows else 'None'}")
    
    # Рассчитываем общее количество заявок
    total_tickets = 0
    for row in rows:
        total_tickets += row['cnt']
    
    # Получаем уникальные значения для фильтров
    cur.execute("SELECT DISTINCT business FROM messages WHERE business IS NOT NULL")
    businesses = [row['business'] for row in cur.fetchall()]
    
    cur.execute("SELECT DISTINCT location_type FROM messages WHERE location_type IS NOT NULL")
    location_types = [row['location_type'] for row in cur.fetchall()]
    
    cur.execute("SELECT DISTINCT city FROM messages WHERE city IS NOT NULL")
    cities = [row['city'] for row in cur.fetchall()]
    
    cur.execute("SELECT DISTINCT location_name FROM messages WHERE location_name IS NOT NULL")
    locations = [row['location_name'] for row in cur.fetchall()]
    
    cur.execute("SELECT DISTINCT category FROM messages WHERE category IS NOT NULL")
    categories = [row['category'] for row in cur.fetchall()]
    
    cur.execute("SELECT DISTINCT status FROM tickets WHERE status IS NOT NULL")
    statuses = [row['status'] for row in cur.fetchall()]
    
    conn.close()
    
    return render_template("analytics.html", 
                         stats=rows,
                         total_tickets=total_tickets,  # ✅ Добавляем общее количество заявок
                         businesses=businesses,
                         location_types=location_types,
                         cities=cities,
                         locations=locations,
                         categories=categories,
                         statuses=statuses)
                         

# === Детали клиента для аналитики ===
@app.route("/analytics/clients/<int:user_id>/details")
@login_required
def client_analytics_details(user_id):
    try:
        conn = get_db()
        cur = conn.cursor()
        
        # Основная информация о клиенте
        cur.execute("""
            SELECT username, client_name, MAX(created_at) as last_contact
            FROM messages 
            WHERE user_id = ?
            GROUP BY user_id
        """, (user_id,))
        client_info = cur.fetchone()
        
        # Общее количество заявок
        cur.execute("SELECT COUNT(*) as total_tickets FROM messages WHERE user_id = ?", (user_id,))
        total_tickets = cur.fetchone()['total_tickets']
        
        # Самая частая категория
        cur.execute("""
            SELECT category, COUNT(*) as cnt 
            FROM messages 
            WHERE user_id = ? AND category IS NOT NULL
            GROUP BY category 
            ORDER BY cnt DESC 
            LIMIT 1
        """, (user_id,))
        category_row = cur.fetchone()
        most_common_category = category_row['category'] if category_row else None
        
        # Самая частая локация
        cur.execute("""
            SELECT location_name, COUNT(*) as cnt 
            FROM messages 
            WHERE user_id = ? AND location_name IS NOT NULL
            GROUP BY location_name 
            ORDER BY cnt DESC 
            LIMIT 1
        """, (user_id,))
        location_row = cur.fetchone()
        most_common_location = location_row['location_name'] if location_row else None
        
        conn.close()
        
        return jsonify({
            'username': client_info['username'] if client_info else None,
            'client_name': client_info['client_name'] if client_info else None,
            'last_contact': client_info['last_contact'] if client_info else None,
            'total_tickets': total_tickets,
            'most_common_category': most_common_category,
            'most_common_location': most_common_location
        })
        
    except Exception as e:
        print(f"Ошибка получения деталей клиента: {e}")
        return jsonify({"error": str(e)}), 500

# === Экспорт аналитики клиентов ===
@app.route("/analytics/clients/export", methods=["POST"])
@login_required
def export_analytics_clients():
    try:
        data = request.json
        format_type = data.get('format', 'xlsx')
        filters = data.get('filters', {})
        export_filtered = data.get('exportFiltered', False)
        
        conn = get_db()
        cur = conn.cursor()
        
        # Базовый запрос для аналитики клиентов
        query = """
            SELECT 
                m.business,
                m.location_type,
                m.city,
                m.location_name,
                m.category,
                t.status,
                COUNT(DISTINCT m.user_id) as cnt
            FROM messages m
            JOIN tickets t ON m.ticket_id = t.ticket_id
            WHERE 1=1
        """
        
        # Применяем фильтры
        where_conditions = []
        params = []
        
        if export_filtered and filters:
            # Фильтры по бизнесу
            if filters.get('business') and filters['business']:
                placeholders = ','.join(['?'] * len(filters['business']))
                where_conditions.append(f"m.business IN ({placeholders})")
                params.extend(filters['business'])
            
            # Фильтры по типу локации
            if filters.get('location_type') and filters['location_type']:
                placeholders = ','.join(['?'] * len(filters['location_type']))
                where_conditions.append(f"m.location_type IN ({placeholders})")
                params.extend(filters['location_type'])
            
            # Фильтры по городу
            if filters.get('city') and filters['city']:
                placeholders = ','.join(['?'] * len(filters['city']))
                where_conditions.append(f"m.city IN ({placeholders})")
                params.extend(filters['city'])
            
            # Фильтры по локации
            if filters.get('location') and filters['location']:
                placeholders = ','.join(['?'] * len(filters['location']))
                where_conditions.append(f"m.location_name IN ({placeholders})")
                params.extend(filters['location'])
            
            # Фильтры по категории
            if filters.get('category') and filters['category']:
                placeholders = ','.join(['?'] * len(filters['category']))
                where_conditions.append(f"m.category IN ({placeholders})")
                params.extend(filters['category'])
            
            # Фильтры по статусу
            if filters.get('status') and filters['status']:
                placeholders = ','.join(['?'] * len(filters['status']))
                where_conditions.append(f"t.status IN ({placeholders})")
                params.extend(filters['status'])
            
            # Фильтр по количеству заявок
            if filters.get('ticketCount'):
                if filters['ticketCount'] == 'custom' and filters.get('customTicketValue'):
                    operator = filters.get('customTicketOperator', '>=')
                    where_conditions.append(f"COUNT(DISTINCT m.user_id) {operator} ?")
                    params.append(int(filters['customTicketValue']))
                else:
                    min_count = int(filters['ticketCount'])
                    where_conditions.append("COUNT(DISTINCT m.user_id) >= ?")
                    params.append(min_count)
        
        if where_conditions:
            query += " AND " + " AND ".join(where_conditions)
        
        query += " GROUP BY m.business, m.location_type, m.city, m.location_name, m.category, t.status"
        
        cur.execute(query, params)
        rows = cur.fetchall()
        
        conn.close()
        
        # Формируем данные для экспорта
        export_data = []
        headers = ['Бизнес', 'Тип локации', 'Город', 'Локация', 'Категория', 'Статус', 'Количество клиентов']
        
        for row in rows:
            export_data.append([
                row['business'] or '',
                row['location_type'] or '',
                row['city'] or '',
                row['location_name'] or '',
                row['category'] or '',
                row['status'] or '',
                row['cnt']
            ])
        
        # Экспорт в выбранном формате
        if format_type == 'csv':
            import csv
            import io
            
            output = io.StringIO()
            writer = csv.writer(output)
            
            # Заголовки
            writer.writerow(headers)
            
            # Данные
            writer.writerows(export_data)
            
            response = Response(output.getvalue(), mimetype='text/csv')
            response.headers['Content-Disposition'] = 'attachment; filename=clients_analytics_export.csv'
            
        else:  # xlsx используя openpyxl напрямую
            from openpyxl import Workbook
            from openpyxl.styles import Font
            from io import BytesIO
            
            output = BytesIO()
            
            wb = Workbook()
            ws = wb.active
            ws.title = "Аналитика клиентов"
            
            # Заголовки с жирным шрифтом
            for col_idx, header in enumerate(headers, 1):
                cell = ws.cell(row=1, column=col_idx, value=header)
                cell.font = Font(bold=True)
            
            # Данные
            for row_idx, row_data in enumerate(export_data, 2):
                for col_idx, value in enumerate(row_data, 1):
                    ws.cell(row=row_idx, column=col_idx, value=value)
            
            # Авто-ширина колонок
            for column in ws.columns:
                max_length = 0
                column_letter = column[0].column_letter
                for cell in column:
                    try:
                        if len(str(cell.value)) > max_length:
                            max_length = len(str(cell.value))
                    except:
                        pass
                adjusted_width = min(max_length + 2, 50)
                ws.column_dimensions[column_letter].width = adjusted_width
            
            wb.save(output)
            output.seek(0)
            
            response = Response(output.read(), mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
            response.headers['Content-Disposition'] = 'attachment; filename=clients_analytics_export.xlsx'
        
        return response
        
    except Exception as e:
        print(f"Ошибка при экспорте аналитики клиентов: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route("/analytics/export", methods=["POST"])
@login_required
def export_analytics():
    try:
        data = request.json
        format_type = data.get("format", "xlsx")
        filters = data.get("filters", {})
        export_filtered = data.get("exportFiltered", False)
        
        conn = get_db()
        cur = conn.cursor()
        
        # Базовый запрос
        query = """
            SELECT 
                business, 
                location_type, 
                city, 
                location_name, 
                category, 
                status, 
                COUNT(*) as cnt
            FROM messages 
            JOIN tickets USING(ticket_id)
        """
        
        # Применяем фильтры если нужно
        where_conditions = []
        params = []
        
        if export_filtered and filters:
            if filters.get('business') and filters['business']:
                where_conditions.append("business IN ({})".format(','.join(['?'] * len(filters['business']))))
                params.extend(filters['business'])
            
            if filters.get('location_type') and filters['location_type']:
                where_conditions.append("location_type IN ({})".format(','.join(['?'] * len(filters['location_type']))))
                params.extend(filters['location_type'])
            
            if filters.get('city') and filters['city']:
                where_conditions.append("city IN ({})".format(','.join(['?'] * len(filters['city']))))
                params.extend(filters['city'])
            
            if filters.get('location') and filters['location']:
                where_conditions.append("location_name IN ({})".format(','.join(['?'] * len(filters['location']))))
                params.extend(filters['location'])
            
            if filters.get('category') and filters['category']:
                where_conditions.append("category IN ({})".format(','.join(['?'] * len(filters['category']))))
                params.extend(filters['category'])
            
            if filters.get('status') and filters['status']:
                where_conditions.append("status IN ({})".format(','.join(['?'] * len(filters['status']))))
                params.extend(filters['status'])
        
        if where_conditions:
            query += " WHERE " + " AND ".join(where_conditions)
        
        query += " GROUP BY business, location_type, city, location_name, category, status"
        
        cur.execute(query, params)
        rows = cur.fetchall()
        
        # Формируем данные для экспорта
        export_data = []
        headers = ['Бизнес', 'Тип локации', 'Город', 'Локация', 'Категория', 'Статус', 'Количество']
        
        for row in rows:
            export_data.append([
                row['business'] or '',
                row['location_type'] or '',
                row['city'] or '',
                row['location_name'] or '',
                row['category'] or '',
                row['status'] or '',
                row['cnt']
            ])
        
        conn.close()
        
        # Экспорт в выбранном формате
        if format_type == 'csv':
            import csv
            import io
            
            output = io.StringIO()
            writer = csv.writer(output)
            
            # Заголовки
            writer.writerow(headers)
            
            # Данные
            writer.writerows(export_data)
            
            response = Response(output.getvalue(), mimetype='text/csv')
            response.headers['Content-Disposition'] = 'attachment; filename=analytics_export.csv'
            
        else:  # xlsx используя openpyxl напрямую
            from openpyxl import Workbook
            from openpyxl.styles import Font
            from io import BytesIO
            
            output = BytesIO()
            
            wb = Workbook()
            ws = wb.active
            ws.title = "Аналитика"
            
            # Заголовки с жирным шрифтом
            for col_idx, header in enumerate(headers, 1):
                cell = ws.cell(row=1, column=col_idx, value=header)
                cell.font = Font(bold=True)
            
            # Данные
            for row_idx, row_data in enumerate(export_data, 2):
                for col_idx, value in enumerate(row_data, 1):
                    ws.cell(row=row_idx, column=col_idx, value=value)
            
            # Авто-ширина колонок
            for column in ws.columns:
                max_length = 0
                column_letter = column[0].column_letter
                for cell in column:
                    try:
                        if len(str(cell.value)) > max_length:
                            max_length = len(str(cell.value))
                    except:
                        pass
                adjusted_width = min(max_length + 2, 50)
                ws.column_dimensions[column_letter].width = adjusted_width
            
            wb.save(output)
            output.seek(0)
            
            response = Response(output.read(), mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
            response.headers['Content-Disposition'] = 'attachment; filename=analytics_export.xlsx'
        
        return response
        
    except Exception as e:
        print(f"Ошибка при экспорте аналитики: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500

@app.route("/users_list")
@login_required
def users_page():
    return render_template("users_list.html")

@app.route("/dashboard")
@login_required
def dashboard():
    conn = get_db()
    cur = conn.cursor()

    # Получаем список всех ресторанов для фильтра
    cur.execute("SELECT DISTINCT location_name FROM messages WHERE location_name IS NOT NULL ORDER BY location_name")
    restaurants = [row['location_name'] for row in cur.fetchall()]

    # Остальной код остается без изменений...
    cur.execute("SELECT status, COUNT(*) AS cnt FROM tickets GROUP BY status")
    status_rows = cur.fetchall()
    status_data = {row['status'] if row['status'] is not None else 'неизвестно': row['cnt'] for row in status_rows}

    cur.execute("SELECT category, COUNT(*) AS cnt FROM messages GROUP BY category")
    category_rows = cur.fetchall()
    category_data = {row['category'] if row['category'] is not None else 'без категории': row['cnt'] for row in category_rows}

    cur.execute("SELECT business, COUNT(*) AS cnt FROM messages GROUP BY business")
    business_rows = cur.fetchall()
    business_data = {row['business'] if row['business'] is not None else 'не указан': row['cnt'] for row in business_rows}

    cur.execute("SELECT city, COUNT(*) AS cnt FROM messages GROUP BY city")
    city_rows = cur.fetchall()
    city_data = {row['city'] if row['city'] is not None else 'не указан': row['cnt'] for row in city_rows}

    conn.close()
    
    return render_template("dashboard.html", 
                         status_data=status_data, 
                         category_data=category_data,
                         business_data=business_data,
                         city_data=city_data,
                         restaurants=restaurants)  # Добавляем restaurants

# --- helpers for tasks people/notifications ---
def _get_people(conn, task_id: int, role: str) -> list[str]:
    rows = conn.execute(
        "SELECT identity FROM task_people WHERE task_id=? AND role=? ORDER BY identity",
        (task_id, role)
    ).fetchall()
    return [r["identity"] for r in rows]

def _add_people(conn, task_id: int, role: str, identities: list[str]) -> None:
    conn.execute("DELETE FROM task_people WHERE task_id=? AND role=?", (task_id, role))
    for ident in (identities or []):
        if ident:
            conn.execute(
                "INSERT OR IGNORE INTO task_people(task_id, role, identity) VALUES(?,?,?)",
                (task_id, role, ident.strip())
            )

def _notify_many(users: list[str], text: str, url: str | None = None) -> None:
    if not users:
        return
    with get_db() as nconn:
        for u in users:
            if u:
                nconn.execute(
                    "INSERT INTO notifications(user, text, url) VALUES(?,?,?)",
                    (u, text, url)
                )
        nconn.commit()

# === КАНАЛЫ (боты) ===
import requests

@app.route('/tasks')
@login_required
def tasks_page():
    import sqlite3
    users = []
    # 1) пытаемся достать пользователей из основной БД (если там есть таблица users)
    try:
        with get_db() as conn:
            conn.row_factory = sqlite3.Row
            users = conn.execute("SELECT id, username, role FROM users ORDER BY username").fetchall()
    except Exception:
        users = []

    # 2) если список пуст — пробуем users-ХРАНИЛИЩЕ (get_users_db)
    if not users:
        try:
            with get_users_db() as uconn:
                uconn.row_factory = sqlite3.Row
                users = uconn.execute("SELECT id, username, role FROM users ORDER BY username").fetchall()
        except Exception:
            users = []

    return render_template('tasks.html', users=users)

@app.route("/api/blacklist/check", methods=["GET"])
def api_blacklist_check():
    """
    Внешние интеграции (бот) могут дергать этот эндпоинт перед обработкой сообщения.
    Возвращает:
      - is_blacklisted: 0/1
      - unblock_requested: 0/1
      - message: текст для клиента (если блэклист)
    """
    user_id = (request.args.get("user_id") or "").strip()
    if not user_id:
        return jsonify({"ok": False, "error": "user_id required"}), 400

    row = None
    try:
        with get_db() as conn:
            row = conn.execute("""
                SELECT is_blacklisted, unblock_requested
                FROM client_blacklist WHERE user_id=?
            """, (user_id,)).fetchone()
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500

    is_bl = int(row["is_blacklisted"]) if row else 0
    unb_req = int(row["unblock_requested"]) if row else 0
    msg = "Ваш аккаунт временно ограничен. Если вы считаете это ошибкой, нажмите «Запросить разблокировку»." if is_bl else ""
    return jsonify({"ok": True, "is_blacklisted": is_bl, "unblock_requested": unb_req, "message": msg})

@app.route("/api/blacklist/add", methods=["POST"])
@login_required
def api_blacklist_add():
    data = request.get_json(force=True) if request.is_json else request.form
    user_id = (data.get("user_id") or "").strip()
    reason = (data.get("reason") or "").strip()
    operator = session.get("username") or "operator"

    if not user_id:
        return jsonify({"ok": False, "error": "user_id required"}), 400

    now_iso = dt.now().isoformat(timespec="seconds") + "Z"
    try:
        with get_db() as conn:
            conn.execute("""
                INSERT INTO client_blacklist (user_id, is_blacklisted, reason, added_at, added_by, unblock_requested, unblock_requested_at)
                VALUES (?, 1, ?, ?, ?, 0, NULL)
                ON CONFLICT(user_id) DO UPDATE SET
                    is_blacklisted=excluded.is_blacklisted,
                    reason=excluded.reason,
                    added_at=excluded.added_at,
                    added_by=excluded.added_by,
                    unblock_requested=0,
                    unblock_requested_at=NULL
            """, (user_id, reason, now_iso, operator))
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500

@app.route("/api/blacklist/remove", methods=["POST"])
@login_required
def api_blacklist_remove():
    data = request.get_json(force=True) if request.is_json else request.form
    user_id = (data.get("user_id") or "").strip()

    if not user_id:
        return jsonify({"ok": False, "error": "user_id required"}), 400

    try:
        with get_db() as conn:
            conn.execute("""
                INSERT INTO client_blacklist (user_id, is_blacklisted, reason, added_at, added_by, unblock_requested, unblock_requested_at)
                VALUES (?, 0, '', NULL, NULL, 0, NULL)
                ON CONFLICT(user_id) DO UPDATE SET
                    is_blacklisted=0,
                    reason='',
                    added_at=NULL,
                    added_by=NULL,
                    unblock_requested=0,
                    unblock_requested_at=NULL
            """, (user_id,))
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500

@app.route("/api/blacklist/request-unblock", methods=["POST"])
def api_blacklist_request_unblock():
    """
    Вызывается, когда клиент нажимает «Запросить разблокировку».
    НЕ требует login_required, т.к. может прилетать извне (например, из бота).
    """
    data = request.get_json(force=True) if request.is_json else request.form
    user_id = (data.get("user_id") or "").strip()
    reason = (data.get("reason") or "").strip()

    if not user_id:
        return jsonify({"ok": False, "error": "user_id required"}), 400

    from datetime import datetime, timezone
    now_iso = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00","Z")
    try:
        with get_db() as conn:
            conn.execute("""
                INSERT INTO client_blacklist (user_id, is_blacklisted, unblock_requested, unblock_requested_at)
                VALUES (?, 1, 1, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    unblock_requested=1,
                    unblock_requested_at=excluded.unblock_requested_at
            """, (user_id, now_iso))
        # Создадим задачу оператору
        create_unblock_request_task(user_id, reason)
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500

@app.route('/api/tasks', methods=['GET'])
@login_required_api
def api_tasks_list():
    q = request.args
    cond, args = [], []

    # --- фильтры (как раньше) ---
    if q.get('num'):
        val = q['num']
        if '_' in val:
            src, num = val.split('_',1)
            cond.append("source = ? AND seq = ?")
            args += [src, int(num)]
        else:
            cond.append("seq = ?")
            args.append(int(val))
    if q.get('title'):
        cond.append("title LIKE ?"); args.append('%'+q['title']+'%')
    if q.get('assignee'):
        cond.append("assignee LIKE ?"); args.append('%'+q['assignee']+'%')
    if q.get('tag'):
        cond.append("tag LIKE ?"); args.append('%'+q['tag']+'%')
    if q.get('status'):
        cond.append("status = ?"); args.append(q['status'])
    # периоды
    if q.get('created_from'):
        cond.append("created_at >= ?"); args.append(q['created_from'] + " 00:00:00")
    if q.get('created_to'):
        cond.append("created_at <= ?"); args.append(q['created_to'] + " 23:59:59")
    if q.get('due_from'):
        cond.append("(due_at IS NOT NULL AND due_at >= ?)"); args.append(q['due_from'] + " 00:00:00")
    if q.get('due_to'):
        cond.append("(due_at IS NOT NULL AND due_at <= ?)"); args.append(q['due_to'] + " 23:59:59")
    if q.get('closed_from'):
        cond.append("(closed_at IS NOT NULL AND closed_at >= ?)"); args.append(q['closed_from'] + " 00:00:00")
    if q.get('closed_to'):
        cond.append("(closed_at IS NOT NULL AND closed_at <= ?)"); args.append(q['closed_to'] + " 23:59:59")

    where = ("WHERE " + " AND ".join(cond)) if cond else ""

    # --- сортировка (белый список) ---
    sort_by = (q.get('sort_by') or 'last_activity_at')
    sort_dir = (q.get('sort_dir') or 'desc').lower()
    allowed_cols = {'seq','source','title','assignee','due_at','last_activity_at','created_at','closed_at','tag','status'}
    if sort_by not in allowed_cols: sort_by = 'last_activity_at'
    if sort_dir not in {'asc','desc'}: sort_dir = 'desc'
    order_sql = f"ORDER BY {sort_by} {sort_dir.upper()}"

    # --- пагинация ---
    try:
        page = max(1, int(q.get('page') or 1))
    except: page = 1
    try:
        page_size = min(200, max(5, int(q.get('page_size') or 20)))
    except: page_size = 20
    offset = (page - 1) * page_size

    with get_db() as conn:
        conn.row_factory = sqlite3.Row
        total = conn.execute(f"SELECT COUNT(*) AS c FROM tasks {where}", args).fetchone()['c']
        rows = conn.execute(f"""
            SELECT * FROM tasks {where}
            {order_sql}
            LIMIT ? OFFSET ?
        """, (*args, page_size, offset)).fetchall()

        items = [{
            'id': r['id'],
            'display_no': (f"{r['source']}_{r['seq']}" if r['source'] else str(r['seq'])),
            'title': r['title'],
            'assignee': r['assignee'],
            'due_at': r['due_at'],
            'last_activity_at': r['last_activity_at'],
            'created_at': r['created_at'],
            'closed_at': r['closed_at'],
            'tag': r['tag'],
            'status': r['status'],
        } for r in rows]

        return jsonify({
            'items': items,
            'total': total,
            'page': page,
            'page_size': page_size
        })

@app.route('/api/tasks/export', methods=['GET'])
@login_required_api
def api_tasks_export():
    # Берём всё без пагинации — повторяем условия из api_tasks_list
    q = request.args
    cond, args = [], []
    def add_like(field,key):
        if q.get(key):
            cond.append(f"{field} LIKE ?"); args.append('%'+q[key]+'%')
    if q.get('num'):
        val = q['num']
        if '_' in val:
            src, num = val.split('_',1)
            cond.append("source = ? AND seq = ?"); args += [src, int(num)]
        else:
            cond.append("seq = ?"); args.append(int(val))
    add_like('title','title')
    add_like('assignee','assignee')
    add_like('tag','tag')
    if q.get('status'):
        cond.append("status = ?"); args.append(q['status'])
    if q.get('created_from'):
        cond.append("created_at >= ?"); args.append(q['created_from'] + " 00:00:00")
    if q.get('created_to'):
        cond.append("created_at <= ?"); args.append(q['created_to'] + " 23:59:59")
    if q.get('due_from'):
        cond.append("(due_at IS NOT NULL AND due_at >= ?)"); args.append(q['due_from'] + " 00:00:00")
    if q.get('due_to'):
        cond.append("(due_at IS NOT NULL AND due_at <= ?)"); args.append(q['due_to'] + " 23:59:59")
    if q.get('closed_from'):
        cond.append("(closed_at IS NOT NULL AND closed_at >= ?)"); args.append(q['closed_from'] + " 00:00:00")
    if q.get('closed_to'):
        cond.append("(closed_at IS NOT NULL AND closed_at <= ?)"); args.append(q['closed_to'] + " 23:59:59")
    where = ("WHERE " + " AND ".join(cond)) if cond else ""

    sort_by = (q.get('sort_by') or 'last_activity_at')
    sort_dir = (q.get('sort_dir') or 'desc').lower()
    allowed_cols = {'seq','source','title','assignee','due_at','last_activity_at','created_at','closed_at','tag','status'}
    if sort_by not in allowed_cols: sort_by = 'last_activity_at'
    if sort_dir not in {'asc','desc'}: sort_dir = 'desc'
    order_sql = f"ORDER BY {sort_by} {sort_dir.upper()}"

    with get_db() as conn:
        conn.row_factory = sqlite3.Row
        rows = conn.execute(f"SELECT * FROM tasks {where} {order_sql}").fetchall()

    from io import StringIO
    import csv
    si = StringIO()
    cw = csv.writer(si, delimiter=';')
    cw.writerow(['№','Название','Исполнитель','Крайний срок','Последняя активность','Создано','Закрыто','Тег','Статус'])
    for r in rows:
        disp = (f"{r['source']}_{r['seq']}" if r['source'] else str(r['seq']))
        cw.writerow([disp, r['title'] or '', r['assignee'] or '', r['due_at'] or '', r['last_activity_at'] or '', r['created_at'] or '', r['closed_at'] or '', r['tag'] or '', r['status'] or ''])
    out = si.getvalue().encode('utf-8-sig')
    return Response(out, headers={
        'Content-Disposition': 'attachment; filename="tasks_export.csv"',
        'Content-Type': 'text/csv; charset=utf-8'
    })

@app.route('/api/tasks', methods=['POST'])
@login_required_api
def api_tasks_save():
    f = request.form
    files = request.files.getlist('files')

    id_ = (f.get('id') or '').strip()
    title = (f.get('title') or '').strip()
    body_html = f.get('body_html') or ''
    # авто-значения, если не передали
    current_user = session.get('user_email') or session.get('username') or ''
    creator = (f.get('creator') or '').strip() or current_user
    assignee = (f.get('assignee') or '').strip() or current_user

    co = [s.strip() for s in (f.get('co') or '').split(',') if s.strip()]
    watchers = [s.strip() for s in (f.get('watchers') or '').split(',') if s.strip()]
    tag = (f.get('tag') or '').strip()
    due_at = (f.get('due_at') or '').strip() or None
    status = (f.get('status') or 'Новая').strip() or 'Новая'

    # файлы до 250 МБ
    attachments = []
    if files:
        base = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "attachments"))
        os.makedirs(base, exist_ok=True)
        for fl in files:
            if not fl.filename: continue
            size = getattr(fl, 'content_length', None)
            if size and size > 250 * 1024 * 1024:
                return jsonify({'error':'Файл превышает 250 МБ'}), 400
            safe = secure_filename(fl.filename)
            path = os.path.join(base, safe)
            fl.save(path)
            attachments.append(safe)
        if attachments:
            body_html += "<div class='mt-2'><b>Файлы:</b> " + ", ".join(attachments) + "</div>"

    with get_db() as conn:
        conn.row_factory = sqlite3.Row
        if id_:
            # UPDATE
            conn.execute("""
                UPDATE tasks SET title=?, body_html=?, creator=?, assignee=?, tag=?, status=?, due_at=?,
                    last_activity_at = datetime('now'),
                    closed_at = CASE WHEN ?='Завершена' THEN COALESCE(closed_at, datetime('now')) ELSE NULL END
                WHERE id=?
            """, (title, body_html, creator, assignee, tag, status, due_at, status, id_))
            _add_people(conn, id_, 'co', co)
            _add_people(conn, id_, 'watcher', watchers)
            conn.execute("INSERT INTO task_history(task_id,text) VALUES(?,?)", (id_, f"Задача изменена ({status})"))
            _touch_activity(conn, id_)
            targets = set([assignee] + co + watchers); targets.discard('')
            _notify_many(list(targets), f"Обновление задачи «{title or 'без названия'}»", url=url_for('tasks_page'))
            return jsonify({'ok': True, 'id': id_})
        else:
            # INSERT
            seq = _next_task_seq(conn)
            source = None
            conn.execute("""
                INSERT INTO tasks(seq, source, title, body_html, creator, assignee, tag, status, due_at)
                VALUES(?,?,?,?,?,?,?,?,?)
            """, (seq, source, title, body_html, creator, assignee, tag, status, due_at))
            task_id = conn.execute("SELECT last_insert_rowid() AS id").fetchone()["id"]
            _add_people(conn, task_id, 'co', co)
            _add_people(conn, task_id, 'watcher', watchers)
            conn.execute("INSERT INTO task_history(task_id,text) VALUES(?,?)", (task_id, "Задача создана"))
            _touch_activity(conn, task_id)
            targets = set([assignee] + co + watchers); targets.discard('')
            _notify_many(list(targets), f"Новая задача «{title or 'без названия'}»", url=url_for('tasks_page'))
            return jsonify({'ok': True, 'id': task_id})

@app.route('/api/tasks/<int:task_id>', methods=['GET'])
@login_required_api
def api_tasks_get(task_id):
    with get_db() as conn:
        conn.row_factory = sqlite3.Row
        t = conn.execute("SELECT * FROM tasks WHERE id=?", (task_id,)).fetchone()
        if not t:
            return jsonify({'error': 'not found'}), 404

        comments = conn.execute("""
            SELECT author, html, created_at
            FROM task_comments
            WHERE task_id=?
            ORDER BY created_at ASC
        """, (task_id,)).fetchall()

        history = conn.execute("""
            SELECT at, text
            FROM task_history
            WHERE task_id=?
            ORDER BY at DESC
        """, (task_id,)).fetchall()

        return jsonify({
            'id': t['id'],
            'display_no': _display_no(t['source'], t['seq']),
            'title': t['title'],
            'body_html': t['body_html'],
            'creator': t['creator'],
            'assignee': t['assignee'],
            'tag': t['tag'],
            'status': t['status'],
            'due_at': t['due_at'],
            'created_at': t['created_at'],
            'closed_at': t['closed_at'],
            'last_activity_at': t['last_activity_at'],
            'co': _get_people(conn, task_id, 'co'),
            'watchers': _get_people(conn, task_id, 'watcher'),
            'comments': [{'author': c['author'], 'html': c['html'], 'created_at': c['created_at']} for c in comments],
            'history':  [{'at': h['at'], 'text': h['text']} for h in history],
        })

@app.route('/api/tasks/<int:task_id>', methods=['DELETE'])
@login_required_api
def api_tasks_delete(task_id):
    with get_db() as conn:
        conn.execute("DELETE FROM tasks WHERE id=?", (task_id,))
    return jsonify({'ok': True})

@app.route('/api/tasks/from_dialog', methods=['POST'])
@login_required_api
def api_task_from_dialog():
    data = request.get_json(force=True, silent=True) or {}
    problem = (data.get('problem') or '').strip()
    location = (data.get('location') or '').strip()
    title = (data.get('title') or '').strip() or (problem[:60] if problem else "Задача из диалога")

    current_user = session.get('user_email') or session.get('username') or session.get('user') or ''
    creator = current_user
    assignee = current_user

    body_html = ""
    if problem:
        body_html += f"<p><b>Проблема:</b> {problem}</p>"
    if location:
        body_html += f"<p><b>Локация:</b> {location}</p>"

    with get_db() as conn:
        seq = _next_task_seq(conn)
        conn.execute("""
            INSERT INTO tasks(seq, source, title, body_html, creator, assignee, status)
            VALUES(?,?,?,?,?,?, 'Новая')
        """, (seq, 'DL', title, body_html, creator, assignee))
        task_id = conn.execute("SELECT last_insert_rowid() AS id").fetchone()["id"]

        # связь задача ↔ диалог (если передан ticket_id)
        if data.get('ticket_id'):
            conn.execute("INSERT OR IGNORE INTO task_links(task_id, ticket_id) VALUES(?,?)",
                         (task_id, str(data['ticket_id'])))

        conn.execute("INSERT INTO task_history(task_id,text) VALUES(?,?)", (task_id, "Создано из диалога"))
        _touch_activity(conn, task_id)

    # уведомим участника
    try:
        _notify_many([assignee] if assignee else [], f"Новая задача «{title}» (из диалога)", url=url_for('tasks_page'))
    except:
        pass

    return jsonify({'ok': True, 'id': task_id})

@app.route('/api/tasks/<int:task_id>/comments', methods=['POST'])
@login_required_api
def api_task_add_comment(task_id):
    author = session.get('user_email') or session.get('user') or session.get('username') or 'user'
    html = (request.form.get('html') or '').strip()
    if not html:
        return jsonify({'ok': False, 'error': 'empty'}), 400
    with get_db() as conn:
        conn.execute("INSERT INTO task_comments(task_id, author, html) VALUES(?,?,?)", (task_id, author, html))
        row = conn.execute("SELECT id, author, html, created_at FROM task_comments WHERE rowid = last_insert_rowid()").fetchone()
        conn.execute("INSERT INTO task_history(task_id,text) VALUES(?,?)", (task_id, f"Комментарий от {author}"))
        _touch_activity(conn, task_id)
    return jsonify({'ok': True, 'item': dict(row)})

@app.route('/api/tickets/<ticket_id>/active', methods=['POST'])
@login_required_api
def api_ticket_set_active(ticket_id):
    user = session.get('user_email') or session.get('user') or session.get('username') or ''
    if not user:
        return jsonify({'ok': False}), 400
    with get_db() as conn:
        conn.execute("""
            INSERT INTO ticket_active(ticket_id, user, last_seen) VALUES(?,?,datetime('now'))
            ON CONFLICT(ticket_id) DO UPDATE SET user=excluded.user, last_seen=datetime('now')
        """, (ticket_id, user))
    return jsonify({'ok': True})

@app.route('/api/notifications/unread_count')
@login_required_api
def api_notify_count():
    user = session.get('user_email') or 'all'
    with get_db() as conn:
        row = conn.execute(
            "SELECT COUNT(*) AS c FROM notifications WHERE (user=? OR user='all') AND is_read=0",
            (user,)
        ).fetchone()
        return jsonify({'count': row['c'] if row else 0})
    with get_db() as conn:
        row = conn.execute("SELECT COUNT(*) AS c FROM notifications WHERE (user=? OR user='all') AND is_read=0", (user,)).fetchone()
        return jsonify({'count': row['c'] if row else 0})

@app.route('/api/notifications')
@login_required_api
def api_notify_list():
    user = session.get('user_email') or 'all'
    with get_db() as conn:
        conn.row_factory = sqlite3.Row
        rows = conn.execute("""
            SELECT id, text, url, created_at
            FROM notifications
            WHERE user=? OR user='all'
            ORDER BY created_at DESC
            LIMIT 100
        """, (user,)).fetchall()
        items = [{'id': r['id'], 'text': r['text'], 'url': r['url'], 'created_at': r['created_at']} for r in rows]
        return jsonify({'items': items})
    with get_db() as conn:
        conn.row_factory = sqlite3.Row
        rows = conn.execute("""
            SELECT id, text, url, created_at FROM notifications
            WHERE user=? OR user='all'
            ORDER BY created_at DESC
            LIMIT 100
        """, (user,)).fetchall()
        items = [{'id':r['id'],'text':r['text'],'url':r['url'],'created_at':r['created_at']} for r in rows]
        return jsonify({'items': items})

@app.route('/api/notifications/<int:nid>/read', methods=['POST'])
@login_required_api
def api_notify_read(nid):
    user = session.get('user_email') or 'all'
    with get_db() as conn:
        conn.execute(
            "UPDATE notifications SET is_read=1 WHERE id=? AND (user=? OR user='all')",
            (nid, user)
        )
    return jsonify({'ok': True})
    with get_db() as conn:
        conn.execute("UPDATE notifications SET is_read=1 WHERE id=? AND (user=? OR user='all')", (nid, user))
    return jsonify({'ok': True})

@app.route("/channels", methods=["GET"])
@login_required
def channels_page():
    conn = get_db()
    rows = conn.execute("SELECT * FROM channels ORDER BY id DESC").fetchall()
    conn.close()
    return render_template("channels.html", channels=rows)

@app.route("/api/channels", methods=["GET"])
@login_required
def api_channels_list():
    conn = get_db()
    rows = conn.execute("SELECT * FROM channels ORDER BY id DESC").fetchall()
    conn.close()
    return jsonify([dict(r) for r in rows])

@app.route("/api/channels", methods=["POST"])
@login_required
def api_channels_create():
    data = request.get_json(force=True)
    token        = (data.get("token") or "").strip()
    channel_name = (data.get("channel_name") or "").strip()
    questions_cfg= data.get("questions_cfg") or {}
    max_questions= int(data.get("max_questions") or 0)
    is_active    = 1 if data.get("is_active", True) else 0

    if not token or not channel_name:
        return jsonify({"success": False, "error": "token и channel_name обязательны"}), 400

    # Валидация токена — запрашиваем getMe
    bot_name = None
    try:
        r = requests.get(f"https://api.telegram.org/bot{token}/getMe", timeout=10)
        j = r.json()
        if not j.get("ok"):
            return jsonify({"success": False, "error": "Токен невалиден"}), 400
        bot_name = j["result"]["username"]
    except Exception as e:
        return jsonify({"success": False, "error": f"Ошибка проверки токена: {e}"}), 400

    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
        INSERT INTO channels(token, bot_name, channel_name, questions_cfg, max_questions, is_active)
        VALUES (:token, :bot_name, :channel_name, :questions_cfg, :max_questions, :is_active)
    """, {
        "token": token,
        "bot_name": bot_name,
        "channel_name": channel_name,
        "questions_cfg": json.dumps(questions_cfg, ensure_ascii=False),
        "max_questions": max_questions,
        "is_active": is_active
    })
    conn.commit()
    conn.close()
    return jsonify({"success": True})

@app.route("/api/channels/<int:channel_id>", methods=["PATCH"])
@login_required
def api_channels_update(channel_id):
    data = request.get_json(force=True)
    fields, params = [], {"id": channel_id}
    for k in ("channel_name", "questions_cfg", "max_questions", "is_active"):
        if k in data:
            fields.append(f"{k} = :{k}")
            params[k] = json.dumps(data[k], ensure_ascii=False) if k == "questions_cfg" else data[k]
    if not fields:
        return jsonify({"success": False, "error": "Нет полей для обновления"}), 400

    conn = get_db()
    conn.execute(f"UPDATE channels SET {', '.join(fields)} WHERE id = :id", params)
    conn.commit()
    conn.close()
    return jsonify({"success": True})

@app.route("/api/channels/<int:channel_id>", methods=["DELETE"])
@login_required
def api_channels_delete(channel_id):
    conn = get_db()
    # Проверка привязок
    exists = conn.execute("SELECT 1 FROM tickets WHERE channel_id = ?", (channel_id,)).fetchone()
    if exists:
        conn.close()
        return jsonify({"success": False, "error": "Есть связанные заявки — удаление запрещено"}), 400
    conn.execute("DELETE FROM channels WHERE id = ?", (channel_id,))
    conn.commit()
    conn.close()
    return jsonify({"success": True})

@app.route("/settings")
@login_required
def settings_page():
    settings = load_settings()
    locations = load_locations()
    city_names = sorted({
        city
        for brand in locations.values()
        for partner_types in (brand or {}).values()
        for city in (partner_types or {}).keys()
    })
    return render_template(
        "settings.html",
        settings=settings,
        locations=locations,
        cities=city_names,
        parameter_types=PARAMETER_TYPES,
    )

@app.route("/settings", methods=["POST"])
@login_required
def update_settings():
    try:
        data = request.json
        
        # Обновляем настройки
        if "auto_close_hours" in data or "categories" in data or "client_statuses" in data:
            settings = load_settings()
            
            if "auto_close_hours" in data:
                settings["auto_close_hours"] = data["auto_close_hours"]
            
            if "categories" in data:
                settings["categories"] = [cat for cat in data["categories"] if cat.strip()]
            
            if "client_statuses" in data:
                settings["client_statuses"] = [status for status in data["client_statuses"] if status.strip()]
            
            save_settings(settings)
        
        # Обновляем локации
        if "locations" in data:
            save_locations(data["locations"])
        
        return jsonify({"success": True})
    
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

def _fetch_parameters_grouped(conn):
    rows = conn.execute(
        "SELECT id, param_type, value FROM settings_parameters ORDER BY param_type, value COLLATE NOCASE"
    ).fetchall()
    grouped = {key: [] for key in PARAMETER_TYPES.keys()}
    for row in rows:
        slug = row["param_type"]
        if slug in grouped:
            grouped[slug].append({"id": row["id"], "value": row["value"]})
    return grouped

@app.route("/api/settings/parameters", methods=["GET"])
@login_required
def api_get_parameters():
    conn = get_db()
    try:
        data = _fetch_parameters_grouped(conn)
        return jsonify(data)
    finally:
        conn.close()

@app.route("/api/settings/parameters", methods=["POST"])
@login_required
def api_create_parameter():
    payload = request.json or {}
    param_type = (payload.get("param_type") or "").strip()
    value = (payload.get("value") or "").strip()

    if param_type not in PARAMETER_TYPES:
        return jsonify({"success": False, "error": "Неизвестный тип параметра"}), 400

    if not value:
        return jsonify({"success": False, "error": "Значение не может быть пустым"}), 400

    conn = get_db()
    try:
        try:
            cur = conn.execute(
                "INSERT INTO settings_parameters (param_type, value) VALUES (?, ?)",
                (param_type, value),
            )
            new_id = cur.lastrowid
            conn.commit()
        except sqlite3.IntegrityError:
            return (
                jsonify({"success": False, "error": "Такое значение уже существует"}),
                409,
            )

        data = _fetch_parameters_grouped(conn)
        return jsonify({"success": True, "id": new_id, "data": data})
    finally:
        conn.close()

@app.route("/api/settings/parameters/<int:param_id>", methods=["PATCH"])
@login_required
def api_update_parameter(param_id):
    payload = request.json or {}
    value = (payload.get("value") or "").strip()

    if not value:
        return jsonify({"success": False, "error": "Значение не может быть пустым"}), 400

    conn = get_db()
    try:
        try:
            cur = conn.execute(
                "UPDATE settings_parameters SET value = ? WHERE id = ?",
                (value, param_id),
            )
            if cur.rowcount == 0:
                return jsonify({"success": False, "error": "Параметр не найден"}), 404
            conn.commit()
        except sqlite3.IntegrityError:
            return (
                jsonify({"success": False, "error": "Такое значение уже существует"}),
                409,
            )

        data = _fetch_parameters_grouped(conn)
        return jsonify({"success": True, "data": data})
    finally:
        conn.close()


@app.route("/api/settings/parameters/<int:param_id>", methods=["DELETE"])
@login_required
def api_delete_parameter(param_id):
    conn = get_db()
    try:
        cur = conn.execute("DELETE FROM settings_parameters WHERE id = ?", (param_id,))
        if cur.rowcount == 0:
            return jsonify({"success": False, "error": "Параметр не найден"}), 404
        conn.commit()
        data = _fetch_parameters_grouped(conn)
        return jsonify({"success": True, "data": data})
    finally:
        conn.close()

# === API для управления пользователями ===
@app.route("/users")
@login_required
def get_users():
    try:
        with get_users_db() as conn:
            users = conn.execute("SELECT id, username, role FROM users").fetchall()
        return jsonify([dict(u) for u in users])
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route("/users", methods=["POST"])
@login_required
def add_user():
    try:
        data = request.json
        username = data.get("username", "").strip()
        password = data.get("password", "").strip()
        role = data.get("role", "user").strip()

        if not username or not password:
            return jsonify({"success": False, "error": "Имя пользователя и пароль не могут быть пустыми"})

        with get_users_db() as conn:
            # Проверяем, существует ли пользователь
            existing = conn.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
            if existing:
                return jsonify({"success": False, "error": "Пользователь уже существует"})

            # Хэшируем пароль и сохраняем
            hashed_password = generate_password_hash(password)
            conn.execute(
                "INSERT INTO users (username, password, role) VALUES (?, ?, ?)",
                (username, hashed_password, role)
            )
            conn.commit()
        
        return jsonify({"success": True})
    
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

@app.route("/users/<int:user_id>", methods=["DELETE"])
@login_required
def delete_user(user_id):
    try:
        # Защита от удаления самого себя или администратора
        if user_id == 1:  # ID администратора
            return jsonify({"success": False, "error": "Нельзя удалить администратора"})
        
        with get_users_db() as conn:
            # Проверяем, существует ли пользователь
            user = conn.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
            if not user:
                return jsonify({"success": False, "error": "Пользователь не найден"})
            
            # Удаляем пользователя
            conn.execute("DELETE FROM users WHERE id = ?", (user_id,))
            conn.commit()
        
        return jsonify({"success": True})
    
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})
            
@app.route("/api/channels/<int:channel_id>", methods=["GET"])
@login_required
def api_channels_get(channel_id):
    conn = get_db()
    row = conn.execute("SELECT * FROM channels WHERE id = ?", (channel_id,)).fetchone()
    conn.close()
    if not row:
        return jsonify({"success": False, "error": "Канал не найден"}), 404
    d = dict(row)
    # Попробуем распарсить JSON, но вернём как есть — фронт сам приведёт
    return jsonify({"success": True, "channel": d})

@app.route("/stats_data")
@login_required
def stats_data():
    conn = get_db()
    total     = conn.execute("SELECT COUNT(*) FROM tickets").fetchone()[0]
    resolved  = conn.execute("SELECT COUNT(*) FROM tickets WHERE status = 'resolved'").fetchone()[0]
    pending   = total - resolved
    by_channel= conn.execute("""
        SELECT c.channel_name AS name, COUNT(*) as total
        FROM tickets t
        LEFT JOIN channels c ON c.id = t.channel_id
        GROUP BY c.channel_name
        ORDER BY total DESC
    """).fetchall()
    conn.close()
    return jsonify({
        "total": total, 
        "resolved": resolved, 
        "pending": pending,
        "by_channel": [dict(r) for r in by_channel]
    })

# === история сообщений (совместимо со всеми Flask) ===
@app.route('/history', methods=['GET'])
@login_required
def history():
    ticket_id = request.args.get("ticket_id", type=str)
    if not ticket_id:
        return jsonify({"success": False, "error": "ticket_id обязателен"}), 400

    # channel_id опционален; фильтруем по нему только если он валидный (>0)
    ch_raw = request.args.get("channel_id", default=None)
    channel_id = None
    try:
        if ch_raw not in (None, "", "null", "undefined"):
            ch_val = int(ch_raw)
            if ch_val > 0:
                channel_id = ch_val
    except Exception:
        channel_id = None

    try:
        conn = get_db()
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()

        # Тащим все строки по тикету; если channel_id валиден — добавляем фильтр
        if channel_id is not None:
            cur.execute("""
                SELECT sender, message, timestamp, message_type, attachment,
                       tg_message_id, reply_to_tg_id, channel_id,
                       edited_at, deleted_at
                  FROM chat_history
                 WHERE ticket_id = ? AND channel_id = ?
                 ORDER BY
                  substr(timestamp,1,19) ASC,
                  COALESCE(tg_message_id, 0) ASC,
                  rowid ASC
            """, (ticket_id, channel_id))
        else:
            cur.execute("""
                SELECT sender, message, timestamp, message_type, attachment,
                       tg_message_id, reply_to_tg_id, channel_id,
                       edited_at, deleted_at
                  FROM chat_history
                 WHERE ticket_id = ?
                 ORDER BY
                  substr(timestamp,1,19) ASC,
                  COALESCE(tg_message_id, 0) ASC,
                  rowid ASC
            """, (ticket_id,))
        rows = cur.fetchall()
    finally:
        try:
            conn.close()
        except:
            pass

    # Построим карту (channel_id, tg_message_id) -> текст для превью
    by_tg = {}
    for r in rows:
        mid = r["tg_message_id"]
        if not mid:
            continue
        # текст для превью: сообщение или подпись к медиа; если пусто — ставить метку
        base = (r["message"] or "").strip()
        if not base:
            # можно подставить короткую метку по типу медиа
            mt = (r["message_type"] or "").lower()
            if mt and mt != "text":
                base = f"[{mt}]"
        # обрежем превью до 140 символов
        by_tg[(r["channel_id"], mid)] = (base[:140] + "…") if len(base) > 140 else base

    # Формируем ответ + reply_preview
    out = []
    for r in rows:
        preview = None
        if r["reply_to_tg_id"]:
            key_exact = (r["channel_id"], r["reply_to_tg_id"])
            preview = by_tg.get(key_exact)
            if preview is None:
                # на всякий случай попробуем без учёта channel_id
                for (ch, mid), txt in by_tg.items():
                    if mid == r["reply_to_tg_id"]:
                        preview = txt
                        break

        out.append({
            "sender":         r["sender"],
            "message":        r["message"],
            "timestamp":      r["timestamp"],
            "message_type":   r["message_type"],
            "attachment":     r["attachment"],
            "tg_message_id":  r["tg_message_id"],
            "reply_to_tg_id": r["reply_to_tg_id"],
            "reply_preview":  preview,
            "edited_at":      r["edited_at"],
            "deleted_at":     r["deleted_at"],
        })

    return jsonify({"success": True, "messages": out})

@app.get("/api/history")
@login_required
def api_history():
    ticket_id = request.args.get("ticket_id")
    channel_id = request.args.get("channel_id", type=int)
    if not ticket_id or channel_id is None:
        return jsonify({"success": False, "error": "ticket_id и channel_id обязательны"}), 400

    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
        SELECT sender, message, timestamp, message_type, attachment,
               tg_message_id, reply_to_tg_id, edited_at, deleted_at
        FROM chat_history
        WHERE ticket_id = ? AND channel_id = ?
        ORDER BY
          substr(timestamp,1,19) ASC,
          COALESCE(tg_message_id, 0) ASC,
          rowid ASC
    """, (ticket_id, channel_id))
    rows = cur.fetchall()
    conn.close()

    # строим словарь для быстрых превью исходных сообщений
    by_tg = {r["tg_message_id"]: (r["message"] or "") for r in rows if r["tg_message_id"]}

    out = []
    for r in rows:
        preview = None
        if r["reply_to_tg_id"]:
            # короткое превью исходного текста
            src = by_tg.get(r["reply_to_tg_id"])
            if src:
                preview = src[:200]  # обрежем на всякий случай
        out.append({
            "sender":         r["sender"],
            "message":        r["message"],
            "timestamp":      r["timestamp"],
            "message_type":   r["message_type"],
            "attachment":     r["attachment"],
            "tg_message_id":  r["tg_message_id"],
            "reply_to_tg_id": r["reply_to_tg_id"],
            "reply_preview":  preview,
            "edited_at":      r["edited_at"],
            "deleted_at":     r["deleted_at"],
        })

    return jsonify({"success": True, "messages": out})
    
@app.route('/reply', methods=['POST'])
@login_required
def reply():
    """
    Ответ оператора текстом.
      • лимит переоткрытий до 3 раз;
      • переоткрытие закрытого тикета;
      • уведомление клиента о переоткрытии;
      • запись в chat_history с channel_id и reply_to_tg_id;
      • возврат флага reopened для фронта.
    """
    try:
        data = request.get_json(force=True)
        user_id   = int(data['user_id'])
        ticket_id = str(data.get('ticket_id') or '')
        if not ticket_id:
            return jsonify(success=False, error="ticket_id is required"), 400

        admin    = (data.get('admin') or 'Поддержка').strip()
        text     = _fix_surrogates((data.get('text') or '').strip())
        reply_to = data.get('reply_to_tg_id')  # может быть None
        if not text:
            return jsonify(success=False, error='Пустой текст')

        # 0) если тикет был закрыт — переоткрываем (с лимитом: не более 3 раз)
        reopened = reopen_ticket_if_needed(ticket_id)  # True | "LIMIT_EXCEEDED" | False

        if reopened == "LIMIT_EXCEEDED":
            return jsonify(success=False, error="Лимит переоткрытий исчерпан (3 раза)."), 409

        # 1) шлём клиенту (reply_to — если был выбран пузырь)
        ok, info = send_telegram_message(
            chat_id=user_id,
            text=f"От: {admin}\n\n{text}",
            parse_mode='HTML',
            reply_to_message_id=int(reply_to) if reply_to else None,
            allow_sending_without_reply=True
        )
        if not ok:
            # аккуратно вытащим retry_after из текста ошибки, если он есть
            import re
            retry_after = None
            m = re.search(r"retry after\s+(\d+)", str(info), re.IGNORECASE)
            if m:
                retry_after = int(m.group(1))
            resp_payload = {"success": False, "error": f"Не удалось отправить: {info}"}
            if retry_after is not None:
                resp_payload["retry_after"] = retry_after
                return jsonify(resp_payload), 429  # явный сигнал фронту
            return jsonify(resp_payload), 502

        tg_msg_id = info.get('message_id') if isinstance(info, dict) else None

        # 1.1) если тикет переоткрыт — отдельное уведомление клиенту
        if reopened is True:
            send_telegram_message(
                chat_id=user_id,
                text="🔄 Ваша заявка переоткрыта. Можете продолжать диалог.",
                parse_mode='HTML'
            )

        # 2) сохраняем запись в chat_history (включая reply_to и channel_id)
        from datetime import datetime, timezone
        now_utc = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace('+00:00', 'Z')

        with get_db() as conn:
            cur = conn.cursor()
            row = cur.execute(
                "SELECT channel_id FROM tickets WHERE ticket_id = ?",
                (ticket_id,)
            ).fetchone()
            channel_id = row['channel_id'] if row else None

            cur.execute("""
                INSERT INTO chat_history (
                    user_id, ticket_id, sender, message, timestamp, message_type,
                    attachment, tg_message_id, reply_to_tg_id, channel_id
                ) VALUES (?, ?, ?, ?, ?, 'text', NULL, ?, ?, ?)
            """, (
                user_id,
                ticket_id,
                admin if admin else 'Поддержка',
                text,
                now_utc,
                tg_msg_id,
                reply_to,
                channel_id
            ))

            # 🟢 ОБНОВЛЕНИЕ СТАТУСА ТИКЕТА
            try:
                cur.execute("UPDATE tickets SET status = 'pending' WHERE ticket_id = ?", (ticket_id,))
                conn.commit()
                app.logger.info(f"🟢 Статус тикета {ticket_id} обновлён на 'pending'")
            except Exception as e:
                app.logger.warning(f"⚠️ Не удалось обновить статус тикета {ticket_id}: {e}")

        # 3) отдаём фронту message_id и факт переоткрытия
        return jsonify(
            success=True,
            message_type='text',
            attachment=None,
            tg_message_id=tg_msg_id,
            reopened=bool(reopened is True)
        )

    except Exception as e:
        app.logger.error(f"/reply failed: {e}")
        return jsonify(success=False, error=str(e)), 500

@app.post('/reply_file')
@login_required
def reply_file():
    try:
        user_id   = int(request.form.get('user_id', 0))
        ticket_id = (request.form.get('ticket_id') or '').strip()
        admin     = (request.form.get('admin') or 'Поддержка').strip()
        reply_to  = request.form.get('reply_to_tg_id')  # может быть None

        f = request.files.get('file')
        if not user_id or not ticket_id or not f:
            return jsonify(success=False, error='bad params'), 400

        from zoneinfo import ZoneInfo
        now_utc = dt.now(timezone.utc).replace(microsecond=0).isoformat().replace('+00:00', 'Z')

        # гарантируем счётчики и переоткрытие
        reopened = False
        with get_db() as conn:
            conn.row_factory = sqlite3.Row
            cols = {r["name"] for r in conn.execute("PRAGMA table_info(tickets)").fetchall()}
            if "reopen_count" not in cols:
                conn.execute("ALTER TABLE tickets ADD COLUMN reopen_count INTEGER DEFAULT 0")
            if "closed_count" not in cols:
                conn.execute("ALTER TABLE tickets ADD COLUMN closed_count INTEGER DEFAULT 0")
            if "work_time_total_sec" not in cols:
                conn.execute("ALTER TABLE tickets ADD COLUMN work_time_total_sec INTEGER DEFAULT 0")
            if "last_reopen_at" not in cols:
                conn.execute("ALTER TABLE tickets ADD COLUMN last_reopen_at TEXT")

            cur = conn.cursor()
            row = cur.execute(
                "SELECT status, COALESCE(reopen_count,0) AS rc FROM tickets WHERE ticket_id = ?",
                (ticket_id,)
            ).fetchone()
            if row and row["status"] == "resolved":
                if row["rc"] >= 3:
                    return jsonify(success=False, error="Лимит переоткрытий исчерпан (3/3)"), 409
                cur.execute("""
                    UPDATE tickets
                       SET status='pending',
                           resolved_by=NULL,
                           resolved_at=NULL,
                           reopen_count = COALESCE(reopen_count,0) + 1,
                           last_reopen_at = ?
                     WHERE ticket_id=?
                """, (now_utc, ticket_id))
                conn.commit()
                reopened = True

        if reopened:
            try:
                send_telegram_message(
                    chat_id=user_id,
                    text=f"⚠️ Обращение #{ticket_id} переоткрыто. Мы продолжим работу.",
                    parse_mode='HTML'
                )
            except Exception:
                pass

        # сохраняем файл
        os.makedirs(os.path.join(ATTACHMENTS_DIR, str(ticket_id)), exist_ok=True)
        fname = secure_filename(f.filename or 'file')
        save_path = os.path.join(ATTACHMENTS_DIR, str(ticket_id), fname)
        f.save(save_path)

        # выбираем метод Telegram по расширению
        ext = os.path.splitext(fname)[1].lower()
        method, files_key, message_type = 'sendDocument', 'document', 'document'
        if ext in {'.jpg', '.jpeg', '.png', '.webp'}:
            method, files_key, message_type = 'sendPhoto', 'photo', 'photo'
        elif ext == '.mp4':
            method, files_key, message_type = 'sendVideo', 'video', 'video'
        elif ext in {'.ogg', '.oga', '.opus'}:
            method, files_key, message_type = 'sendVoice', 'voice', 'voice'
        elif ext in {'.mp3', '.m4a', '.wav', '.aac', '.flac'}:
            method, files_key, message_type = 'sendAudio', 'audio', 'audio'
        elif ext == '.gif':
            method, files_key, message_type = 'sendAnimation', 'animation', 'animation'
        elif ext in {'.tgs', '.webm', '.webp'}:
            method, files_key, message_type = 'sendSticker', 'sticker', 'sticker'

        # шлём в Telegram
        url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/{method}"
        payload = {'chat_id': user_id}
        if method != 'sendSticker':
            payload['caption'] = f"От: {admin}"
            payload['parse_mode'] = 'HTML'
        if reply_to:
            payload['reply_to_message_id'] = int(reply_to)
            payload['allow_sending_without_reply'] = True

        with open(save_path, 'rb') as fh:
            resp = requests.post(url, data=payload, files={files_key: fh}, timeout=60)
        data = resp.json()
        if not data.get('ok'):
            return jsonify(success=False, error=data.get('description', 'Telegram error')), 502
        tg_msg_id = data['result']['message_id']

        # пишем в историю (с channel_id и reply_to)
        def _insert():
            with get_db() as conn:
                cur = conn.cursor()
                row = cur.execute("SELECT channel_id FROM tickets WHERE ticket_id = ?", (ticket_id,)).fetchone()
                channel_id = row['channel_id'] if row else None
                cur.execute("""
                    INSERT INTO chat_history (
                        user_id, ticket_id, sender, message, timestamp, message_type,
                        attachment, tg_message_id, reply_to_tg_id, channel_id
                    ) VALUES (?, ?, 'support', ?, ?, ?, ?, ?, ?, ?)
                """, (user_id, ticket_id, f"От: {admin}", now_utc, message_type, save_path, tg_msg_id, reply_to, channel_id))
        exec_with_retry(_insert)

        # ✅ обновляем статус тикета (финальное подтверждение)
        with get_db() as conn:
            cur = conn.cursor()
            cur.execute("UPDATE tickets SET status = 'pending' WHERE ticket_id = ?", (ticket_id,))
            conn.commit()
            app.logger.info(f"🟢 Статус тикета {ticket_id} обновлён на 'pending' (файл)")

        return jsonify(success=True, message_type=message_type, attachment=save_path,
                       tg_message_id=tg_msg_id, reopened=reopened)

    except Exception as e:
        app.logger.exception("reply_file failed")
        return jsonify(success=False, error=str(e)), 500

@app.route("/close_ticket", methods=["POST"])
@login_required
def close_ticket():
    from datetime import datetime as dt, timezone
    data = request.json
    user_id   = int(data["user_id"])
    ticket_id = data["ticket_id"]
    admin_name = (data.get("admin") or "Поддержка").strip()
    category   = data.get("category", "Без категории")

    try:
        # 1) перенос категории в messages
        conn = get_db()
        conn.execute("UPDATE messages SET category = ? WHERE ticket_id = ?", (category, ticket_id))

        # 2) гарантируем служебные колонки
        cols = {r["name"] for r in conn.execute("PRAGMA table_info(tickets)").fetchall()}
        if "reopen_count" not in cols:
            conn.execute("ALTER TABLE tickets ADD COLUMN reopen_count INTEGER DEFAULT 0")
        if "closed_count" not in cols:
            conn.execute("ALTER TABLE tickets ADD COLUMN closed_count INTEGER DEFAULT 0")
        if "work_time_total_sec" not in cols:
            conn.execute("ALTER TABLE tickets ADD COLUMN work_time_total_sec INTEGER DEFAULT 0")
        if "last_reopen_at" not in cols:
            conn.execute("ALTER TABLE tickets ADD COLUMN last_reopen_at TEXT")

        # 3) добиваем суммарное время: если есть last_reopen_at — прибавим дельту
        row = conn.execute("SELECT work_time_total_sec, last_reopen_at FROM tickets WHERE ticket_id=?",
                           (ticket_id,)).fetchone()
        total_sec = int((row["work_time_total_sec"] or 0) if row else 0)
        if row and row["last_reopen_at"]:
            try:
                dt_last = dt.fromisoformat(row["last_reopen_at"])
            except Exception:
                dt_last = None
            if dt_last:
                total_sec += int((dt.now() - dt_last).total_seconds())

        # 4) помечаем закрытым, увеличиваем closed_count, обнуляем last_reopen_at, фиксируем total_sec
        now_iso = dt.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
        conn.execute("""
            UPDATE tickets
               SET status='resolved',
                   resolved_at=?,
                   resolved_by=?,
                   closed_count = COALESCE(closed_count,0) + 1,
                   work_time_total_sec = ?,
                   last_reopen_at = NULL
             WHERE ticket_id = ?
        """, (now_iso, admin_name, total_sec, ticket_id))
        conn.commit()
        conn.close()

                # 5) уведомления клиенту: закрытие + просьба оценить (пер-ботово из настроек)
        try:
            # определим channel_id тикета
            with get_db() as conn2:
                row_ch = conn2.execute("SELECT channel_id FROM tickets WHERE ticket_id=?", (ticket_id,)).fetchone()
                channel_id = row_ch["channel_id"] if row_ch else None
                row_cfg = conn2.execute("SELECT questions_cfg FROM channels WHERE id=?", (channel_id,)).fetchone()
                cfg = json.loads(row_cfg["questions_cfg"] or "{}") if row_cfg else {}
            fb = (cfg.get("feedback") or {})
            prompts = (fb.get("prompts") or {})
            on_close = prompts.get("on_close") or (
                "🌟 Пожалуйста, оцените качество поддержки: отправьте цифру 1–5."
            )

            close_msg = f"Ваше обращение #{ticket_id} закрыто. Для запуска нового диалога нажмите /start"
            send_telegram_message(chat_id=user_id, text=close_msg, parse_mode='HTML')
            send_telegram_message(chat_id=user_id, text=on_close, parse_mode='HTML')

            # 5.1. ставим «ожидание оценки» на 24 часа
            try:
                with get_db() as conn2:
                    expires = (datetime.datetime.now() + datetime.timedelta(hours=24)).isoformat()
                    conn2.execute("""
                        INSERT OR REPLACE INTO pending_feedback_requests
                            (user_id, channel_id, ticket_id, source, created_at, expires_at)
                        VALUES (?,?,?,?,?,?)
                    """, (user_id, channel_id, ticket_id, 'operator_close', dt.now().isoformat(), expires))
            except Exception as e:
                print(f"pending_feedback_requests insert error: {e}")

            def send_sorry():
                with sqlite3.connect(os.path.join(BASE_DIR, "tickets.db")) as conn2:
                    row2 = conn2.execute("SELECT 1 FROM feedbacks WHERE user_id = ? LIMIT 1", (user_id,)).fetchone()
                if not row2:
                    send_telegram_message(chat_id=user_id, text="Очень жаль, что не получили вашей оценки.", parse_mode='HTML')

            from threading import Timer
            Timer(900, send_sorry).start()

        except Exception as e:
            logging.error(f"❌ Ошибка при отправке уведомлений: {e}")

        return jsonify({"success": True})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})
        
if __name__ == "__main__":
    app.run(port=5000, debug=True)
