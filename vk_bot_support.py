"""VK bot integration for the support CRM.

This module contains the common logic for processing dialogs with users, a
long-poll runner (legacy mode) and can be reused by other entry points such as
webhook handlers.
"""
from __future__ import annotations

import json
import logging
import os
import random
import shutil
import sqlite3
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Dict, List, Optional

import requests
from vk_api import VkApi
from vk_api.bot_longpoll import VkBotEventType, VkBotLongPoll
from vk_api.keyboard import VkKeyboard, VkKeyboardColor

from bot_support import (
    ATTACHMENTS_DIR,
    LOCATIONS,
    add_history,
    create_ticket,
    get_questions_cfg,
    insert_message,
    iter_active_channels,
    save_username_if_changed,
)
from migrations_runner import ensure_schema_is_current
from config import get_settings

settings = get_settings()
DB_PATH = str(settings.db.tickets_path)

logger = logging.getLogger(__name__)

ensure_schema_is_current()


def _parse_platform_config(raw) -> dict:
    if isinstance(raw, str) and raw.strip():
        try:
            parsed = json.loads(raw)
            return parsed if isinstance(parsed, dict) else {}
        except Exception:
            return {}
    if isinstance(raw, dict):
        return dict(raw)
    return {}


@dataclass
class ConversationState:
    user_id: int
    channel_id: int
    questions: List[dict]
    answers: Dict[str, str] = field(default_factory=dict)
    index: int = 0
    awaiting_problem: bool = False

    def current_question(self) -> Optional[dict]:
        if 0 <= self.index < len(self.questions):
            return self.questions[self.index]
        return None


