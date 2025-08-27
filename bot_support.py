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

from config import TELEGRAM_BOT_TOKEN, DB_PATH, GROUP_CHAT_ID, load_settings
TOKEN = TELEGRAM_BOT_TOKEN

ATTACHMENTS_DIR = "attachments"
os.makedirs(ATTACHMENTS_DIR, exist_ok=True)

# --- загрузка структуры локаций ---
def load_locations():
    try:
        with open("locations.json", "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        logging.error(f"Ошибка загрузки locations.json: {e}")
        return {}

LOCATIONS = load_locations()
BUSINESS_OPTIONS = list(LOCATIONS.keys())
SETTINGS = load_settings()

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
                ticket_id TEXT,
                message_type TEXT,
                attachment TEXT
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
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")

# --- состояния ---
BUSINESS, LOCATION_TYPE, CITY, LOCATION_NAME, PROBLEM = range(5)

# --- /start ---
async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    context.user_data.clear()
    user = update.effective_user

    with sqlite3.connect(DB_PATH) as conn:
        active = conn.execute(
            "SELECT 1 FROM tickets WHERE user_id = ? AND status = 'pending'",
            (user.id,),
        ).fetchone()
    if active:
        await update.message.reply_text("У вас уже есть активная заявка. Ожидайте ответа поддержки.")
        return ConversationHandler.END

    await update.message.reply_text(f"Привет, {user.first_name}! 🌟\nНачнём заполнять заявку.")
    await update.message.reply_text(
        "1️⃣ Выберите бизнес:",
        reply_markup=get_keyboard_with_back(BUSINESS_OPTIONS, has_back=False),
    )
    return BUSINESS

# --- выбор бизнеса ---
async def business_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text

    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END

    if text not in BUSINESS_OPTIONS:
        await update.message.reply_text(
            "Выберите бизнес:",
            reply_markup=get_keyboard_with_back(BUSINESS_OPTIONS, has_back=False),
        )
        return BUSINESS

    context.user_data["business"] = text
    location_type_options = list(LOCATIONS[text].keys())
    context.user_data["location_type_options"] = location_type_options
    await update.message.reply_text(
        "2️⃣ Тип локации:",
        reply_markup=get_keyboard_with_back(location_type_options),
    )
    return LOCATION_TYPE
    
# --- создание тикета ---
async def create_ticket(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    ticket_id = str(uuid.uuid4())  # Генерация уникального ticket_id

    # Записываем в базу данных
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute("INSERT INTO tickets (user_id, ticket_id, status) VALUES (?, ?, ?)", 
                     (user.id, ticket_id, 'pending'))

    # Отправляем клиенту уникальный ID
    await update.message.reply_text(f"✅ Ваша заявка создана! Ваш ID заявки: {ticket_id}")

# --- проверка старых заявок ---
async def check_existing_tickets(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user

    with sqlite3.connect(DB_PATH) as conn:
        closed_tickets = conn.execute(
            "SELECT ticket_id, status, subject FROM tickets WHERE user_id = ? AND status = 'closed'", 
            (user.id,)
        ).fetchall()

    if closed_tickets:
        # Если есть закрытые заявки
        ticket_list = "\n".join([f"ID: {ticket[0]} - Тема: {ticket[2]}" for ticket in closed_tickets])
        await update.message.reply_text(f"У вас есть закрытые заявки:\n{ticket_list}\n\nХотите продолжить работу с ними или создать новую заявку?")
        return

    # Если закрытых заявок нет, продолжаем создание новой заявки
    await update.message.reply_text("Начинаем создание новой заявки.")
    return BUSINESS

# --- выбор типа локации ---
async def location_type_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text
    location_type_options = context.user_data.get("location_type_options", [])

    if text == "◀️ Назад":
        await update.message.reply_text(
            "1️⃣ Выберите бизнес:",
            reply_markup=get_keyboard_with_back(BUSINESS_OPTIONS, has_back=False),
        )
        return BUSINESS

    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END

    if text not in location_type_options:
        await update.message.reply_text(
            "Выберите тип:",
            reply_markup=get_keyboard_with_back(location_type_options, has_back=False),
        )
        return LOCATION_TYPE

    context.user_data["location_type"] = text
    business = context.user_data["business"]
    cities = list(LOCATIONS[business][text].keys())
    context.user_data["city_options"] = cities
    await update.message.reply_text(
        "3️⃣ Выберите город:",
        reply_markup=get_keyboard_with_back(cities),
    )
    return CITY

# --- выбор города ---
async def city_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text
    city_options = context.user_data.get("city_options", [])
    business = context.user_data["business"]
    loc_type = context.user_data["location_type"]

    if text == "◀️ Назад":
        location_type_options = context.user_data.get("location_type_options", [])
        await update.message.reply_text(
            "2️⃣ Тип локации:",
            reply_markup=get_keyboard_with_back(location_type_options),
        )
        return LOCATION_TYPE

    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END

    if text not in city_options:
        await update.message.reply_text(
            "Выберите город:",
            reply_markup=get_keyboard_with_back(city_options, has_back=False),
        )
        return CITY

    context.user_data["city"] = text
    locations = LOCATIONS[business][loc_type][text]
    context.user_data["location_options"] = locations
    await update.message.reply_text(
        "4️⃣ Выберите локацию:",
        reply_markup=get_keyboard_with_back(locations),
    )
    return LOCATION_NAME

# --- выбор локации ---
async def location_name_choice(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text
    locations = context.user_data.get("location_options", [])

    if text == "◀️ Назад":
        cities = context.user_data.get("city_options", [])
        await update.message.reply_text(
            "3️⃣ Выберите город:",
            reply_markup=get_keyboard_with_back(cities),
        )
        return CITY

    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END

    if text not in locations:
        await update.message.reply_text(
            "Выберите локацию из списка.",
            reply_markup=get_keyboard_with_back(locations, has_back=False),
        )
        return LOCATION_NAME

    context.user_data["location_name"] = text
    await update.message.reply_text(
        "5️⃣ Опишите проблему:",
        reply_markup=get_keyboard_with_back([], has_back=True, has_cancel=True),
    )
    return PROBLEM

# --- описание проблемы и создание тикета ---
async def problem_description(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text

    if text == "◀️ Назад":
        locations = context.user_data.get("location_options", [])
        await update.message.reply_text(
            "4️⃣ Выберите локацию:",
            reply_markup=get_keyboard_with_back(locations),
        )
        return LOCATION_NAME

    if text == "🚫 Отмена":
        await update.message.reply_text("❌ Заявка отменена.", reply_markup=ReplyKeyboardRemove())
        context.user_data.clear()
        return ConversationHandler.END

    if context.user_data.get("in_progress"):
        await update.message.reply_text("❌ Вы уже отправляете заявку.")
        return ConversationHandler.END

    context.user_data["in_progress"] = True
    context.user_data["problem"] = text
    user = update.effective_user
    username = user.username or ""
    ticket_id = str(uuid.uuid4())[:8]
    context.user_data["ticket_id"] = ticket_id

    now = datetime.datetime.now()
    created_at = now.isoformat()
    created_date = now.strftime("%Y-%m-%d")
    created_time = now.strftime("%H:%M")

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
            chat_id=GROUP_CHAT_ID, text=full_message, parse_mode="HTML"
        )
        with sqlite3.connect(DB_PATH) as conn:
            conn.execute(
                "INSERT INTO tickets (user_id, group_msg_id, status, ticket_id) VALUES (?, ?, ?, ?)",
                (user.id, sent.message_id, "pending", ticket_id),
            )
            conn.execute(
                "INSERT INTO messages (group_msg_id, user_id, business, location_type, city, location_name, problem, created_at, username, ticket_id, created_date, created_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    sent.message_id,
                    user.id,
                    context.user_data["business"],
                    context.user_data["location_type"],
                    context.user_data["city"],
                    context.user_data["location_name"],
                    context.user_data["problem"],
                    created_at,
                    username,
                    ticket_id,
                    created_date,
                    created_time,
                ),
            )
            conn.execute(
                "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id, message_type, attachment) VALUES (?, ?, ?, ?, ?, ?, ?)",
                (user.id, "user", context.user_data["problem"], created_at, ticket_id, "text", None),
            )
            conn.commit()
        await update.message.reply_text("✅ Заявка отправлена. Спасибо!", reply_markup=ReplyKeyboardRemove())
    except Exception as e:
        await update.message.reply_text(f"❌ Ошибка: {e}")
        logging.error(f"Ошибка отправки: {e}")
    finally:
        context.user_data.clear()
    return ConversationHandler.END

# --- ответ администратора ---
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
        await context.bot.send_message(chat_id=user_id, text=reply_text, parse_mode="HTML")
        with sqlite3.connect(DB_PATH) as conn:
            ticket_id = conn.execute(
                "SELECT ticket_id FROM tickets WHERE user_id = ? ORDER BY ROWID DESC LIMIT 1",
                (user_id,),
            ).fetchone()
            ticket_id = ticket_id[0] if ticket_id else "unknown"
            conn.execute(
                "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id, message_type, attachment) VALUES (?, ?, ?, ?, ?, ?, ?)",
                (user_id, "support", message.text, datetime.datetime.now().isoformat(), ticket_id, "text", None),
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
        pending = total - resolved
    await update.message.reply_text(
        f"📊 <b>Статистика</b>\n\n"
        f"📬 Всего: <b>{total}</b>\n"
        f"✅ Решено: <b>{resolved}</b>\n"
        f"⏳ В обработке: <b>{pending}</b>",
        parse_mode="HTML",
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
    await update.message.reply_text(text, parse_mode="HTML")

async def my_tickets(update: Update, context: ContextTypes.DEFAULT_TYPE):
    admin = update.effective_user
    admin_name = f"@{admin.username}" if admin.username else admin.first_name
    with sqlite3.connect(DB_PATH) as conn:
        rows = conn.execute("SELECT user_id FROM tickets WHERE resolved_by = ?", (admin_name,)).fetchall()
    count = len(rows)
    await update.message.reply_text(
        f"🛠 Вы закрыли <b>{count}</b> заявок.",
        parse_mode="HTML",
    )

# --- сохранение текста пользователя ---
async def save_user_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    message = update.message.text

    with sqlite3.connect(DB_PATH) as conn:
        row = conn.execute(
            "SELECT ticket_id FROM tickets WHERE user_id = ? ORDER BY ROWID DESC LIMIT 1",
            (user.id,),
        ).fetchone()

    if not row:
        return

    ticket_id = row[0]
    try:
        with sqlite3.connect(DB_PATH) as conn:
            conn.execute(
                "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id, message_type, attachment) VALUES (?, ?, ?, ?, ?, ?, ?)",
                (user.id, "user", message, datetime.datetime.now().isoformat(), ticket_id, "text", None),
            )
            conn.commit()
        logging.info(f"Сообщение от {user.id} сохранено в историю")
    except Exception as e:
        logging.error(f"Ошибка сохранения сообщения: {e}")

# --- сохранение медиа ---
async def save_user_media(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    message = update.message

    with sqlite3.connect(DB_PATH) as conn:
        row = conn.execute(
            "SELECT ticket_id FROM tickets WHERE user_id = ? ORDER BY ROWID DESC LIMIT 1",
            (user.id,),
        ).fetchone()

    if not row:
        return

    ticket_id = row[0]
    timestamp = datetime.datetime.now().isoformat()
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
        elif message.document:
            message_type = "document"
            file = await message.document.get_file()
            filename = message.document.file_name or f"{file.file_id}"
            attachment_path = os.path.join(ATTACHMENTS_DIR, ticket_id, filename)
            await file.download_to_drive(attachment_path)
        else:
            return

        with sqlite3.connect(DB_PATH) as conn:
            conn.execute(
                "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id, message_type, attachment) VALUES (?, ?, ?, ?, ?, ?, ?)",
                (user.id, "user", text, timestamp, ticket_id, message_type, attachment_path),
            )
            conn.commit()
        logging.info(f"Медиа от {user.id} сохранено")
    except Exception as e:
        logging.error(f"Ошибка сохранения медиа: {e}")

# --- оценка поддержки ---
async def handle_feedback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    rating = update.message.text
    if rating in ["1", "2", "3", "4", "5"]:
        with sqlite3.connect(DB_PATH) as conn:
            conn.execute(
                "INSERT INTO feedbacks (user_id, rating, timestamp) VALUES (?, ?, ?)",
                (user_id, int(rating), datetime.datetime.now().isoformat()),
            )
            conn.commit()
        await update.message.reply_text("Спасибо за оценку! 🙏", reply_markup=ReplyKeyboardRemove())

# --- автоматическое закрытие ---
def auto_close_inactive():
    
    with sqlite3.connect(DB_PATH) as conn:
        hours = SETTINGS.get("auto_close_hours", 24)
        cutoff = datetime.datetime.now() - datetime.timedelta(hours=hours)
        rows = conn.execute("""
            SELECT DISTINCT ch.user_id FROM chat_history ch
            WHERE ch.timestamp < ? AND ch.user_id IN (SELECT user_id FROM tickets WHERE status = 'pending')
        """, (cutoff.isoformat(),))
        for (user_id,) in rows:
            conn.execute(
                "UPDATE tickets SET status = 'resolved', resolved_at = ?, resolved_by = 'Авто-система' WHERE user_id = ?",
                (datetime.datetime.now().isoformat(), user_id),
            )
            logging.info(f"Авто-закрытие: {user_id}")
        conn.commit()

# --- обработка вложений ---
MAX_FILE_SIZE = 20 * 1024 * 1024  # 20 MB

async def handle_attachment(update: Update, context: ContextTypes.DEFAULT_TYPE):
    file = update.message.document or update.message.photo[-1]  # Получаем файл
    file_id = file.file_id
    file_size = file.file_size

    # Если размер файла больше 20 МБ
    if file_size > MAX_FILE_SIZE:
        await update.message.reply_text("⚠️ Размер файла превышает допустимый лимит (20 MB). Попробуйте отправить файл меньшего размера.")
        return

    # Загружаем файл, если размер в пределах нормы
    file_path = await file.get_file()
    file_path.download(f"{ATTACHMENTS_DIR}/{file_id}.jpg")  # Сохраняем файл
    await update.message.reply_text("✅ Файл успешно загружен!")

# --- запуск приложения ---
if __name__ == "__main__":
    app = ApplicationBuilder().token(TOKEN).build()

    conv_handler = ConversationHandler(
        entry_points=[CommandHandler("start", start)],
        states={
            BUSINESS: [MessageHandler(filters.TEXT & ~filters.COMMAND, business_choice)],
            LOCATION_TYPE: [MessageHandler(filters.TEXT & ~filters.COMMAND, location_type_choice)],
            CITY: [MessageHandler(filters.TEXT & ~filters.COMMAND, city_choice)],
            LOCATION_NAME: [MessageHandler(filters.TEXT & ~filters.COMMAND, location_name_choice)],
            PROBLEM: [MessageHandler(filters.TEXT & ~filters.COMMAND, problem_description)],
        },
        fallbacks=[CommandHandler("start", start)],
    )

    app.add_handler(conv_handler)
    app.add_handler(MessageHandler(filters.REPLY & filters.Chat(GROUP_CHAT_ID), reply_to_user))
    app.add_handler(CommandHandler("stats", stats))
    app.add_handler(CommandHandler("pending", pending))
    app.add_handler(CommandHandler("my", my_tickets))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND & filters.ChatType.PRIVATE, save_user_message))
    app.add_handler(MessageHandler(
        (filters.PHOTO | filters.VOICE | filters.VIDEO | filters.Document.ALL) & filters.ChatType.PRIVATE,
        save_user_media,
    ))
    app.add_handler(MessageHandler(filters.Regex("^[1-5]$") & filters.ChatType.PRIVATE, handle_feedback))
    
    app.add_handler(MessageHandler(
    (filters.PHOTO | filters.VOICE | filters.VIDEO | filters.Document.ALL) & filters.ChatType.PRIVATE,
    save_user_media,
))

    logging.info("✅ Бот запущен с системой статусов и фильтрами")

    scheduler = BackgroundScheduler()
    
    scheduler.add_job(auto_close_inactive, "interval", minutes=30)

    def reload_locations():
        global LOCATIONS, BUSINESS_OPTIONS
        try:
            mtime = os.path.getmtime("locations.json")
            if hasattr(reload_locations, "last_mtime"):
                if mtime > reload_locations.last_mtime:
                    LOCATIONS = load_locations()
                    BUSINESS_OPTIONS = list(LOCATIONS.keys())
                    logging.info("✅ Структура локаций перезагружена")
            reload_locations.last_mtime = mtime
        
        except Exception as e:
            logging.error(f"Ошибка перезагрузки locations.json: {e}")

    def reload_settings():
        global SETTINGS
        try:
            mtime = os.path.getmtime("settings.json")
            if hasattr(reload_settings, "last_mtime"):
                if mtime > reload_settings.last_mtime:
                    SETTINGS = load_settings()
                    logging.info("✅ Настройки перезагружены")
            reload_settings.last_mtime = mtime
        except Exception as e:
            logging.error(f"Ошибка перезагрузки settings.json: {e}")

    scheduler.add_job(reload_locations, "interval", seconds=10)
    scheduler.add_job(reload_settings, "interval", seconds=10)
    scheduler.start()

    app.run_polling()
