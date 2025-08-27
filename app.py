# panel/app.py
from flask import Flask, render_template, request, jsonify, redirect, url_for, session
import sqlite3
import datetime
import requests
import json
import os
import logging
from apscheduler.schedulers.background import BackgroundScheduler
from threading import Timer

app = Flask(__name__)
app.secret_key = 'your-secret-key-change-in-production'

# === НАСТРОЙКИ ===
TELEGRAM_BOT_TOKEN = "8391583658:AAGrNdENe29YmD8U-DSZBoNJmCXAiEb98sI"
GROUP_CHAT_ID = -4961108450

# Подключение к базе
def get_db():
    conn = sqlite3.connect("../tickets.db")
    conn.row_factory = sqlite3.Row
    return conn

def get_users_db():
    conn = sqlite3.connect("../users.db")
    conn.row_factory = sqlite3.Row
    return conn

# === Авторизация ===
@app.route("/login", methods=["GET", "POST"])
def login():
    if request.method == "POST":
        username = request.form["username"]
        password = request.form["password"]
        with get_users_db() as conn:
            user = conn.execute("SELECT * FROM users WHERE username = ? AND password = ?", (username, password)).fetchone()
        if user:
            session["user"] = username
            session["role"] = user["role"]
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

# === Отправка сообщения через Telegram API ===
def send_telegram_message(chat_id, text, parse_mode='HTML'):
    url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
    payload = {
        'chat_id': chat_id,
        'text': text,
        'parse_mode': parse_mode,
        'disable_web_page_preview': True
    }
    try:
        response = requests.post(url, data=payload, timeout=10)
        result = response.json()
        if result.get('ok'):
            return True, result
        else:
            return False, result.get('description', 'Unknown error')
    except Exception as e:
        return False, str(e)

# === МАРШРУТЫ ===

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
            m.username,
            m.client_name,
            m.business,
            m.city,
            m.location_name,
            m.problem,
            m.created_at,
            t.status,
            t.resolved_by,
            t.resolved_at,
            m.created_date,
            m.created_time
        FROM messages m
        LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
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
        settings=settings
    )

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
                m.username,
                m.client_name,
                m.business,
                m.city,
                m.location_name,
                m.problem,
                m.created_at,
                t.status,
                t.resolved_by,
                t.resolved_at,
                m.created_date,
                m.created_time
            FROM messages m
            LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
            ORDER BY m.created_at DESC
        """)
        tickets = cur.fetchall()

        result = []
        for t in tickets:
            cur.execute("""
                SELECT sender, message, timestamp FROM chat_history
                WHERE ticket_id = ? AND sender = 'support'
                ORDER BY timestamp ASC
                LIMIT 1
            """, (t['ticket_id'],))
            first_reply = cur.fetchone()

            admin_name = "—"
            if first_reply:
                import re
                match = re.search(r"от поддержки \(([^)]+)\)", first_reply['message'])
                if match:
                    admin_name = match.group(1)
                else:
                    admin_name = "Поддержка"
            else:
                admin_name = "—"

            row = dict(t)
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

@app.route("/clients")
@login_required
def clients_list():
    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
        SELECT 
            m.user_id,
            m.username,
            m.client_name,
            COUNT(*) as ticket_count,
            MIN(m.created_at) as first_contact,
            MAX(m.created_at) as last_contact
        FROM messages m
        GROUP BY m.user_id
        ORDER BY last_contact DESC
    """)
    clients = cur.fetchall()
    conn.close()
    return render_template("clients.html", clients=clients)

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
            m.category
        FROM messages m
        LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
        WHERE m.user_id = ?
        ORDER BY m.created_at DESC
    """, (user_id,))
    tickets = cur.fetchall()

    # ✅ Считаем duration_minutes в Python
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

    conn.close()

    return render_template(
        "client_profile.html",
        client=dict(info),
        tickets=ticket_list,  # ← Уже список словарей с duration_minutes
        stats=dict(stats),
        feedbacks=[dict(f) for f in feedbacks]
        # ❌ Убрали datetime=dt — не нужно
    )

    # ✅ Вычисляем duration_minutes на стороне Python
    ticket_list = []
    for t in tickets:
        row = dict(t)
        if row['status'] == 'resolved' and row['resolved_at'] and row['created_date'] and row['created_time']:
            try:
                start = datetime.datetime.fromisoformat(f"{row['created_date']} {row['created_time']}")
                end = datetime.datetime.fromisoformat(row['resolved_at'])
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

    conn.close()

    return render_template(
        "client_profile.html",
        client=dict(info),
        tickets=ticket_list,
        stats=dict(stats),
        feedbacks=[dict(f) for f in feedbacks]
    )

@app.route("/analytics/clients")
@login_required
def analytics_clients():
    conn = get_db()
    cur = conn.cursor()

    # Топ клиентов по количеству заявок
    cur.execute("""
        SELECT 
            m.user_id,
            m.username,
            m.client_name,
            COUNT(*) as ticket_count
        FROM messages m
        GROUP BY m.user_id
        ORDER BY ticket_count DESC
        LIMIT 10
    """)
    top_clients = cur.fetchall()

    # Распределение по статусам
    cur.execute("""
        SELECT 
            t.status,
            COUNT(*) as cnt
        FROM messages m
        LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
        GROUP BY t.status
    """)
    status_data = {row['status'] or 'неизвестно': row['cnt'] for row in cur.fetchall()}

    # По бизнесу
    cur.execute("""
        SELECT business, COUNT(*) as cnt FROM messages GROUP BY business
    """)
    business_data = {row['business'] or 'не указан': row['cnt'] for row in cur.fetchall()}

    conn.close()
    return render_template("analytics_clients.html", 
                         top_clients=top_clients, 
                         status_data=status_data,
                         business_data=business_data)

@app.route("/analytics")
@login_required
def analytics():
    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
        SELECT business, location_type, city, category, status, COUNT(*) as cnt
        FROM messages JOIN tickets USING(ticket_id)
        GROUP BY business, location_type, city, category, status
    """)
    rows = cur.fetchall()
    conn.close()
    return render_template("analytics.html", stats=rows)

