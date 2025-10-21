import logging
import sqlite3
import datetime
import json
import os
import uuid
import re
import asyncio
import signal, sys
from datetime import datetime, timezone
from telegram import Update, ReplyKeyboardMarkup, ReplyKeyboardRemove
from telegram.ext import (
    ApplicationBuilder,
    CommandHandler,
    MessageHandler,
    filters,
    ContextTypes,
    ConversationHandler,
)
# --- PTB filters compatibility (работает и с объектами, и с классами, и с .ALL) ---
import inspect

def _filter_inst(m, name_upper, name_camel):
    """Вернёт ИНСТАНС фильтра (или None), независимо от того, объект это, класс или .ALL."""
    f = getattr(m, name_upper, None) or getattr(m, name_camel, None)
    if f is None:
        return None
    # Если есть .ALL (например, Document.ALL), это уже инстанс — вернём его
    all_attr = getattr(f, "ALL", None)
    if all_attr is not None:
        return all_attr
    # Если это класс (Sticker/Animation/VideoNote/Audio), инстанцируем без аргументов
    if inspect.isclass(f):
        try:
            return f()
        except TypeError:
            return None
    # Иначе это уже инстанс фильтра
    return f

F_STICKER    = _filter_inst(filters, "STICKER", "Sticker")
F_ANIMATION  = _filter_inst(filters, "ANIMATION", "Animation")
F_VIDEO_NOTE = _filter_inst(filters, "VIDEO_NOTE", "VideoNote")
F_AUDIO      = _filter_inst(filters, "AUDIO", "Audio")

# Базовые фильтры-медиа, которые точно есть во всех v20+
MEDIA_FILTERS = (filters.PHOTO | filters.VOICE | filters.VIDEO | filters.Document.ALL)

# Аккуратно добавляем «дополнительные» только если получилось получить ИНСТАНС
for _extra in (F_ANIMATION, F_STICKER, F_VIDEO_NOTE, F_AUDIO):
    if _extra is not None:
        MEDIA_FILTERS = (MEDIA_FILTERS | _extra)


# Единый фильтр для всех медиа
MEDIA_FILTERS = (filters.PHOTO | filters.VOICE | filters.VIDEO | filters.Document.ALL)
for _extra in (F_ANIMATION, F_STICKER, F_VIDEO_NOTE, F_AUDIO):
    if _extra:
        MEDIA_FILTERS |= _extra

from apscheduler.schedulers.background import BackgroundScheduler
from config import DB_PATH, load_settings  # Убраны TOKEN и GROUP_CHAT_ID
from bot_settings_utils import (
    DEFAULT_BOT_PRESET_DEFINITIONS,
    build_location_presets,
    default_bot_settings,
    rating_allowed_values,
    rating_prompt,
    rating_response_for,
    rating_scale,
    sanitize_bot_settings,
)

ATTACHMENTS_DIR = "attachments"
os.makedirs(ATTACHMENTS_DIR, exist_ok=True)
os.makedirs(os.path.join(ATTACHMENTS_DIR, "temp"), exist_ok=True)

# --- Кеш channel_id по токену ---
_channel_id_cache = {}

# === channels & db helpers ===
import sqlite3, json
from functools import wraps

_channel_cache = {}

# === DB write helpers (используйте их из хендлеров) ===
def db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def create_ticket(conn, *, ticket_id: str, user_id: int, status: str, created_at: str, channel_id: int):
    conn.execute("""
        INSERT INTO tickets(ticket_id, user_id, status, created_at, channel_id)
        VALUES (?, ?, ?, ?, ?)
    """, (ticket_id, user_id, status, created_at, channel_id))
    # ⬇️ уведомление «новое обращение» для всех
    try:
        conn.execute(
            "INSERT INTO notifications(user, text, url) VALUES(?, ?, ?)",
            ('all', f'Новое обращение: {ticket_id}', f'/#open=ticket:{ticket_id}')
        )
    except Exception as _e:
        pass

