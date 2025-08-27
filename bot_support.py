# bot_support.py
import logging
import sqlite3
import datetime
import json
import os
import uuid
from telegram import Update, ReplyKeyboardMarkup, ReplyKeyboardRemove
from telegram.ext import (
    ApplicationBuilder,
    CommandHandler,
    MessageHandler,
    filters,
    ContextTypes,
    ConversationHandler,
)
from apscheduler.schedulers.background import BackgroundScheduler
from threading import Timer

# === Отладка: где запускаемся ===
print("📁 Рабочая папка:", os.getcwd())
print("📄 Файлы в папке:", os.listdir(os.getcwd()))

# === НАСТРОЙКИ ===
from config import TELEGRAM_BOT_TOKEN, DB_PATH, GROUP_CHAT_ID
TOKEN = TELEGRAM_BOT_TOKEN 

# === БАЗА ДАННЫХ ===
def init_db():
    with sqlite3.connect(DB_PATH) as conn:  # ← Используем единый путь
        conn.execute("""
            CREATE TABLE IF NOT EXISTS tickets (
                user_id INTEGER,
                group_msg_id INTEGER,
                status TEXT DEFAULT 'pending',
                resolved_at TEXT,
                resolved_by TEXT,
                ticket_id TEXT UNIQUE,
                PRIMARY KEY (user_id, ticket_id)
            )
        """)

# Состояния
BUSINESS, LOCATION_TYPE, CITY, LOCATION_NAME, PROBLEM = range(5)
PREV = -1  # Состояние "Назад"

# Варианты
BUSINESS_OPTIONS = ["СушиВесла", "БлинБери"]
LOCATION_TYPE_OPTIONS = ["Корпоративная сеть", "Партнёры-франчайзи"]

# === СТРУКТУРА ЛОКАЦИЙ ===
def load_locations():
    try:
        with open("locations.json", "r", encoding="utf-8") as f:
            data = json.load(f)
            print("✅ JSON загружен. Бизнесы:", list(data.keys()))
            return data
    except FileNotFoundError:
        print("❌ locations.json не найден!")
        return {}
    except json.JSONDecodeError as e:
        print(f"❌ Ошибка JSON: {e}")
        return {}
    except Exception as e:
        print(f"❌ Ошибка: {e}")
        return {}

LOCATIONS = load_locations()

# === Вспомогательная функция: клавиатура с "Назад" и "Отмена" ===
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