@app.route("/dashboard")
@login_required
def dashboard():
    conn = get_db()
    cur = conn.cursor()

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
                         city_data=city_data)

@app.route("/settings")
@login_required
def settings_page():
    settings = {"auto_close_hours": 24, "categories": ["Консультация"]}  # ✅ Дефолтные значения
    locations = {}
    if os.path.exists("../settings.json"):
        try:
            with open("../settings.json", "r", encoding="utf-8") as f:
                loaded = json.load(f)
                settings.update({k: v for k, v in loaded.items() if k in ["auto_close_hours", "categories"]})
        except Exception as e:
            logging.error(f"Ошибка загрузки settings.json: {e}")
    if os.path.exists("../locations.json"):
        try:
            with open("../locations.json", "r", encoding="utf-8") as f:
                locations = json.load(f)
        except Exception as e:
            logging.error(f"Ошибка загрузки locations.json: {e}")
    return render_template("settings.html", settings=settings, locations=locations)

@app.route("/settings", methods=["POST"])
@login_required
def update_settings():
    data = request.json
    if "locations" in data:
        with open("../locations.json", "w", encoding="utf-8") as f:
            json.dump(data["locations"], f, ensure_ascii=False, indent=2)
    if "categories" in data or "auto_close_hours" in data:
        current = {"auto_close_hours": 24, "categories": ["Консультация"]}
        if os.path.exists("../settings.json"):
            with open("../settings.json", "r", encoding="utf-8") as f:
                current = json.load(f)
        current.update({k: v for k, v in data.items() if k in ["auto_close_hours", "categories"]})
        with open("../settings.json", "w", encoding="utf-8") as f:
            json.dump(current, f, ensure_ascii=False, indent=2)
    return jsonify({"success": True})

# === API ===

@app.route("/users")
@login_required
def get_users():
    with get_users_db() as conn:
        users = conn.execute("SELECT id, username, role FROM users").fetchall()
    return jsonify([dict(u) for u in users])

@app.route("/users", methods=["POST"])
@login_required
def add_user():
    data = request.json
    username = data["username"]
    password = data["password"]
    role = data.get("role", "user")
    try:
        with get_users_db() as conn:
            conn.execute("INSERT INTO users (username, password, role) VALUES (?, ?, ?)", (username, password, role))
            conn.commit()
        return jsonify({"success": True})
    except sqlite3.IntegrityError:
        return jsonify({"success": False, "error": "Пользователь уже существует"})