class VkConversationManager:
    """Stateful helper that encapsulates the dialog logic for VK bots."""

    def __init__(self, channel_id: int, api, *, group_id: Optional[int] = None):
        self.channel_id = channel_id
        self.api = api
        self.group_id = group_id
        self.conversations: Dict[int, ConversationState] = {}
        self.question_flow = self._load_question_flow()

    # --- Configuration -----------------------------------------------------
    def _load_question_flow(self) -> List[dict]:
        cfg = get_questions_cfg(self.channel_id)
        questions = cfg.get("questions") if isinstance(cfg, dict) else []
        prepared: List[dict] = []
        if isinstance(questions, list):
            for question in questions:
                if isinstance(question, dict):
                    prepared.append(dict(question))
        return prepared

    def reload_questions(self) -> None:
        """Reloads question flow from DB (used for hot reconfiguration)."""

        self.question_flow = self._load_question_flow()

    # --- Public API --------------------------------------------------------
    def handle_message(self, message: dict) -> None:
        user_id = message.get("from_id")
        peer_id = message.get("peer_id")
        if not user_id or peer_id != user_id:
            # Игнорируем групповые беседы
            return
        text = (message.get("text") or "").strip()
        state = self.conversations.get(user_id)
        if state is None:
            state = ConversationState(
                user_id=user_id,
                channel_id=self.channel_id,
                questions=list(self.question_flow),
            )
            self.conversations[user_id] = state
            self._send_message(user_id, "Здравствуйте! Давайте оформим обращение.")
            if not state.questions:
                state.awaiting_problem = True
                self._send_message(user_id, "Опишите проблему, пожалуйста.")
            else:
                self._prompt_question(state)
            return

        lowered = text.lower()
        if lowered in {"стоп", "cancel", "отмена"}:
            self._send_message(user_id, "Заявка отменена. Вы можете начать заново, просто написав сообщение.")
            self.conversations.pop(user_id, None)
            return

        if state.awaiting_problem:
            if not text and not message.get("attachments"):
                self._send_message(user_id, "Пожалуйста, опишите проблему словами.")
                return
            self._finalize_ticket(state, message, text)
            self.conversations.pop(user_id, None)
            return

        question = state.current_question()
        if question is None:
            state.awaiting_problem = True
            self._send_message(user_id, "Опишите проблему, пожалуйста.")
            return

        options = self._resolve_options(question, state.answers)
        if options and text not in options:
            lines = ["Пожалуйста, выберите вариант из списка:"] + [f"• {opt}" for opt in options]
            self._send_message(user_id, "\n".join(lines), keyboard=self._build_keyboard(options))
            return

        key = self._question_key(question)
        if key:
            state.answers[key] = text
        state.index += 1
        if state.index >= len(state.questions):
            state.awaiting_problem = True
            self._send_message(
                user_id,
                "Опишите проблему, пожалуйста. При необходимости прикрепите фото или документы одним сообщением.",
            )
        else:
            self._prompt_question(state)

    def _prompt_question(self, state: ConversationState) -> None:
        question = state.current_question()
        if question is None:
            return
        label = question.get("label") or "Уточните, пожалуйста:"
        options = self._resolve_options(question, state.answers)
        lines = [label]
        if options:
            lines.extend(f"• {opt}" for opt in options)
        keyboard = self._build_keyboard(options)
        self._send_message(state.user_id, "\n".join(lines), keyboard=keyboard)

    def _question_key(self, question: dict) -> str:
        preset = question.get("preset") if isinstance(question.get("preset"), dict) else None
        if preset:
            field = preset.get("field")
            if isinstance(field, str) and field.strip():
                return field.strip()
        qid = question.get("id")
        if isinstance(qid, str) and qid.strip():
            return qid.strip()
        return f"q_{question.get('order', len(self.question_flow))}"

    def _resolve_options(self, question: dict, answers: dict) -> List[str]:
        preset = question.get("preset") if isinstance(question.get("preset"), dict) else None
        if not preset:
            return []
        field = preset.get("field")
        options: List[str] = []
        if field == "business":
            options = sorted(LOCATIONS.keys())
        elif field == "location_type":
            business = answers.get("business")
            branch = LOCATIONS.get(business) if business else {}
            options = sorted(branch.keys()) if isinstance(branch, dict) else []
        elif field == "city":
            business = answers.get("business")
            location_type = answers.get("location_type")
            branch = LOCATIONS.get(business) if business else {}
            cities = branch.get(location_type) if isinstance(branch, dict) else {}
            options = sorted(cities.keys()) if isinstance(cities, dict) else []
        elif field == "location_name":
            business = answers.get("business")
            location_type = answers.get("location_type")
            city = answers.get("city")
            branch = LOCATIONS.get(business) if business else {}
            types = branch.get(location_type) if isinstance(branch, dict) else {}
            names = types.get(city) if isinstance(types, dict) else []
            options = list(names) if isinstance(names, list) else []
        excluded = question.get("excluded_options")
        if isinstance(excluded, list):
            excluded_values = {str(value) for value in excluded}
            options = [opt for opt in options if opt not in excluded_values]
        return options

    def _build_keyboard(self, options: List[str]):
        if not options or len(options) > 10:
            return None
        keyboard = VkKeyboard(one_time=True)
        row_count = 0
        for idx, option in enumerate(options, start=1):
            keyboard.add_button(option, color=VkKeyboardColor.SECONDARY)
            if idx % 3 == 0 and idx != len(options):
                keyboard.add_line()
                row_count += 1
                if row_count >= 3:
                    break
        return keyboard.get_keyboard()

    # --- Ticket persistence ----------------------------------------------------
    def _finalize_ticket(self, state: ConversationState, message: dict, problem_text: str) -> None:
        user_id = state.user_id
        attachments = self._collect_attachments(message)
        ticket_id = uuid.uuid4().hex[:8]
        now = datetime.now(timezone.utc)
        created_at = now.isoformat()
        created_date = now.strftime('%Y-%m-%d')
        created_time = now.strftime('%H:%M')

        profile = self._fetch_user_profile(user_id)
        username = profile.get('screen_name') or f"id{user_id}"
        client_name = f"{profile.get('first_name', '')} {profile.get('last_name', '')}".strip() or None

        business = state.answers.get('business')
        location_type = state.answers.get('location_type')
        city = state.answers.get('city')
        location_name = state.answers.get('location_name')

        try:
            with sqlite3.connect(DB_PATH) as conn:
                conn.execute("BEGIN")
                save_username_if_changed(conn, user_id, username)
                create_ticket(
                    conn,
                    ticket_id=ticket_id,
                    user_id=user_id,
                    status='pending',
                    created_at=created_at,
                    channel_id=self.channel_id,
                )
                insert_message(
                    conn,
                    group_msg_id=None,
                    user_id=user_id,
                    business=business,
                    location_type=location_type,
                    city=city,
                    location_name=location_name,
                    problem=problem_text,
                    created_at=created_at,
                    username=username,
                    category=None,
                    ticket_id=ticket_id,
                    created_date=created_date,
                    created_time=created_time,
                    client_status=None,
                    client_name=client_name,
                    updated_at=created_at,
                    updated_by='vk_bot',
                    channel_id=self.channel_id,
                )
                add_history(
                    conn=conn,
                    ticket_id=ticket_id,
                    sender='user',
                    text=problem_text,
                    ts=created_at,
                    message_type='text',
                    attachment=None,
                    channel_id=self.channel_id,
                    user_id=user_id,
                )
                if attachments:
                    dest_dir = os.path.join(ATTACHMENTS_DIR, ticket_id)
                    os.makedirs(dest_dir, exist_ok=True)
                    for attachment in attachments:
                        src = attachment["temp_path"]
                        filename = attachment["filename"]
                        dest = os.path.join(dest_dir, filename)
                        try:
                            shutil.copy2(src, dest)
                        except Exception as copy_exc:
                            logger.warning("Не удалось переместить вложение %s: %s", src, copy_exc)
                            continue
                        add_history(
                            conn=conn,
                            ticket_id=ticket_id,
                            sender='user',
                            text=attachment.get('caption'),
                            ts=attachment['timestamp'],
                            message_type=attachment['message_type'],
                            attachment=dest,
                            channel_id=self.channel_id,
                            user_id=user_id,
                        )
                conn.commit()
        finally:
            for attachment in attachments:
                try:
                    if os.path.exists(attachment["temp_path"]):
                        os.remove(attachment["temp_path"])
                except Exception:
                    pass

        self._send_message(
            user_id,
            f"Спасибо! Заявка отправлена. ID: {ticket_id}. Мы свяжемся с вами в ближайшее время.",
        )

    def _collect_attachments(self, message: dict) -> List[dict]:
        collected: List[dict] = []
        attachments = message.get("attachments") or []
        if not isinstance(attachments, list):
            return collected
        for attachment in attachments:
            att_type = attachment.get("type")
            handler = getattr(self, f"_handle_attachment_{att_type}", None)
            if callable(handler):
                info = handler(attachment)
                if info:
                    collected.append(info)
        return collected

    def _download_file(self, url: str, suffix: str) -> Optional[str]:
        if not url:
            return None
        try:
            response = requests.get(url, timeout=30)
            response.raise_for_status()
        except Exception as exc:
            logger.warning("Не удалось скачать вложение из %s: %s", url, exc)
            return None
        filename = f"{uuid.uuid4().hex}{suffix}"
        temp_path = os.path.join(ATTACHMENTS_DIR, "temp", filename)
        os.makedirs(os.path.dirname(temp_path), exist_ok=True)
        with open(temp_path, "wb") as handle:
            handle.write(response.content)
        return temp_path

    def _handle_attachment_photo(self, attachment: dict) -> Optional[dict]:
        photo = attachment.get("photo") or {}
        sizes = photo.get("sizes") or []
        best = None
        for candidate in sizes:
            if not isinstance(candidate, dict):
                continue
            if best is None or candidate.get("width", 0) * candidate.get("height", 0) > best.get("width", 0) * best.get("height", 0):
                best = candidate
        url = best.get("url") if isinstance(best, dict) else None
        temp_path = self._download_file(url, ".jpg")
        if not temp_path:
            return None
        return {
            "temp_path": temp_path,
            "filename": os.path.basename(temp_path),
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "message_type": "photo",
            "caption": None,
        }

    def _handle_attachment_doc(self, attachment: dict) -> Optional[dict]:
        doc = attachment.get("doc") or {}
        url = doc.get("url")
        title = doc.get("title") or "document"
        ext = os.path.splitext(title)[1] or ".bin"
        temp_path = self._download_file(url, ext)
        if not temp_path:
            return None
        filename = f"{title}" if ext else f"{title}{ext}"
        return {
            "temp_path": temp_path,
            "filename": filename,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "message_type": "document",
            "caption": None,
        }

    def _handle_attachment_audio_message(self, attachment: dict) -> Optional[dict]:
        audio = attachment.get("audio_message") or {}
        url = audio.get("link_mp3") or audio.get("link_ogg")
        temp_path = self._download_file(url, ".mp3")
        if not temp_path:
            return None
        return {
            "temp_path": temp_path,
            "filename": os.path.basename(temp_path),
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "message_type": "voice",
            "caption": None,
        }

    def _handle_attachment_audio(self, attachment: dict) -> Optional[dict]:
        audio = attachment.get("audio") or {}
        url = audio.get("url")
        temp_path = self._download_file(url, ".mp3")
        if not temp_path:
            return None
        performer = audio.get("artist") or "audio"
        title = audio.get("title") or "track"
        filename = f"{performer}-{title}.mp3".replace("/", "_")
        return {
            "temp_path": temp_path,
            "filename": filename,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "message_type": "audio",
            "caption": None,
        }

    def _handle_attachment_video(self, attachment: dict) -> Optional[dict]:
        video = attachment.get("video") or {}
        files = video.get("files") or {}
        url = files.get("mp4_720") or files.get("mp4_480") or files.get("mp4_360")
        temp_path = self._download_file(url, ".mp4")
        if not temp_path:
            return None
        return {
            "temp_path": temp_path,
            "filename": os.path.basename(temp_path),
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "message_type": "video",
            "caption": None,
        }

    def _fetch_user_profile(self, user_id: int) -> dict:
        try:
            response = self.api.users.get(user_ids=user_id, fields="screen_name")
            if isinstance(response, list) and response:
                data = response[0]
                return {
                    "screen_name": data.get("screen_name") or "",
                    "first_name": data.get("first_name") or "",
                    "last_name": data.get("last_name") or "",
                }
        except Exception as exc:
            logger.warning("Не удалось получить профиль VK %s: %s", user_id, exc)
        return {"screen_name": f"id{user_id}", "first_name": "", "last_name": ""}

    def _send_message(self, user_id: int, text: str, keyboard: Optional[str] = None) -> None:
        payload = {
            "user_id": user_id,
            "random_id": random.randint(1, 2_147_483_647),
            "message": text,
        }
        if keyboard:
            payload["keyboard"] = keyboard
        try:
            self.api.messages.send(**payload)
        except Exception as exc:
            logger.error("Не удалось отправить сообщение пользователю %s: %s", user_id, exc)