def insert_message(conn, *, group_msg_id, user_id, business, location_type, city, location_name,
                   problem, created_at, username, category, ticket_id, created_date, created_time,
                   client_status, client_name, updated_at, updated_by, channel_id: int):
    conn.execute("""
      INSERT INTO messages(
        group_msg_id, user_id, business, location_type, city, location_name,
        problem, created_at, username, category, ticket_id, created_date,
        created_time, client_status, client_name, updated_at, updated_by, channel_id
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (group_msg_id, user_id, business, location_type, city, location_name,
          problem, created_at, username, category, ticket_id, created_date,
          created_time, client_status, client_name, updated_at, updated_by, channel_id))

    # уведомление «новое сообщение» (оператору, кто активен на тикете; иначе — всем)
    try:
        row = conn.execute(
            "SELECT user FROM ticket_active WHERE ticket_id=? ORDER BY last_seen DESC LIMIT 1",
            (ticket_id,)
        ).fetchone()
        target_user = row['user'] if row else None
        if target_user:
            conn.execute(
                "INSERT INTO notifications(user, text, url) VALUES(?, ?, ?)",
                (target_user, f'Новое сообщение в диалоге {ticket_id}', f'/#open=ticket:{ticket_id}')
            )
        else:
            conn.execute(
                "INSERT INTO notifications(user, text, url) VALUES(?, ?, ?)",
                ('all', f'Новое сообщение в диалоге {ticket_id}', f'/#open=ticket:{ticket_id}')
            )
    except Exception:
        pass

# === ХЕНДЛЕР ВХОДЯЩЕГО ТЕКСТА С ПРОСТАВЛЕНИЕМ channel_id И ЗАПИСЬЮ В БД ===
async def on_text(update, context):
    user = update.effective_user
    msg  = update.effective_message
    text = (msg.text or "").strip()

    channel_id = (context.application.bot_data.get("channel_id")
                  or get_channel_id_by_token(context.bot.token))
    ticket_id = f"{user.id}"  # твоя текущая схема тикета
    now_utc = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00","Z")
    created_at   = now_utc
    created_date = now_utc.split("T")[0]
    created_time = now_utc.split("T")[1].replace("Z","")

    # reply-связка из Telegram
    reply_mid = msg.reply_to_message.message_id if msg.reply_to_message else None
    this_mid  = msg.message_id

    conn = db()
    try:
        conn.execute("BEGIN")

        # === Собираем полный профиль пользователя ===
        first_name = user.first_name or ""
        last_name = user.last_name or ""
        username = user.username or ""
        lang = getattr(user, "language_code", "") or ""

        # Сформируем «человеческое имя»
        client_name = (first_name + (" " + last_name if last_name else "")).strip() or None

        # === Проверяем/создаём тикет ===
        exists = conn.execute(
            "SELECT 1 FROM tickets WHERE ticket_id = ? AND channel_id = ? LIMIT 1",
            (ticket_id, channel_id)
        ).fetchone()
        if not exists:
            create_ticket(conn,
                ticket_id=ticket_id, user_id=user.id,
                status='pending', created_at=created_at, channel_id=channel_id
            )

        # === Сохраняем сообщение + полный профиль в messages ===
        insert_message(conn,
            group_msg_id=None, user_id=user.id, business=None, location_type=None, city=None, location_name=None,
            problem=text, created_at=created_at, username=username, category=None, ticket_id=ticket_id,
            created_date=created_at.split('T')[0], created_time=created_at.split('T')[1][:8],
            client_status=None, client_name=client_name, updated_at=created_at, updated_by='bot', channel_id=channel_id
        )

        # === Сохраняем в историю ===
        add_history(
            conn=conn,
            ticket_id=ticket_id,
            sender='user',
            text=text,
            ts=now_utc,
            message_type='text',
            attachment=None,
            tg_message_id=msg.message_id,
            reply_to_tg_id=(msg.reply_to_message.message_id if getattr(msg, 'reply_to_message', None) else None)
        )

        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    # читаем конфиг вопросов канала и действуем по нему
    cfg = get_questions_cfg(channel_id)
    per_limit = cfg.get("per_dialog_limit", 0)
    questions = cfg.get("questions", [])

    # Пример: задаём первый вопрос, если он есть
    if questions:
        first_q = questions[0]
        label = first_q.get("label") or "Уточните, пожалуйста:"
        await msg.reply_text(label)

# === READ-ХЕЛПЕРЫ С УЧЁТОМ channel_id ===
def last_sender(ticket_id: str, channel_id: int) -> str | None:
    conn = db()
    try:
        row = conn.execute("""
            SELECT sender
            FROM chat_history
            WHERE ticket_id = ? AND channel_id = ?
            ORDER BY timestamp DESC
            LIMIT 1
        """, (ticket_id, channel_id)).fetchone()
        return row['sender'] if row else None
    finally:
        conn.close()

def has_support_reply(ticket_id: str, channel_id: int) -> bool:
    conn = db()
    try:
        row = conn.execute("""
            SELECT 1
            FROM chat_history
            WHERE ticket_id = ? AND channel_id = ? AND sender = 'support'
            LIMIT 1
        """, (ticket_id, channel_id)).fetchone()
        return row is not None
    finally:
        conn.close()


def add_history(conn, ticket_id, sender, text, ts, message_type='text', attachment=None,
                tg_message_id=None, reply_to_tg_id=None, channel_id=None, user_id=None):
    conn.execute("""
        INSERT INTO chat_history
          (user_id, ticket_id, sender, message, timestamp, message_type, attachment,
           tg_message_id, reply_to_tg_id, channel_id)
        VALUES (?,       ?,         ?,      ?,       ?,         ?,          ?,
                ?,            ?,             ?)
    """, (user_id, ticket_id, sender, text, ts, message_type, attachment,
          tg_message_id, reply_to_tg_id, channel_id))

# --- загрузка структуры локаций ---
def load_locations():
    try:
        with open("locations.json", "r", encoding="utf-8") as f:
            data = json.load(f)
            if isinstance(data, dict) and "tree" in data:
                tree = data.get("tree")
                return tree if isinstance(tree, dict) else {}
            return data if isinstance(data, dict) else {}
    except Exception as e:
        logging.error(f"Ошибка загрузки locations.json: {e}")
        return {}
LOCATIONS = load_locations()
BUSINESS_OPTIONS = list(LOCATIONS.keys())
SETTINGS = load_settings()


def load_bot_settings_config():
    try:
        settings_payload = load_settings()
    except Exception:
        settings_payload = {}

    try:
        locations_tree = load_locations()
    except Exception:
        locations_tree = {}

    definitions = build_location_presets(
        locations_tree if isinstance(locations_tree, dict) else {},
        base_definitions=DEFAULT_BOT_PRESET_DEFINITIONS,
    )

    if isinstance(settings_payload, dict):
        return sanitize_bot_settings(
            settings_payload.get("bot_settings"), definitions=definitions
        )
    return default_bot_settings(definitions)

# --- вспомогательная клавиатура ---
def get_keyboard_with_back(options, has_back=True, has_cancel=True):
    buttons = [[opt] for opt in options]
    row = []
    if has_back:
        row.append("◀️ Назад")
    if has_cancel:
        row.append("🚫 Отмена")
    if row:
        buttons.append(row)
    return ReplyKeyboardMarkup(buttons, one_time_keyboard=True, resize_keyboard=True)

# --- база данных ---
def init_db():
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS tickets (
                user_id INTEGER,
                group_msg_id INTEGER,
                status TEXT DEFAULT 'pending',
                resolved_at TEXT,
                resolved_by TEXT,
                ticket_id TEXT UNIQUE,
                channel_id INTEGER REFERENCES channels(id),
                PRIMARY KEY (user_id, ticket_id)
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS pending_feedback_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                channel_id INTEGER NOT NULL,
                ticket_id TEXT,
                source TEXT, -- 'operator_close' | 'auto_close'
                created_at TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                UNIQUE(user_id, channel_id, ticket_id, source)
            )
        """)
                # --- настройки приложения (по каналам) ---
        conn.execute("""
            CREATE TABLE IF NOT EXISTS app_settings(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                channel_id INTEGER NOT NULL,
                key TEXT NOT NULL,
                value TEXT NOT NULL,
                UNIQUE(channel_id, key)
            )
        """)

        # Колонка sent_at для контроля рассылки запросов
        cols = {r[1] for r in conn.execute("PRAGMA table_info(pending_feedback_requests)")}
        if "sent_at" not in cols:
            conn.execute("ALTER TABLE pending_feedback_requests ADD COLUMN sent_at TEXT")

        # Триггер: при любом переводе тикета в resolved создаём запрос на оценку
        conn.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_on_ticket_resolved
            AFTER UPDATE OF status ON tickets
            WHEN NEW.status = 'resolved'
            BEGIN
                INSERT OR IGNORE INTO pending_feedback_requests(
                    user_id, channel_id, ticket_id, source, created_at, expires_at
                )
                VALUES(
                    NEW.user_id,
                    NEW.channel_id,
                    NEW.ticket_id,
                    CASE WHEN NEW.resolved_by = 'Авто-система' THEN 'auto_close' ELSE 'operator_close' END,
                    datetime('now'),
                    datetime('now', '+5 minutes')
                );
            END;
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                group_msg_id INTEGER PRIMARY KEY,
                user_id INTEGER,
                business TEXT,
                location_type TEXT,
                city TEXT,
                location_name TEXT,
                problem TEXT,
                created_at TEXT,
                username TEXT DEFAULT '',
                category TEXT,
                ticket_id TEXT UNIQUE,
                created_date TEXT,
                created_time TEXT,
                channel_id INTEGER REFERENCES channels(id)
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS chat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                sender TEXT,
                message TEXT,
                timestamp TEXT,
                ticket_id TEXT,
                message_type TEXT,
                attachment TEXT,
                channel_id INTEGER REFERENCES channels(id)
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS feedbacks (
                user_id INTEGER,
                rating INTEGER,
                timestamp TEXT
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS client_statuses (
                user_id INTEGER PRIMARY KEY,
                status TEXT DEFAULT 'Не указан',
                client_name TEXT,
                updated_at TEXT,
                updated_by TEXT
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS client_usernames (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                username TEXT NOT NULL,
                seen_at TEXT NOT NULL,
                UNIQUE(user_id, username)
            )
        """)
        conn.execute("""
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

                # --- миграции под новые поля панели ---
        try:
            conn.execute("ALTER TABLE messages ADD COLUMN client_name TEXT")
        except sqlite3.OperationalError:
            pass  # Поле уже существует
        try:
            conn.execute("ALTER TABLE messages ADD COLUMN client_status TEXT")
        except sqlite3.OperationalError:
            pass  # Поле уже существует
        try:
            conn.execute("ALTER TABLE messages ADD COLUMN updated_at TEXT")
        except sqlite3.OperationalError:
            pass
        try:
            conn.execute("ALTER TABLE messages ADD COLUMN updated_by TEXT")
        except sqlite3.OperationalError:
            pass
init_db()

# --- состояния ---
BUSINESS, LOCATION_TYPE, CITY, LOCATION_NAME, PROBLEM = range(5)
PREV_STEP = 5

# --- обработчик решения пользователя ---
async def previous_choice_decision(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = (update.message.text or "").strip().lower()
    if "да" in text or "✅" in text:
        # восстановить последний выбор и сразу попросить описание проблемы
        sel = context.user_data.get("last_selection") or {}
        context.user_data.update(sel)
        await update.message.reply_text(
            "Хорошо. Опишите проблему для создания новой заявки:",
            reply_markup=get_keyboard_with_back([], has_back=True, has_cancel=True)
        )
        return PROBLEM
    # иначе — начинаем заново
    await update.message.reply_text(
        "Ок, начнём заново.\n1️⃣ Выберите бизнес:",
        reply_markup=get_keyboard_with_back(BUSINESS_OPTIONS, has_back=False)
    )
    return BUSINESS
    
# --- /cancel ---
async def cancel(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Отмена диалога: чистим состояние и убираем клавиатуру."""
    try:
        context.user_data.clear()
    except Exception:
        pass
    await update.message.reply_text("❌ Отменено.", reply_markup=ReplyKeyboardRemove())
    return ConversationHandler.END

# --- /start ---
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data.clear()
    user = update.effective_user

    channel_id = context.application.bot_data["channel_id"]

    # 1) Предложить продолжить с последним выбором по последней ЗАКРЫТОЙ заявке
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row

        last_closed = conn.execute(
            "SELECT ticket_id FROM tickets "
            "WHERE user_id=? AND status='resolved' AND channel_id=? "
            "ORDER BY ROWID DESC LIMIT 1",
            (user.id, channel_id)
        ).fetchone()

        if last_closed:
            row = conn.execute(
                "SELECT business, location_type, city, location_name "
                "FROM messages WHERE ticket_id=? ORDER BY ROWID DESC LIMIT 1",
                (last_closed["ticket_id"],)
            ).fetchone()

            if row:
                context.user_data["last_selection"] = {
                    "business": row["business"],
                    "location_type": row["location_type"],
                    "city": row["city"],
                    "location_name": row["location_name"],
                }
                summary = (
                    "Нашёл ваши последние выбранные данные:\n"
                    f"• Бизнес: {row['business']}\n"
                    f"• Тип: {row['location_type']}\n"
                    f"• Город: {row['city']}\n"
                    f"• Локация: {row['location_name']}\n\n"
                    "Хотите продолжить с этими данными?"
                )
                kb = ReplyKeyboardMarkup([["✅ Да", "↩️ Нет"]], resize_keyboard=True, one_time_keyboard=True)
                await update.message.reply_text(summary, reply_markup=kb)
                return PREV_STEP

        # 2) Если есть активная (pending) — не даём создать новую
        active = conn.execute(
            "SELECT 1 FROM tickets WHERE user_id = ? AND status = 'pending' AND channel_id = ?",
            (user.id, channel_id),
        ).fetchone()

    if active:
        await update.message.reply_text("У вас уже есть активная заявка. Ожидайте ответа поддержки.")
        return ConversationHandler.END

    # 3) Обычный старт (первичный сбор данных)
    await update.message.reply_text(f"Привет, {user.first_name}! 🌟\nНачнём заполнять заявку.")
    await update.message.reply_text(
        "1️⃣ Выберите бизнес:",
        reply_markup=get_keyboard_with_back(BUSINESS_OPTIONS, has_back=False),
    )
    return BUSINESS

# --- /tickets - просмотр старых заявок ---
async def tickets_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    with sqlite3.connect(DB_PATH) as conn:
        closed_tickets = conn.execute(
            "SELECT ticket_id, status, resolved_at FROM tickets WHERE user_id = ? ORDER BY resolved_at DESC LIMIT 5", 
            (user.id,)
        ).fetchall()
    if closed_tickets:
        ticket_list = "\n".join([f"ID: {ticket[0]} - Статус: {ticket[1]} - Закрыта: {ticket[2][:10] if ticket[2] else 'N/A'}" for ticket in closed_tickets])
        await update.message.reply_text(f"📋 Ваши последние заявки:\n{ticket_list}")
    else:
        await update.message.reply_text("У вас пока нет закрытых заявок.")

# --- выбор бизнеса ---
async def business_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text
    if text == "◀️ Назад":
        return await start(update, context)
    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END
    if text not in BUSINESS_OPTIONS:
        await update.message.reply_text("Выберите бизнес:", reply_markup=get_keyboard_with_back(BUSINESS_OPTIONS, has_back=False))
        return BUSINESS
    context.user_data['business'] = text
    location_type_options = list(LOCATIONS[text].keys())
    context.user_data['location_type_options'] = location_type_options
    await update.message.reply_text(
        "2️⃣ Тип локации:",
        reply_markup=get_keyboard_with_back(location_type_options)
    )
    return LOCATION_TYPE

# --- выбор типа локации ---
async def location_type_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text.strip()
    if text == "◀️ Назад":
        await update.message.reply_text(
            "1️⃣ Выберите бизнес:",
            reply_markup=get_keyboard_with_back(BUSINESS_OPTIONS, has_back=False)
        )
        return BUSINESS
    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END
    if text not in context.user_data.get('location_type_options', []):
        await update.message.reply_text(
            "Выберите тип из списка:",
            reply_markup=get_keyboard_with_back(context.user_data.get('location_type_options', []), has_back=False)
        )
        return LOCATION_TYPE
    context.user_data['location_type'] = text
    business = context.user_data['business']
    loc_type = context.user_data['location_type']
    try:
        cities = list(LOCATIONS[business][loc_type].keys())
        await update.message.reply_text(
            "3️⃣ Выберите город:",
            reply_markup=get_keyboard_with_back(cities)
        )
        return CITY
    except KeyError:
        await update.message.reply_text("❌ Ошибка: нет данных. Перезапустите /start")
        return ConversationHandler.END

# --- выбор города ---
async def city_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text
    if text == "◀️ Назад":
        await update.message.reply_text(
            "2️⃣ Тип локации:",
            reply_markup=get_keyboard_with_back(context.user_data.get('location_type_options', []))
        )
        return LOCATION_TYPE
    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END
    business = context.user_data['business']
    loc_type = context.user_data['location_type']
    if text not in LOCATIONS.get(business, {}).get(loc_type, {}):
        cities = list(LOCATIONS[business][loc_type].keys())
        await update.message.reply_text("Выберите город:", reply_markup=get_keyboard_with_back(cities, has_back=False))
        return CITY
    context.user_data['city'] = text
    locations = LOCATIONS[business][loc_type][text]
    await update.message.reply_text(
        "4️⃣ Выберите локацию:",
        reply_markup=get_keyboard_with_back(locations)
    )
    return LOCATION_NAME

# --- выбор локации ---
async def location_name_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text
    if text == "◀️ Назад":
        business = context.user_data['business']
        loc_type = context.user_data['location_type']
        cities = list(LOCATIONS[business][loc_type].keys())
        await update.message.reply_text(
            "3️⃣ Выберите город:",
            reply_markup=get_keyboard_with_back(cities)
        )
        return CITY
    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END
    business = context.user_data['business']
    loc_type = context.user_data['location_type']
    city = context.user_data['city']
    locations = LOCATIONS[business][loc_type][city]
    if text not in locations:
        await update.message.reply_text("Выберите локацию из списка.", reply_markup=get_keyboard_with_back(locations, has_back=False))
        return LOCATION_NAME
    context.user_data['location_name'] = text
    await update.message.reply_text("5️⃣ Опишите проблему:", reply_markup=get_keyboard_with_back([], has_back=True, has_cancel=True))
    return PROBLEM

# --- описание проблемы и создание тикета ---
async def problem_description(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text
    if text == "◀️ Назад":
        business = context.user_data['business']
        loc_type = context.user_data['location_type']
        city = context.user_data['city']
        locations = LOCATIONS[business][loc_type][city]
        await update.message.reply_text(
            "4️⃣ Выберите локацию:",
            reply_markup=get_keyboard_with_back(locations)
        )
        return LOCATION_NAME
    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END
    if context.user_data.get('in_progress'):
        await update.message.reply_text("❌ Вы уже отправляете заявку.")
        return ConversationHandler.END
    context.user_data['in_progress'] = True
    context.user_data['problem'] = text

    user = update.effective_user
    username = user.username or ""
    ticket_id = str(uuid.uuid4())[:8]
    context.user_data['ticket_id'] = ticket_id
    now = datetime.now()
    created_at = now.isoformat()
    created_date = now.strftime('%Y-%m-%d')
    created_time = now.strftime('%H:%M')

    channel_id = context.application.bot_data["channel_id"]

    full_message = (
        f"📩 <b>Новая заявка #{ticket_id}</b>\n"
        f"👤 <b>Пользователь:</b> @{username} (ID: <code>{user.id}</code>)\n"
        f"🏢 <b>Бизнес:</b> {context.user_data['business']}\n"
        f"📍 <b>Тип:</b> {context.user_data['location_type']}\n"
        f"🏙️ <b>Город:</b> {context.user_data['city']}\n"
        f"🏪 <b>Локация:</b> {context.user_data['location_name']}\n"
        f"📝 <b>Проблема:</b> {context.user_data['problem']}\n"
        f"📌 <b>Статус:</b> ⏳ В обработке"
    )

    try:
        # 1) куда слать карточку
        group_chat_id = get_support_chat_id(channel_id)
        if not group_chat_id and is_group_update(update):
            group_chat_id = update.effective_chat.id

        sent = None
        if group_chat_id:
            # 2) карточка в группу
            sent = await context.bot.send_message(
                chat_id=group_chat_id,
                text=full_message,
                parse_mode='HTML'
            )

            # 3) медиа — тредом к карточке
            media_messages = []
            if 'media_attachments' in context.user_data:
                for media in context.user_data['media_attachments']:
                    try:
                        if not media.get('attachment_path'):
                            continue
                        if media['message_type'] == 'photo':
                            with open(media['attachment_path'], 'rb') as ph:
                                media_msg = await context.bot.send_photo(
                                    chat_id=group_chat_id,
                                    photo=ph,
                                    caption=f"📎 Медиафайл к заявке #{ticket_id}",
                                    reply_to_message_id=sent.message_id
                                )
                        elif media['message_type'] == 'document':
                            with open(media['attachment_path'], 'rb') as doc:
                                media_msg = await context.bot.send_document(
                                    chat_id=group_chat_id,
                                    document=doc,
                                    caption=f"📎 Документ к заявке #{ticket_id}",
                                    reply_to_message_id=sent.message_id
                                )
                        elif media['message_type'] == 'video':
                            with open(media['attachment_path'], 'rb') as vd:
                                media_msg = await context.bot.send_video(
                                    chat_id=group_chat_id,
                                    video=vd,
                                    caption=f"📎 Видео к заявке #{ticket_id}",
                                    reply_to_message_id=sent.message_id
                                )
                        elif media['message_type'] == 'voice':
                            with open(media['attachment_path'], 'rb') as vc:
                                media_msg = await context.bot.send_voice(
                                    chat_id=group_chat_id,
                                    voice=vc,
                                    caption=f"📎 Голосовое сообщение к заявке #{ticket_id}",
                                    reply_to_message_id=sent.message_id
                                )
                        elif media['message_type'] == 'animation':  # ← ДОБАВИТЬ
                            with open(media['attachment_path'], 'rb') as an:
                                media_msg = await context.bot.send_animation(
                                    chat_id=group_chat_id,
                                    animation=an,
                                    caption=f"📎 Анимация к заявке #{ticket_id}",
                                    reply_to_message_id=sent.message_id
                                )
                        elif media['message_type'] == 'sticker':    # ← ДОБАВИТЬ
                            with open(media['attachment_path'], 'rb') as st:
                                media_msg = await context.bot.send_sticker(
                                    chat_id=group_chat_id,
                                    sticker=st,
                                    reply_to_message_id=sent.message_id
                                )
                        elif media['message_type'] == 'video_note': # ← ДОБАВИТЬ
                            with open(media['attachment_path'], 'rb') as vn:
                                media_msg = await context.bot.send_video_note(
                                    chat_id=group_chat_id,
                                    video_note=vn,
                                    reply_to_message_id=sent.message_id
                                )
                        elif media['message_type'] == 'audio':      # ← ДОБАВИТЬ
                            with open(media['attachment_path'], 'rb') as au:
                                media_msg = await context.bot.send_audio(
                                    chat_id=group_chat_id,
                                    audio=au,
                                    caption=f"📎 Аудио к заявке #{ticket_id}",
                                    reply_to_message_id=sent.message_id
                                )

                        else:
                            media_msg = None
                        if media_msg:
                            media_messages.append(media_msg.message_id)
                    except Exception as e:
                        logging.error(f"Ошибка отправки медиафайла: {e}")
                        await context.bot.send_message(
                            chat_id=group_chat_id,
                            text=f"❌ Не удалось отправить медиафайл для заявки #{ticket_id}: {e}",
                            reply_to_message_id=sent.message_id
                        )

        # 4) запись в БД (+ перенос медиа)
        if context.user_data.get('media_attachments'):
            full_message += f"\n📎 Прикреплено медиафайлов: {len(context.user_data['media_attachments'])}"
            full_message += f"\n💾 Для просмотра: /media {ticket_id}"

        with sqlite3.connect(DB_PATH) as conn:
            save_username_if_changed(conn, user.id, username)
            conn.execute(
                "INSERT INTO tickets (user_id, group_msg_id, status, ticket_id, channel_id) VALUES (?, ?, ?, ?, ?)",
                (user.id, (sent.message_id if sent else None), "pending", ticket_id, channel_id)
            )
            conn.execute(
                "INSERT INTO messages (group_msg_id, user_id, business, location_type, city, location_name, problem, created_at, username, ticket_id, created_date, created_time, channel_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ((sent.message_id if sent else None), user.id, context.user_data['business'], context.user_data['location_type'],
                 context.user_data['city'], context.user_data['location_name'], context.user_data['problem'],
                 created_at, username, ticket_id, created_date, created_time, channel_id)
            )
            conn.execute(
                "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id, channel_id) VALUES (?, ?, ?, ?, ?, ?)",
                (user.id, "user", context.user_data['problem'], created_at, ticket_id, channel_id)
            )
            if context.user_data.get('media_attachments'):
                for media in context.user_data['media_attachments']:
                    if media.get('attachment_path') and os.path.exists(media['attachment_path']):
                        filename = os.path.basename(media['attachment_path'])
                        new_path = os.path.join(ATTACHMENTS_DIR, ticket_id, filename)
                        os.makedirs(os.path.dirname(new_path), exist_ok=True)
                        import shutil
                        shutil.copy2(media['attachment_path'], new_path)
                        conn.execute(
                            "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id, message_type, attachment, channel_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            (user.id, "user", media.get('text'), media.get('timestamp'), ticket_id, media.get('message_type'), new_path, channel_id)
                        )
            conn.commit()

        # 5) пользователю — только подтверждение
        await update.message.reply_text(
            f"✅ Заявка отправлена. ID: {ticket_id}\nСпасибо!",
            reply_markup=ReplyKeyboardRemove()
        )

    except Exception as e:
        await update.message.reply_text(f"❌ Ошибка: {e}")
        logging.error(f"Ошибка отправки: {e}")
    finally:
        # чистим временные файлы
        for media in context.user_data.get('media_attachments', []):
            try:
                p = media.get('attachment_path')
                if p and os.path.exists(p):
                    os.remove(p)
            except:
                pass
        context.user_data.clear()

    return ConversationHandler.END
    
async def save_user_contact(update: Update, context: ContextTypes.DEFAULT_TYPE):
    contact = update.message.contact
    user = update.effective_user
    # сохраняем только если это сам клиент шарит свой контакт (contact.user_id == user.id)
    if not contact or (contact.user_id and contact.user_id != user.id):
        return
    try:
        with sqlite3.connect(DB_PATH) as conn:
            conn.row_factory = sqlite3.Row
            insert_phone_if_new(conn, user.id, contact.phone_number, source='telegram', label='из Telegram', created_by='bot')
            # на всякий — обновим username-историю тоже
            save_username_if_changed(conn, user.id, user.username or "")
            conn.commit()
        await update.message.reply_text("✅ Телефон получен.")
    except Exception as e:
        await update.message.reply_text(f"❌ Ошибка сохранения телефона: {e}")

# --- ответ администратора ---
async def reply_to_user(update: Update, context: ContextTypes.DEFAULT_TYPE):
    message = update.message
    if not message.reply_to_message:
        return
    replied = message.reply_to_message
    if replied.from_user.id != context.bot.id:
        return

    # Вытаскиваем user_id из текста карточки
    match = re.search(r"ID: <code>(\d+)</code>", replied.text or "")
    if not match:
        return
    user_id = int(match.group(1))

    admin = update.effective_user
    admin_name = f"@{admin.username}" if admin.username else admin.first_name
    reply_text = f"📩 <b>Ответ от поддержки ({admin_name}):</b>\n{message.text}"

    # channel_id текущего бота
    channel_id = context.application.bot_data["channel_id"]

    try:
        # Отправляем клиенту
        await context.bot.send_message(chat_id=user_id, text=reply_text, parse_mode="HTML")

        # Находим последний тикет пользователя В РАМКАХ ЭТОГО КАНАЛА
        now_iso = datetime.now().isoformat()
        with sqlite3.connect(DB_PATH) as conn:
            row = conn.execute(
                "SELECT ticket_id FROM tickets WHERE user_id = ? AND channel_id = ? "
                "ORDER BY ROWID DESC LIMIT 1",
                (user_id, channel_id),
            ).fetchone()
            ticket_id = row[0] if row else "unknown"

            # Пишем в историю
            conn.execute(
                "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id, "
                "message_type, attachment, channel_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (user_id, "support", message.text, now_iso, ticket_id, "text", None, channel_id),
            )
            conn.commit()

        await message.reply_text("✅ Ответ отправлен. Диалог продолжается.")
    except Exception as e:
        await message.reply_text(f"❌ Ошибка: {e}")

# --- команды статистики ---
async def stats(update: Update, context: ContextTypes.DEFAULT_TYPE):
    with sqlite3.connect(DB_PATH) as conn:
        total = conn.execute("SELECT COUNT(*) FROM tickets").fetchone()[0]
        resolved = conn.execute("SELECT COUNT(*) FROM tickets WHERE status = 'resolved'").fetchone()[0]
        pending_count = total - resolved
    await update.message.reply_text(
        f"📊 <b>Статистика</b>\n"
        f"📬 Всего: <b>{total}</b>\n"
        f"✅ Решено: <b>{resolved}</b>\n"
        f"⏳ В обработке: <b>{pending_count}</b>",
        parse_mode="HTML",
    )

async def pending(update: Update, context: ContextTypes.DEFAULT_TYPE):
    with sqlite3.connect(DB_PATH) as conn:
        rows = conn.execute("""
            SELECT t.user_id, t.ticket_id, m.problem 
            FROM tickets t 
            JOIN messages m ON t.ticket_id = m.ticket_id 
            WHERE t.status = 'pending'
        """).fetchall()
    if not rows:
        await update.message.reply_text("✅ Нет заявок в обработке.")
        return
    text = "📬 <b>Заявки в обработке:</b>\n"
    for user_id, ticket_id, problem in rows:
        text += f"• ID: {ticket_id} - Пользователь: <code>{user_id}</code>\nПроблема: {problem[:50]}...\n"
    await update.message.reply_text(text, parse_mode="HTML")

async def my_tickets(update: Update, context: ContextTypes.DEFAULT_TYPE):
    admin = update.effective_user
    admin_name = f"@{admin.username}" if admin.username else admin.first_name
    with sqlite3.connect(DB_PATH) as conn:
        rows = conn.execute("SELECT COUNT(*) FROM tickets WHERE resolved_by = ?", (admin_name,)).fetchone()
    count = rows[0] if rows else 0
    await update.message.reply_text(
        f"🛠 Вы закрыли <b>{count}</b> заявок.",
        parse_mode="HTML",
    )

# --- сохранение текста пользователя ---
async def save_user_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    text_msg = update.message.text or ""
    channel_id = (
        context.chat_data.get("channel_id")
        or getattr(context.application, "bot_data", {}).get("channel_id")
        or get_channel_id_by_token(context.bot.token)
        )

    try:
        # 1) Берём последний активный тикет этого пользователя в рамках текущего канала
        with sqlite3.connect(DB_PATH) as conn:
            conn.row_factory = sqlite3.Row
            row = conn.execute(
                "SELECT ticket_id FROM tickets "
                "WHERE user_id = ? AND status = 'pending' AND channel_id = ? "
                "ORDER BY ROWID DESC LIMIT 1",
                (user.id, channel_id)
            ).fetchone()
            if not row:
                await update.message.reply_text(
                    "У вас нет открытой заявки. Нажмите /start, чтобы создать новую."
                )
                return
            ticket_id = row["ticket_id"] if "ticket_id" in row.keys() else row[0]

            # 2) Обновим историю username (ОБЯЗАТЕЛЬНО отдельным вызовом, НЕ внутри SQL)
            try:
                save_username_if_changed(conn, user.id, user.username or "")
            except Exception:
                # не роняем основной поток, если тут что-то не так
                pass

            # 3) Сохраняем сообщение в историю
            conn.execute(
                """
                INSERT INTO chat_history (
                    user_id, ticket_id, sender, message, timestamp, message_type, attachment,
                    tg_message_id, reply_to_tg_id, channel_id
                )
                VALUES (?, ?, 'user', ?, ?, 'text', NULL, ?, ?, ?)
                """,
                (
                    user.id,
                    ticket_id,
                    text_msg,
                    datetime.now().isoformat(),
                    update.message.message_id,
                    (update.message.reply_to_message.message_id if getattr(update.message, 'reply_to_message', None) else None),
                    channel_id
                )
            )
            conn.commit()

        # 4) Уведомляем группу в треде (если настроена)
        group_chat_id = get_support_chat_id(channel_id)
        if group_chat_id:
            with sqlite3.connect(DB_PATH) as conn2:
                conn2.row_factory = sqlite3.Row
                row2 = conn2.execute(
                    "SELECT group_msg_id FROM tickets WHERE ticket_id = ?",
                    (ticket_id,)
                ).fetchone()
            thread_id = (row2["group_msg_id"] if row2 and hasattr(row2, "keys") and "group_msg_id" in row2.keys()
                         else (row2[0] if row2 else None))
            await context.bot.send_message(
                chat_id=group_chat_id,
                text=f"✉️ Сообщение от клиента #{ticket_id}:\n{text_msg}",
                reply_to_message_id=thread_id
            )

    except Exception as e:
        logging.error(f"Ошибка сохранения сообщения: {e}")


# --- сохранение медиа ---
async def save_user_media(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    message = update.message

    # режим до отправки проблемы — складываем во временную папку
    if 'business' in context.user_data and 'problem' not in context.user_data:
        if 'media_attachments' not in context.user_data:
            context.user_data['media_attachments'] = []
        timestamp = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace('+00:00','Z')
        attachment_info = {
            'timestamp': timestamp,
            'message_type': None,
            'attachment_path': None,
            'text': message.caption or ""
        }
        os.makedirs(os.path.join(ATTACHMENTS_DIR, "temp", str(user.id)), exist_ok=True)
        try:
            if message.photo:
                attachment_info['message_type'] = "photo"
                file = await message.photo[-1].get_file()
                attachment_info['attachment_path'] = os.path.join(ATTACHMENTS_DIR, "temp", str(user.id), f"{file.file_id}.jpg")
                await file.download_to_drive(attachment_info['attachment_path'])
            elif message.voice:
                attachment_info['message_type'] = "voice"
                file = await message.voice.get_file()
                attachment_info['attachment_path'] = os.path.join(ATTACHMENTS_DIR, "temp", str(user.id), f"{file.file_id}.ogg")
                await file.download_to_drive(attachment_info['attachment_path'])
            elif message.video:
                attachment_info['message_type'] = "video"
                file = await message.video.get_file()
                attachment_info['attachment_path'] = os.path.join(ATTACHMENTS_DIR, "temp", str(user.id), f"{file.file_id}.mp4")
                await file.download_to_drive(attachment_info['attachment_path'])
            elif message.animation:  # ← ДОБАВИТЬ (GIF/анимации из Telegram)
                attachment_info['message_type'] = "animation"
                file = await message.animation.get_file()
                # сохраним как .mp4 (так Telegram отдаёт анимации)
                attachment_info['attachment_path'] = os.path.join(ATTACHMENTS_DIR, "temp", str(user.id), f"{file.file_id}.mp4")
                await file.download_to_drive(attachment_info['attachment_path'])
            elif message.sticker:
                attachment_info['message_type'] = "sticker"
                file = await message.sticker.get_file()
                # правильный выбор расширения
                if getattr(message.sticker, "is_animated", False):
                    ext = ".tgs"     # лотти-стикер
                elif getattr(message.sticker, "is_video", False):
                    ext = ".webm"    # видео-стикер
                else:
                    ext = ".webp"    # статический
                attachment_info['attachment_path'] = os.path.join(
                    ATTACHMENTS_DIR, "temp", str(user.id), f"{file.file_id}{ext}"
                )
                await file.download_to_drive(attachment_info['attachment_path'])
            elif message.video_note:  # ← ДОБАВИТЬ (круглое видео)
                attachment_info['message_type'] = "video_note"
                file = await message.video_note.get_file()
                attachment_info['attachment_path'] = os.path.join(ATTACHMENTS_DIR, "temp", str(user.id), f"{file.file_id}.mp4")
                await file.download_to_drive(attachment_info['attachment_path'])
            elif message.audio:  # ← ДОБАВИТЬ (музыка/аудио)
                attachment_info['message_type'] = "audio"
                file = await message.audio.get_file()
                filename = (message.audio.file_name or f"{file.file_id}.mp3")
                attachment_info['attachment_path'] = os.path.join(ATTACHMENTS_DIR, "temp", str(user.id), filename)
                await file.download_to_drive(attachment_info['attachment_path'])
            elif message.document:
                attachment_info['message_type'] = "document"
                file = await message.document.get_file()
                filename = message.document.file_name or f"{file.file_id}"
                attachment_info['attachment_path'] = os.path.join(ATTACHMENTS_DIR, "temp", str(user.id), filename)
                await file.download_to_drive(attachment_info['attachment_path'])
            else:
                return
            context.user_data['media_attachments'].append(attachment_info)
            await update.message.reply_text("✅ Медиа сохранено. Теперь опишите проблему текстом.")
            logging.info(f"Временное медиа от {user.id} сохранено")
        except Exception as e:
            await update.message.reply_text(f"❌ Ошибка: {e}")
            logging.error(f"Ошибка сохранения временного медиа: {e}")
        return

    # режим после создания тикета — сразу в постоянное хранилище
    channel_id = context.application.bot_data["channel_id"]
    with sqlite3.connect(DB_PATH) as conn:
        row = conn.execute(
            "SELECT ticket_id FROM tickets WHERE user_id = ? AND status = 'pending' AND channel_id = ? ORDER BY ROWID DESC LIMIT 1",
            (user.id, channel_id)
        ).fetchone()
    if not row:
        await update.message.reply_text("❌ Ошибка: активная заявка не найдена. Перезапустите /start")
        return
    ticket_id = row[0]

    timestamp = datetime.now().isoformat()
    attachment_path = None
    message_type = None
    text = message.caption or ""
    os.makedirs(os.path.join(ATTACHMENTS_DIR, ticket_id), exist_ok=True)

    try:
        if message.photo:
            message_type = "photo"
            file = await message.photo[-1].get_file()
            attachment_path = os.path.join(ATTACHMENTS_DIR, ticket_id, f"{file.file_id}.jpg")
            await file.download_to_drive(attachment_path)
        elif message.voice:
            message_type = "voice"
            file = await message.voice.get_file()
            attachment_path = os.path.join(ATTACHMENTS_DIR, ticket_id, f"{file.file_id}.ogg")
            await file.download_to_drive(attachment_path)
        elif message.video:
            message_type = "video"
            file = await message.video.get_file()
            attachment_path = os.path.join(ATTACHMENTS_DIR, ticket_id, f"{file.file_id}.mp4")
            await file.download_to_drive(attachment_path)
        elif message.animation:  # ← ДОБАВИТЬ
            message_type = "animation"
            file = await message.animation.get_file()
            attachment_path = os.path.join(ATTACHMENTS_DIR, ticket_id, f"{file.file_id}.mp4")
            await file.download_to_drive(attachment_path)
        elif message.sticker:
            message_type = "sticker"
            file = await message.sticker.get_file()
            if getattr(message.sticker, "is_animated", False):
                ext = ".tgs"
            elif getattr(message.sticker, "is_video", False):
                ext = ".webm"
            else:
                ext = ".webp"
            attachment_path = os.path.join(ATTACHMENTS_DIR, ticket_id, f"{file.file_id}{ext}")
            await file.download_to_drive(attachment_path)
        elif message.video_note: # ← ДОБАВИТЬ
            message_type = "video_note"
            file = await message.video_note.get_file()
            attachment_path = os.path.join(ATTACHMENTS_DIR, ticket_id, f"{file.file_id}.mp4")
            await file.download_to_drive(attachment_path)
        elif message.audio:      # ← ДОБАВИТЬ
            message_type = "audio"
            file = await message.audio.get_file()
            filename = (message.audio.file_name or f"{file.file_id}.mp3")
            attachment_path = os.path.join(ATTACHMENTS_DIR, ticket_id, filename)
            await file.download_to_drive(attachment_path)
        elif message.document:
            message_type = "document"
            file = await message.document.get_file()
            filename = message.document.file_name or f"{file.file_id}"
            attachment_path = os.path.join(ATTACHMENTS_DIR, ticket_id, filename)
            await file.download_to_drive(attachment_path)
        else:
            return

        msg = update.effective_message
        reply_mid = msg.reply_to_message.message_id if msg.reply_to_message else None
        this_mid  = msg.message_id

        with sqlite3.connect(DB_PATH) as conn:
            conn.execute("""
                INSERT INTO chat_history (user_id, ticket_id, sender, message, timestamp, message_type, attachment, tg_message_id, reply_to_tg_id, channel_id)
                VALUES (?, ?, 'user', ?, ?, ?, ?, ?, ?, ?)
            """, (
                user.id, ticket_id,
                (msg.caption or "") if hasattr(msg, "caption") else "",
                timestamp,
                message_type, attachment_path,
                this_mid, reply_mid, channel_id
            ))
            conn.commit()

        # уведомим группу медиа в треде
        try:
            group_chat_id = get_support_chat_id(channel_id)
            if group_chat_id:
                with sqlite3.connect(DB_PATH) as conn2:
                    row2 = conn2.execute(
                        "SELECT group_msg_id FROM tickets WHERE ticket_id = ?",
                        (ticket_id,)
                    ).fetchone()
                thread_id = row2[0] if row2 else None

                if message_type == "photo":
                    with open(attachment_path, "rb") as photo:
                        await context.bot.send_photo(group_chat_id, photo=photo, caption=f"📎 Медиа от клиента #{ticket_id}", reply_to_message_id=thread_id)
                elif message_type == "video":
                    with open(attachment_path, "rb") as video:
                        await context.bot.send_video(group_chat_id, video=video, caption=f"📎 Медиа от клиента #{ticket_id}", reply_to_message_id=thread_id)
                elif message_type == "document":
                    with open(attachment_path, "rb") as doc:
                        await context.bot.send_document(group_chat_id, document=doc, caption=f"📎 Медиа от клиента #{ticket_id}", reply_to_message_id=thread_id)
                elif message_type == "voice":
                    with open(attachment_path, "rb") as voice:
                        await context.bot.send_voice(group_chat_id, voice=voice, caption=f"📎 Голосовое от клиента #{ticket_id}", reply_to_message_id=thread_id)
                elif message_type == "animation":  # ← ДОБАВИТЬ
                    with open(attachment_path, "rb") as anim:
                        await context.bot.send_animation(group_chat_id, animation=anim, caption=f"📎 Медиа от клиента #{ticket_id}", reply_to_message_id=thread_id)
                elif message_type == "sticker":    # ← ДОБАВИТЬ
                    with open(attachment_path, "rb") as st:
                        await context.bot.send_sticker(group_chat_id, sticker=st, reply_to_message_id=thread_id)
                elif message_type == "video_note": # ← ДОБАВИТЬ
                    with open(attachment_path, "rb") as vn:
                        await context.bot.send_video_note(group_chat_id, video_note=vn, reply_to_message_id=thread_id)
                elif message_type == "audio":      # ← ДОБАВИТЬ
                    with open(attachment_path, "rb") as au:
                        await context.bot.send_audio(group_chat_id, audio=au, caption=f"📎 Аудио от клиента #{ticket_id}", reply_to_message_id=thread_id)
        except Exception as e:
            logging.error(f"Не удалось уведомить группу медиа: {e}")

        await update.message.reply_text("✅ Медиа сохранено. Спасибо!")
        logging.info(f"Медиа от {user.id} сохранено")
    except Exception as e:
        await update.message.reply_text(f"❌ Ошибка: {e}")
        logging.error(f"Ошибка сохранения медиа: {e}")

# --- команда для просмотра медиафайлов заявки ---
async def show_media(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not context.args:
        await update.message.reply_text("Использование: /media <ticket_id>")
        return
    ticket_id = context.args[0]
    with sqlite3.connect(DB_PATH) as conn:
        media_files = conn.execute(
            "SELECT message_type, attachment FROM chat_history WHERE ticket_id = ? "
            "AND message_type IN ('photo','video','document','voice','animation','sticker','video_note','audio')",
            (ticket_id,)
        ).fetchall()
    if not media_files:
        await update.message.reply_text(f"Для заявки #{ticket_id} нет медиафайлов.")
        return
    await update.message.reply_text(f"📎 Медиафайлы заявки #{ticket_id}:")
    for message_type, attachment_path in media_files:
        path = attachment_path
        if path and not os.path.isabs(path) and not os.path.exists(path):
            # старые записи могли хранить только имя файла → собираем полный путь
            path = os.path.join(ATTACHMENTS_DIR, ticket_id, os.path.basename(path))

        if path and os.path.exists(path):
            try:
                if message_type == 'photo':
                    with open(path, 'rb') as photo:
                        await update.message.reply_photo(photo=photo, caption=f"Фото из заявки #{ticket_id}")
                elif message_type == 'video':
                    with open(path, 'rb') as video:
                        await update.message.reply_video(video=video, caption=f"Видео из заявки #{ticket_id}")
                elif message_type == 'document':
                    with open(path, 'rb') as doc:
                        await update.message.reply_document(document=doc, caption=f"Документ из заявки #{ticket_id}")
                elif message_type == 'voice':
                    with open(path, 'rb') as voice:
                        await update.message.reply_voice(voice=voice, caption=f"Голосовое сообщение из заявки #{ticket_id}")
                elif message_type == 'animation':  # ← ДОБАВИТЬ
                    with open(path, 'rb') as anim:
                        await update.message.reply_animation(animation=anim, caption=f"Анимация из заявки #{ticket_id}")
                elif message_type == 'sticker':    # ← ДОБАВИТЬ
                    with open(path, 'rb') as st:
                        # стикеры отправляются без caption
                        await update.message.reply_sticker(sticker=st)
                elif message_type == 'video_note': # ← ДОБАВИТЬ
                    with open(path, 'rb') as vn:
                        await update.message.reply_video_note(video_note=vn)
                elif message_type == 'audio':      # ← ДОБАВИТЬ
                    with open(path, 'rb') as au:
                        await update.message.reply_audio(audio=au, caption=f"Аудио из заявки #{ticket_id}")
            except Exception as e:
                await update.message.reply_text(f"❌ Ошибка отправки файла: {e}")
        else:
            await update.message.reply_text(f"Файл не найден: {attachment_path}")

# --- оценка поддержки ---
async def handle_feedback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    # Не принимать оценку, если её не запрашивали
    if not context.chat_data.get("awaiting_rating"):
        return
    txt = (update.message.text or "").strip()
    bot_config = load_bot_settings_config()
    allowed_values = rating_allowed_values(bot_config)
    if txt not in allowed_values:
        scale = rating_scale(bot_config)
        if scale:
            await update.message.reply_text(
                f"Пожалуйста, отправьте число от 1 до {scale}."
            )
        return
    user_id = update.effective_user.id
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            "INSERT INTO feedbacks (user_id, rating, timestamp) VALUES (?, ?, ?)",
            (user_id, int(txt), datetime.now().isoformat()),
        )
        conn.commit()
    context.chat_data["awaiting_rating"] = False
    response_text = rating_response_for(bot_config, txt)
    scale = rating_scale(bot_config)
    if response_text:
        try:
            response_text = response_text.format(value=txt, scale=scale)
        except Exception:
            pass
    else:
        response_text = f"Спасибо за вашу оценку {txt}!"
    await update.message.reply_text(response_text, reply_markup=ReplyKeyboardRemove())

def save_username_if_changed(conn, user_id: int, username: str):
    if not username:
        return
    row = conn.execute(
        "SELECT username FROM client_usernames WHERE user_id=? ORDER BY seen_at DESC LIMIT 1",
        (user_id,)
    ).fetchone()
    # Аккуратно достаём последнее значение вне зависимости от row_factory
    last = None
    if row:
        try:
            last = row['username']          # sqlite3.Row
        except Exception:
            last = row[0]                   # tuple

    if last != username:
        conn.execute(
            "INSERT OR IGNORE INTO client_usernames(user_id, username, seen_at) VALUES (?,?,?)",
            (user_id, username, datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00","Z"))
        )

def get_setting(channel_id: int, key: str, default: str = "") -> str:
    """Возвращает строковое значение настройки из БД, либо default."""
    try:
        with sqlite3.connect(DB_PATH) as conn:
            conn.row_factory = sqlite3.Row
            row = conn.execute(
                "SELECT value FROM app_settings WHERE channel_id=? AND key=?",
                (channel_id, key)
            ).fetchone()
            if row and row["value"] is not None:
                return str(row["value"])
    except Exception as e:
        logging.error(f"get_setting error: {e}")
    return default

def set_setting(channel_id: int, key: str, value: str):
    """Сохраняет/обновляет настройку в БД."""
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            """
            INSERT INTO app_settings(channel_id, key, value)
            VALUES(?,?,?)
            ON CONFLICT(channel_id, key) DO UPDATE SET value=excluded.value
            """,
            (channel_id, key, value),
        )
        conn.commit()

def get_rating_prompt_text(channel_id: int, ticket_id: str | None = None) -> str:
    """Шаблон запроса оценки. Поддерживает {ticket_id} в тексте."""
    # Значение можно редактировать на settings.html -> пишет в app_settings
    bot_config = load_bot_settings_config()
    scale = max(1, rating_scale(bot_config))
    base_prompt = rating_prompt(
        bot_config,
        default=f"Пожалуйста, оцените ответ поддержки по заявке #{{ticket_id}} от 1 до {{scale}}.",
    )
    try:
        default_text = base_prompt.format(scale=scale, ticket_id="{ticket_id}")
    except Exception:
        default_text = base_prompt
    txt = get_setting(channel_id, "rating_prompt_text", default_text)
    try:
        return txt.format(ticket_id=ticket_id or "", scale=scale)
    except Exception:
        # На случай некрректного шаблона — не уронить отправку
        return txt

def insert_phone_if_new(conn, user_id:int, phone:str, source:str, label:str=None, created_by:str='bot'):
    if not phone:
        return
    # нормализуем простейше
    phone_norm = ''.join(ch for ch in phone if ch.isdigit() or ch == '+')
    # не дублируем
    row = conn.execute(
        "SELECT 1 FROM client_phones WHERE user_id=? AND phone=? AND source=? AND is_active=1 LIMIT 1",
        (user_id, phone_norm, source)
    ).fetchone()
    if not row:
        conn.execute("""
            INSERT INTO client_phones(user_id, phone, label, source, is_active, created_at, created_by)
            VALUES (?,?,?,?,1,?,?)
        """, (user_id, phone_norm, label, source, datetime.datetime.utcnow().isoformat(), created_by))

# --- автоматическое закрытие ---
def auto_close_inactive():
    with sqlite3.connect(DB_PATH) as conn:
        hours = SETTINGS.get("auto_close_hours", 24)
        cutoff = datetime.now() - datetime.timedelta(hours=hours)
        inactive_tickets = conn.execute("""
            SELECT t.user_id, t.ticket_id, t.channel_id
            FROM tickets t
            WHERE t.status = 'pending'
              AND t.ticket_id NOT IN (
                SELECT DISTINCT ticket_id
                FROM chat_history
                WHERE timestamp > ?
              )
        """, (cutoff.isoformat(),)).fetchall()

        now_iso = datetime.now().isoformat()
        for user_id, ticket_id, channel_id in inactive_tickets:
            # 1) Закрываем тикет
            conn.execute(
                "UPDATE tickets SET status = 'resolved', resolved_at = ?, resolved_by = 'Авто-система' WHERE ticket_id = ?",
                (now_iso, ticket_id),
            )
            # 2) На всякий случай явно создаём «ожидание оценки» (если триггер уже сработал — INSERT OR IGNORE не создаст дубль)
            conn.execute("""
                INSERT OR IGNORE INTO pending_feedback_requests(
                    user_id, channel_id, ticket_id, source, created_at, expires_at
                ) VALUES(?, ?, ?, 'auto_close', ?, datetime('now', '+5 minutes'))
            """, (user_id, channel_id, ticket_id, now_iso))
            logging.info(f"Авто-закрытие тикета {ticket_id} пользователя {user_id}")

        conn.commit()

def get_questions_cfg(channel_id: int) -> dict:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    row = conn.execute("SELECT questions_cfg, max_questions FROM channels WHERE id = ?", (channel_id,)).fetchone()
    conn.close()
    if not row:
        return {"per_dialog_limit": 0, "questions": []}
    try:
        cfg = json.loads(row["questions_cfg"] or "{}")
    except:
        cfg = {}
    cfg.setdefault("per_dialog_limit", row["max_questions"] or 0)
    questions = cfg.setdefault("questions", [])
    try:
        questions.sort(key=lambda item: int(item.get("order") or 0))
    except Exception:
        pass
    for idx, item in enumerate(questions, start=1):
        item.setdefault("order", idx)
        item.setdefault("label", item.get("label") or item.get("question") or "")
    cfg.setdefault("feedback", {})
    fb = cfg["feedback"]
    bot_config = load_bot_settings_config()
    scale = max(1, rating_scale(bot_config))
    scale_hint = f"1–{scale}" if scale > 1 else "1"
    fb.setdefault("prompts", {
        "on_close": f"🌟 Пожалуйста, оцените нашу поддержку: отправьте цифру {scale_hint}.",
        "on_auto_close": f"Диалог закрыт по неактивности. Пожалуйста, оцените нашу поддержку: {scale_hint}."
    })
    fb.setdefault("auto_close_extra_text", "")
    cfg.setdefault("feedback", {})
    return cfg

def with_channel(handler):
    @wraps(handler)
    async def wrapper(update, context, *args, **kwargs):
        cid = get_channel_id_by_token(context.bot.token)
        # кладём в context.chat_data и context.user_data — как удобнее
        context.chat_data['channel_id'] = cid
        return await handler(update, context, *args, **kwargs)
    return wrapper

def get_channel_id_by_token(token: str) -> int:
    if token in _channel_cache:
        return _channel_cache[token]
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT id FROM channels WHERE token = ?", (token,))
    row = cur.fetchone()
    conn.close()
    if not row:
        raise RuntimeError("Token не найден в таблице channels")
    _channel_cache[token] = row[0]
    return _channel_cache[token]
 
def get_support_chat_id(channel_id: int):
    """Возвращает chat_id группы поддержки для канала.
       Приоритет: channels.support_chat_id -> SETTINGS.support_chat_id / group_chat_id -> None"""
    try:
        conn = sqlite3.connect(DB_PATH)
        conn.row_factory = sqlite3.Row
        # мягко проверим, есть ли колонка в базе
        cols = {r["name"] for r in conn.execute("PRAGMA table_info(channels)").fetchall()}
        chat_id = None
        if "support_chat_id" in cols:
            row = conn.execute("SELECT support_chat_id FROM channels WHERE id = ?", (channel_id,)).fetchone()
            if row and row["support_chat_id"]:
                try:
                    chat_id = int(row["support_chat_id"])
                except Exception:
                    chat_id = row["support_chat_id"]
        conn.close()
    except Exception:
        chat_id = None

    if not chat_id:
        chat_id = SETTINGS.get("support_chat_id") or SETTINGS.get("group_chat_id")
    return chat_id

def is_group_update(update: Update) -> bool:
    try:
        return update.effective_chat and update.effective_chat.type in ("group", "supergroup")
    except Exception:
        return False


# --- функции для запуска нескольких ботов ---
def iter_active_channels():
    """Генератор активных каналов из БД."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    rows = conn.execute("SELECT id, token FROM channels WHERE is_active = 1").fetchall()
    conn.close()
    return rows

async def run_all_bots():
    """Запускает всех активных ботов в ОДНОМ event loop без run_polling()."""
    def ensure_history_reply_columns():
        import sqlite3
        with sqlite3.connect(DB_PATH) as conn:
            cols = {r[1] for r in conn.execute("PRAGMA table_info(chat_history)")}
            if "tg_message_id" not in cols:
                conn.execute("ALTER TABLE chat_history ADD COLUMN tg_message_id INTEGER")
            if "reply_to_tg_id" not in cols:
                conn.execute("ALTER TABLE chat_history ADD COLUMN reply_to_tg_id INTEGER")
            conn.commit()

    apps = []
    active_channels = iter_active_channels()
    if not active_channels:
        print("❌ Нет активных каналов в базе данных.")
        return

    # 1) Собираем приложения и регистрируем хендлеры
    for row in active_channels:
        token = row["token"]
        channel_id = get_channel_id_by_token(token)

        application = ApplicationBuilder().token(token).build()
        
                # === Джоб для рассылки запросов оценки ===
        async def send_pending_feedback_requests(context: ContextTypes.DEFAULT_TYPE):
            try:
                now_iso = datetime.now().isoformat()
                with sqlite3.connect(DB_PATH) as conn:
                    conn.row_factory = sqlite3.Row
                    rows = conn.execute("""
                        SELECT id, user_id, ticket_id
                        FROM pending_feedback_requests
                        WHERE channel_id = ?
                          AND sent_at IS NULL
                          AND expires_at > ?
                        ORDER BY created_at ASC
                        LIMIT 50
                    """, (channel_id, now_iso)).fetchall()

                    for r in rows:
                        rid   = r["id"]
                        uid   = r["user_id"]
                        t_id  = r["ticket_id"]
                        # Текст из БД (с поддержкой {ticket_id})
                        prompt_text = get_rating_prompt_text(channel_id, t_id)
                        try:
                            await context.bot.send_message(chat_id=uid, text=prompt_text)
                            # фиксируем отправку и время — окно ожидания ограничено expires_at
                            conn.execute("UPDATE pending_feedback_requests SET sent_at = ? WHERE id = ?", (now_iso, rid))
                            conn.commit()
                        except Exception as e:
                            logging.error(f"send_pending_feedback_requests: send to {uid} failed: {e}")
            except Exception as e:
                logging.error(f"send_pending_feedback_requests error: {e}")

        # Запустим периодический опрос каждые 30 сек, старт через 5 сек
        application.job_queue.run_repeating(
            send_pending_feedback_requests,
            interval=30,
            first=5,
            name=f"send_feedback_{channel_id}"
        )

        # === регистрация хендлеров ===
                                # === FEEDBACK: 1..5 — принимаем только при активном запросе в БД (expires_at не истёк, sent_at не NULL) ===
        async def handle_feedback(update: Update, context: ContextTypes.DEFAULT_TYPE):
            try:
                user_id = update.effective_user.id
                raw = (update.message.text or "").strip()
                bot_config = load_bot_settings_config()
                allowed_values = rating_allowed_values(bot_config)
                if raw not in allowed_values:
                    scale = rating_scale(bot_config)
                    if scale:
                        await update.message.reply_text(
                            f"Пожалуйста, отправьте число от 1 до {scale}."
                        )
                    return
                rating = int(raw)

                # Проверяем, есть ли активный запрос на оценку (для этого канала и пользователя)
                now_iso = datetime.now().isoformat()
                channel_id = get_channel_id_by_token(context.bot.token)
                with sqlite3.connect(DB_PATH) as conn:
                    conn.row_factory = sqlite3.Row
                    row = conn.execute("""
                        SELECT id, ticket_id
                          FROM pending_feedback_requests
                         WHERE user_id = ?
                           AND channel_id = ?
                           AND sent_at IS NOT NULL
                           AND expires_at > ?
                         ORDER BY created_at DESC
                         LIMIT 1
                    """, (user_id, channel_id, now_iso)).fetchone()

                    if not row:
                        # Нет активного окна ожидания — игнорим (ничего не ломаем)
                        return

                    # Сохраняем оценку (минимально — как было)
                    conn.execute(
                        "INSERT INTO feedbacks (user_id, rating, timestamp) VALUES (?, ?, ?)",
                        (user_id, rating, now_iso),
                    )
                    # Закрываем запрос на оценку (убираем из ожидания)
                    conn.execute("DELETE FROM pending_feedback_requests WHERE id = ?", (row["id"],))
                    conn.commit()

                # Опциональные дополнительные вопросы — как у вас было (если конфиг включит)
                cfg = get_questions_cfg(channel_id)
                fb_cfg = (cfg.get("feedback") or {})

                # 4) Если в cfg.feedback есть post_questions, то отправим их
                extra_qs = fb_cfg.get("post_questions") or []
                if isinstance(extra_qs, list) and extra_qs:
                    lines = []
                    for i, q in enumerate(extra_qs, 1):
                        label = str(q).strip()
                        if not label:
                            continue
                        lines.append(f"{i}. {label}")
                    if lines:
                        await update.message.reply_text(
                            "Пара уточняющих вопросов:\n" + "\n".join(lines)
                        )

                response_text = rating_response_for(bot_config, rating)
                scale = rating_scale(bot_config)
                formatted_response: str | None = None
                if response_text:
                    try:
                        formatted_response = response_text.format(value=rating, scale=scale)
                    except Exception:
                        formatted_response = response_text

                if formatted_response:
                    await update.message.reply_text(formatted_response, reply_markup=ReplyKeyboardRemove())
                elif not fb_cfg.get("disable_default_thanks"):
                    await update.message.reply_text("Спасибо за оценку! 🙏", reply_markup=ReplyKeyboardRemove())

            except Exception as e:
                logging.error(f"handle_feedback error: {e}")

        # Регистрируем ОДИН раз, только в приватных чатах и ПЕРЕД conv_handler
        application.add_handler(MessageHandler(filters.Regex(r'^\d+$') & filters.ChatType.PRIVATE, handle_feedback))

        conv_handler = ConversationHandler(
            entry_points=[
                CommandHandler("start", start),
                CommandHandler("tickets", tickets_command),
            ],
            states={
                PREV_STEP: [MessageHandler(filters.TEXT & ~filters.COMMAND, previous_choice_decision)],
                BUSINESS: [MessageHandler(filters.TEXT & ~filters.COMMAND, business_choice)],
                LOCATION_TYPE: [MessageHandler(filters.TEXT & ~filters.COMMAND, location_type_choice)],
                CITY: [MessageHandler(filters.TEXT & ~filters.COMMAND, city_choice)],
                LOCATION_NAME: [MessageHandler(filters.TEXT & ~filters.COMMAND, location_name_choice)],
                PROBLEM: [
                    MessageHandler(filters.TEXT & ~filters.COMMAND, problem_description),
                    MessageHandler(MEDIA_FILTERS, save_user_media),
                    MessageHandler(filters.CONTACT, save_user_contact),
                ],
            },
            fallbacks=[CommandHandler("cancel", cancel),],
            allow_reentry=True,
        )
        application.add_handler(conv_handler)
        application.add_handler(MessageHandler(filters.REPLY & filters.ChatType.GROUPS, reply_to_user))
        application.add_handler(CommandHandler("stats", stats))
        application.add_handler(CommandHandler("pending", pending))
        application.add_handler(CommandHandler("my", my_tickets))
        application.add_handler(MessageHandler(filters.TEXT& ~filters.COMMAND& ~filters.Regex(r'^\d+$')& filters.ChatType.PRIVATE, save_user_message))
        application.add_handler(MessageHandler(MEDIA_FILTERS & filters.ChatType.PRIVATE, save_user_media,))
        application.add_handler(CommandHandler("media", show_media))
        application.add_handler(MessageHandler(filters.CONTACT & filters.ChatType.PRIVATE, save_user_contact))

        # Передаём данные канала в контекст приложения
        application.bot_data["channel_id"] = channel_id
        application.bot_data["token"] = token

        apps.append(application)
        logging.info(f"✅ Бот для канала ID={channel_id} подготовлен")

    # 2) Инициализация, старт и запуск polling'а ДЛЯ КАЖДОГО бота
    for app in apps:
        await app.initialize()
    for app in apps:
        await app.start()
    for app in apps:
        # updater создаётся на initialize(); запускаем polling в общем loop
        await app.updater.start_polling()

        logging.info("🚀 Все боты запущены. Ожидание событий...")

    # 3) Ждём Ctrl+C/SIGTERM без stacktrace
    stop_event = asyncio.Event()
    loop = asyncio.get_running_loop()
    try:
        # На Unix — ловим сигналы, на Windows может не поддерживаться (будет NotImplementedError)
        loop.add_signal_handler(signal.SIGINT, stop_event.set)
        loop.add_signal_handler(signal.SIGTERM, stop_event.set)
    except (NotImplementedError, RuntimeError):
        pass

    try:
        await stop_event.wait()
    except asyncio.CancelledError:
        # подавляем отмену, чтобы asyncio.run(...) не печатал трассу
        pass
    finally:
        # 4) Корректная остановка всех ботов
        for app in apps:
            await app.updater.stop()
        for app in apps:
            await app.stop()
        for app in apps:
            await app.shutdown()
        logging.info("🛑 Все боты остановлены.")

# --- точка входа ---
if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
    try:
        # На Windows безопаснее selector policy (без этого add_signal_handler может не работать)
        if sys.platform.startswith("win"):
            try:
                asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())  # type: ignore[attr-defined]
            except Exception:
                pass
        asyncio.run(run_all_bots())
    except KeyboardInterrupt:
        # гасим финальный traceback от asyncio.run
        pass