@app.route("/users/<int:user_id>", methods=["DELETE"])
@login_required
def delete_user(user_id):
    if user_id == 1:  # Защита admin
        return jsonify({"success": False, "error": "Нельзя удалить этого пользователя"})
    with get_users_db() as conn:
        conn.execute("DELETE FROM users WHERE id = ?", (user_id,))
        conn.commit()
    return jsonify({"success": True})

@app.route("/stats_data")
@login_required
def stats_data():
    conn = get_db()
    total = conn.execute("SELECT COUNT(*) FROM tickets").fetchone()[0]
    resolved = conn.execute("SELECT COUNT(*) FROM tickets WHERE status = 'resolved'").fetchone()[0]
    pending = total - resolved
    conn.close()
    return jsonify({"total": total, "resolved": resolved, "pending": pending})

@app.route("/history")
@login_required
def history():
    user_id = request.args.get("user_id")
    ticket_id = request.args.get("ticket_id")
    conn = get_db()
    cur = conn.cursor()
    cur.execute("""
        SELECT sender, message, timestamp FROM chat_history
        WHERE user_id = ? AND ticket_id = ?
        ORDER BY timestamp ASC
    """, (user_id, ticket_id))
    rows = cur.fetchall()
    conn.close()

    messages = [
        {"sender": r["sender"], "message": r["message"], "timestamp": r["timestamp"]}
        for r in rows
    ]
    return jsonify({"messages": messages})

@app.route("/reply", methods=["POST"])
@login_required
def reply():
    data = request.json
    user_id = data["user_id"]
    reply_text = data["text"]
    admin_name = data.get("admin", "Поддержка")
    ticket_id = data.get("ticket_id")

    full_reply = f"📩 <b>Ответ от поддержки ({admin_name}):</b>\n\n{reply_text}"
    success, info = send_telegram_message(chat_id=user_id, text=full_reply)

    if not success:
        return jsonify({"success": False, "error": f"❌ Не удалось отправить: {info}"})

    try:
        conn = get_db()
        conn.execute(
            "INSERT INTO chat_history (user_id, sender, message, timestamp, ticket_id) VALUES (?, ?, ?, ?, ?)",
            (user_id, "support", reply_text, datetime.datetime.now().isoformat(), ticket_id)
        )
        conn.commit()
        conn.close()
        return jsonify({"success": True})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

@app.route("/close_ticket", methods=["POST"])
@login_required
def close_ticket():
    data = request.json
    user_id = data["user_id"]
    ticket_id = data["ticket_id"]
    admin_name = data.get("admin", "Поддержка")
    category = data.get("category", "Без категории")

    try:
        conn = get_db()
        conn.execute("UPDATE messages SET category = ? WHERE ticket_id = ?", (category, ticket_id))
        conn.execute(
            "UPDATE tickets SET status = 'resolved', resolved_at = ?, resolved_by = ? WHERE ticket_id = ?",
            (datetime.datetime.now().isoformat(), admin_name, ticket_id)
        )
        conn.commit()
        conn.close()

        try:
            close_msg = f"Ваше обращение #{ticket_id} закрыто. Для запуска нового диалога нажмите /start"
            send_telegram_message(chat_id=user_id, text=close_msg, parse_mode='HTML')

            feedback_msg = (
                "🌟 Пожалуйста, оцените качество поддержки:\n\n"
                "1️⃣ — Очень плохо\n"
                "2️⃣ — Плохо\n"
                "3️⃣ — Удовлетворительно\n"
                "4️⃣ — Хорошо\n"
                "5️⃣ — Отлично\n\n"
                "Пожалуйста, отправьте одну цифру."
            )
            send_telegram_message(chat_id=user_id, text=feedback_msg, parse_mode='HTML')

            def send_sorry():
                with sqlite3.connect("tickets.db") as conn:
                    row = conn.execute("SELECT * FROM feedbacks WHERE user_id = ?", (user_id,)).fetchone()
                if not row:
                    send_telegram_message(chat_id=user_id, text="Очень жаль, что не получили вашей оценки.", parse_mode='HTML')

            Timer(900, send_sorry).start()

        except Exception as e:
            logging.error(f"❌ Ошибка при отправке уведомлений: {e}")

        return jsonify({"success": True})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)})

if __name__ == "__main__":
    app.run(port=5000, debug=True)