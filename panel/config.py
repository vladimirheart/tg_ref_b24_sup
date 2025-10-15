# panel/config.py
import sqlite3
from dotenv import load_dotenv

load_dotenv()

def get_db():
    conn = sqlite3.connect("../tickets.db")
    conn.row_factory = sqlite3.Row
    return conn

def get_users_db():
    conn = sqlite3.connect("../users.db")
    conn.row_factory = sqlite3.Row
    return conn