# === БАЗА ДАННЫХ ===
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
                PRIMARY KEY (user_id, ticket_id)
            )
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
                created_time TEXT
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS chat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                sender TEXT,
                message TEXT,
                timestamp TEXT,
                ticket_id TEXT
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
                updated_at TEXT,
                updated_by TEXT
            )
        """)

init_db()

# === ЛОГИРОВАНИЕ ===
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# === ОБРАБОТЧИКИ ===

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data.clear()
    user = update.effective_user
    await update.message.reply_text(
        f"Привет, {user.first_name}! 🌟\n"
        "Начнём заполнять заявку."
    )
    await update.message.reply_text(
        "1️⃣ Выберите бизнес:",
        reply_markup=get_keyboard_with_back(BUSINESS_OPTIONS, has_back=False)
    )
    return BUSINESS

# === ШАГ 1: Выбор бизнеса ===
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
    await update.message.reply_text(
        "2️⃣ Тип локации:",
        reply_markup=get_keyboard_with_back(LOCATION_TYPE_OPTIONS)
    )
    return LOCATION_TYPE

# === ШАГ 2: Выбор типа локации ===
async def location_type_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text

    if text == "◀️ Назад":
        await update.message.reply_text(
            "1️⃣ Выберите бизнес:",
            reply_markup=get_keyboard_with_back(BUSINESS_OPTIONS)
        )
        return BUSINESS

    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END

    if text not in LOCATION_TYPE_OPTIONS:
        await update.message.reply_text("Выберите тип:", reply_markup=get_keyboard_with_back(LOCATION_TYPE_OPTIONS, has_back=False))
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

# === ШАГ 3: Выбор города ===
async def city_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text

    if text == "◀️ Назад":
        await update.message.reply_text(
            "2️⃣ Тип локации:",
            reply_markup=get_keyboard_with_back(LOCATION_TYPE_OPTIONS)
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

# === ШАГ 4: Выбор локации ===
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

# === ШАГ 5: Описание проблемы ===
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

    # 🕒 Получаем текущее время
    now = datetime.datetime.now()
    created_at = now.isoformat()
    created_date = now.strftime('%Y-%m-%d')
    created_time = now.strftime('%H:%M')

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
        sent = await context.bot.send_message(
            chat_id=GROUP_CHAT_ID,
            text=full_message,
            parse_mode='HTML'
        )

        with sqlite3.connect(DB_PATH) as conn:
            conn.execute(
                "INSERT INTO tickets (user_id, group_msg_id, status, ticket_id) VALUES (?, ?, ?, ?)",
                (user.id, sent.message_id, "pending", ticket_id)
            )
            conn.execute(
                "INSERT INTO messages (group_msg_id, user_id, business, location_type, city, location_name, problem, created_at, username, ticket_id, created_date, created_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (sent.message_id, user.id, context.user_data['business'], context.user_data['location_type'],
                 context.user_data['city'], context.user_data['location_name'], context.user_data['problem'],
                 created_at, username, ticket_id, created_date, created_time)
            )
            conn.execute(
                "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id) VALUES (?, ?, ?, ?, ?)",
                (user.id, "user", context.user_data['problem'], created_at, ticket_id)
            )
            conn.commit()

        await update.message.reply_text("✅ Заявка отправлена. Спасибо!", reply_markup=ReplyKeyboardRemove())

    except Exception as e:
        await update.message.reply_text(f"❌ Ошибка: {e}")
        logging.error(f"Ошибка отправки: {e}")

    finally:
        context.user_data.clear()

    return ConversationHandler.END

# === ОТВЕТ АДМИНА ===
async def reply_to_user(update: Update, context: ContextTypes.DEFAULT_TYPE):
    message = update.message
    if not message.reply_to_message:
        return
    replied = message.reply_to_message
    if replied.from_user.id != context.bot.id:
        return

    import re
    match = re.search(r"ID: <code>(\d+)</code>", replied.text)
    if not match:
        return
    user_id = int(match.group(1))

    admin = update.effective_user
    admin_name = f"@{admin.username}" if admin.username else admin.first_name
    reply_text = f"📩 <b>Ответ от поддержки ({admin_name}):</b>\n\n{message.text}"

    try:
        await context.bot.send_message(chat_id=user_id, text=reply_text, parse_mode='HTML')

        with sqlite3.connect(DB_PATH) as conn:
            ticket_id = conn.execute(
                "SELECT ticket_id FROM tickets WHERE user_id = ? ORDER BY ROWID DESC LIMIT 1",
                (user_id,)
            ).fetchone()
            ticket_id = ticket_id[0] if ticket_id else "unknown"

            conn.execute(
                "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id) VALUES (?, ?, ?, ?, ?)",
                (user_id, "support", message.text, datetime.datetime.now().isoformat(), ticket_id)
            )
            conn.commit()

        await message.reply_text("✅ Ответ отправлен. Диалог продолжается.")

    except Exception as e:
        await message.reply_text(f"❌ Ошибка: {e}")

# === КОМАНДЫ ===

async def stats(update: Update, context: ContextTypes.DEFAULT_TYPE):
    with sqlite3.connect(DB_PATH) as conn:
        total = conn.execute("SELECT COUNT(*) FROM tickets").fetchone()[0]
        resolved = conn.execute("SELECT COUNT(*) FROM tickets WHERE status = 'resolved'").fetchone()[0]
        pending = total - resolved
    await update.message.reply_text(
        f"📊 <b>Статистика</b>\n\n"
        f"📬 Всего: <b>{total}</b>\n"
        f"✅ Решено: <b>{resolved}</b>\n"
        f"⏳ В обработке: <b>{pending}</b>",
        parse_mode='HTML'
    )

async def pending(update: Update, context: ContextTypes.DEFAULT_TYPE):
    with sqlite3.connect(DB_PATH) as conn:
        rows = conn.execute("SELECT user_id FROM tickets WHERE status = 'pending'").fetchall()
    if not rows:
        await update.message.reply_text("✅ Нет заявок в обработке.")
        return
    text = "📬 <b>Заявки в обработке:</b>\n\n"
    for (user_id,) in rows:
        text += f"• Пользователь <code>{user_id}</code>\n"
    await update.message.reply_text(text, parse_mode='HTML')

async def my_tickets(update: Update, context: ContextTypes.DEFAULT_TYPE):
    admin = update.effective_user
    admin_name = f"@{admin.username}" if admin.username else admin.first_name
    with sqlite3.connect(DB_PATH) as conn:
        rows = conn.execute("SELECT user_id FROM tickets WHERE resolved_by = ?", (admin_name,)).fetchall()
    count = len(rows)
    await update.message.reply_text(
        f"🛠 Вы закрыли <b>{count}</b> заявок.",
        parse_mode='HTML'
    )

# === ОБРАБОТЧИК: все входящие сообщения от пользователя ===
async def save_user_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    message = update.message.text

    with sqlite3.connect(DB_PATH) as conn:
        row = conn.execute("SELECT user_id FROM tickets WHERE user_id = ?", (user.id,)).fetchone()

    if not row:
        return

    try:
        with sqlite3.connect(DB_PATH) as conn:
            conn.execute(
                "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id) VALUES (?, ?, ?, ?, ?)",
                (user.id, "user", message, datetime.datetime.now().isoformat(), "media")
            )
            conn.commit()
        logging.info(f"Сообщение от {user.id} сохранено в историю")
    except Exception as e:
        logging.error(f"Ошибка сохранения сообщения: {e}")

# === Оценка поддержки ===
async def handle_feedback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    rating = update.message.text
    if rating in ["1", "2", "3", "4", "5"]:
        with sqlite3.connect(DB_PATH) as conn:
            conn.execute("INSERT INTO feedbacks (user_id, rating, timestamp) VALUES (?, ?, ?)",
                         (user_id, int(rating), datetime.datetime.now().isoformat()))
            conn.commit()
        await update.message.reply_text("Спасибо за оценку! 🙏", reply_markup=ReplyKeyboardRemove())

# === Автоматическое закрытие ===
def auto_close_inactive():
    with sqlite3.connect("tickets.db") as conn:
        try:
            with open("settings.json", "r", encoding="utf-8") as f:
                settings = json.load(f)
            hours = settings.get("auto_close_hours", 24)
        except:
            hours = 24

        cutoff = datetime.datetime.now() - datetime.timedelta(hours=hours)
        rows = conn.execute("""
            SELECT DISTINCT ch.user_id FROM chat_history ch
            WHERE ch.timestamp < ? AND ch.user_id IN (SELECT user_id FROM tickets WHERE status = 'pending')
        """, (cutoff.isoformat(),))
        for (user_id,) in rows:
            conn.execute(
                "UPDATE tickets SET status = 'resolved', resolved_at = ?, resolved_by = 'Авто-система' WHERE user_id = ?",
                (datetime.datetime.now().isoformat(), user_id)
            )
            logging.info(f"Авто-закрытие: {user_id}")
        conn.commit()

# === ЗАПУСК ===
if __name__ == '__main__':
    app = ApplicationBuilder().token(TOKEN).build()

    conv_handler = ConversationHandler(
        entry_points=[CommandHandler('start', start)],
        states={
            BUSINESS: [MessageHandler(filters.TEXT & ~filters.COMMAND, business_choice)],
            LOCATION_TYPE: [MessageHandler(filters.TEXT & ~filters.COMMAND, location_type_choice)],
            CITY: [MessageHandler(filters.TEXT & ~filters.COMMAND, city_choice)],
            LOCATION_NAME: [MessageHandler(filters.TEXT & ~filters.COMMAND, location_name_choice)],
            PROBLEM: [MessageHandler(filters.TEXT & ~filters.COMMAND, problem_description)],
        },
        fallbacks=[CommandHandler('start', start)]
    )

    app.add_handler(conv_handler)
    app.add_handler(MessageHandler(filters.REPLY & filters.Chat(GROUP_CHAT_ID), reply_to_user))
    app.add_handler(CommandHandler('stats', stats))
    app.add_handler(CommandHandler('pending', pending))
    app.add_handler(CommandHandler('my', my_tickets))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND & filters.ChatType.PRIVATE, save_user_message))
    app.add_handler(MessageHandler(filters.Regex("^[1-5]$") & filters.ChatType.PRIVATE, handle_feedback))

    logging.info("✅ Бот запущен с системой статусов и фильтрами")

    # Запуск планировщика
    scheduler = BackgroundScheduler()
    scheduler.add_job(auto_close_inactive, 'interval', minutes=30)

    def reload_locations():
        global LOCATIONS
        try:
            mtime = os.path.getmtime("locations.json")
            if hasattr(reload_locations, "last_mtime"):
                if mtime > reload_locations.last_mtime:
                    LOCATIONS = load_locations()
                    logging.info("✅ Структура локаций перезагружена")
            reload_locations.last_mtime = mtime
        except Exception as e:
            logging.error(f"Ошибка перезагрузки locations.json: {e}")

    scheduler.add_job(reload_locations, 'interval', seconds=10)
    scheduler.start()

    app.run_polling()