class VkBotRunner:
    """Legacy runner that uses long-poll API."""

    def __init__(self, channel_id: int, token: str, group_id: int):
        self.channel_id = channel_id
        self.token = token
        self.group_id = group_id
        self.session = VkApi(token=token)
        self.api = self.session.get_api()
        self.manager = VkConversationManager(channel_id, self.api, group_id=group_id)
        self.longpoll = VkBotLongPoll(self.session, group_id)
        self._stop_event = threading.Event()

    def stop(self) -> None:
        self._stop_event.set()

    def run(self) -> None:
        logger.info("Запуск VK-бота для канала %s (group_id=%s)", self.channel_id, self.group_id)
        while not self._stop_event.is_set():
            try:
                for event in self.longpoll.check():
                    if self._stop_event.is_set():
                        break
                    if event.type == VkBotEventType.MESSAGE_NEW:
                        self.manager.handle_message(event.message)
            except Exception as exc:
                logger.error("VK longpoll error for channel %s: %s", self.channel_id, exc)
                time.sleep(5)


def run_all_vk_bots() -> None:
    channels = iter_active_channels(platform="vk")
    if not channels:
        print("❌ Нет активных VK-каналов в базе данных.")
        return
    runners: List[VkBotRunner] = []
    threads: List[threading.Thread] = []
    for row in channels:
        row_dict = dict(row) if hasattr(row, "keys") else dict(row or {})
        config = _parse_platform_config(row_dict.get("platform_config"))
        group_id = config.get("group_id") or config.get("groupId")
        try:
            group_id_int = int(group_id)
        except (TypeError, ValueError):
            logger.warning("Пропускаем канал %s: не указан корректный group_id", row_dict.get("id"))
            continue
        runner = VkBotRunner(channel_id=row_dict.get("id"), token=row_dict.get("token"), group_id=group_id_int)
        thread = threading.Thread(target=runner.run, daemon=True)
        thread.start()
        runners.append(runner)
        threads.append(thread)

    if not threads:
        print("❌ Не найдено корректно настроенных VK-каналов.")
        return

    try:
        while any(t.is_alive() for t in threads):
            time.sleep(1)
    except KeyboardInterrupt:
        print("Остановка VK-ботов…")
        for runner in runners:
            runner.stop()
        for thread in threads:
            thread.join(timeout=5)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="[%(levelname)s] %(message)s")
    run_all_vk_bots()
