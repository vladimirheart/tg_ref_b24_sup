import logging
from telebot import TeleBot, types
from database import get_db_connection
from bitrix24 import create_lead, create_contact
import os
from datetime import datetime

logger = logging.getLogger(__name__)

def setup_handlers(bot: TeleBot):
    
    @bot.message_handler(func=lambda message: True)
    def handle_message(message):
        try:
            conn = get_db_connection()
            cursor = conn.cursor()
            
            # Сохраняем сообщение в историю с указанием типа
            cursor.execute('''
                INSERT INTO chat_history (user_id, message, timestamp, message_id, message_type)
                VALUES (?, ?, ?, ?, ?)
            ''', (message.chat.id, message.text, message.date, message.message_id, 'text'))
            
            conn.commit()
            conn.close()
            
        except Exception as e:
            logger.error(f"Error handling message: {e}")
            bot.reply_to(message, "❌ Произошла ошибка при обработке сообщения")

    @bot.message_handler(content_types=['photo'])
    def handle_photo(message):
        try:
            user_id = message.chat.id
            conn = get_db_connection()
            cursor = conn.cursor()
            
            # Получаем информацию о файле
            file_id = message.photo[-1].file_id
            file_info = bot.get_file(file_id)
            downloaded_file = bot.download_file(file_info.file_path)
            
            # Создаем директорию для фото, если её нет
            os.makedirs('photos', exist_ok=True)
            
            # Сохраняем фото
            file_extension = file_info.file_path.split('.')[-1] if '.' in file_info.file_path else 'jpg'
            photo_filename = f"photos/{user_id}_{message.message_id}.{file_extension}"
            
            with open(photo_filename, 'wb') as new_file:
                new_file.write(downloaded_file)
            
            # Сохраняем в историю чата с типом 'photo'
            cursor.execute('''
                INSERT INTO chat_history (user_id, message, timestamp, message_id, message_type)
                VALUES (?, ?, ?, ?, ?)
            ''', (user_id, f"photo:{photo_filename}", message.date, message.message_id, 'photo'))
            
            # Проверяем, есть ли активная заявка у пользователя
            cursor.execute('''
                SELECT id, problem_description FROM applications 
                WHERE user_id = ? AND status = 'draft'
                ORDER BY created_at DESC LIMIT 1
            ''', (user_id,))
            
            application = cursor.fetchone()
            
            if application:
                # Обновляем существующую заявку с фото
                app_id, problem_description = application
                cursor.execute('''
                    UPDATE applications SET photo_path = ? WHERE id = ?
                ''', (photo_filename, app_id))
                
                # Если есть описание проблемы, завершаем заявку
                if problem_description:
                    cursor.execute('''
                        UPDATE applications SET status = 'new' WHERE id = ?
                    ''', (app_id,))
                    
                    # Создаем контакт и сделку в Bitrix24
                    try:
                        contact_id = create_contact(
                            user_id=user_id,
                            username=message.from_user.username,
                            first_name=message.from_user.first_name,
                            last_name=message.from_user.last_name
                        )
                        
                        deal_id = create_lead(
                            contact_id=contact_id,
                            description=problem_description,
                            photo_path=photo_filename
                        )
                        
                        # Обновляем заявку с ID из Bitrix24
                        cursor.execute('''
                            UPDATE applications 
                            SET b24_contact_id = ?, b24_deal_id = ?, status = 'processed'
                            WHERE id = ?
                        ''', (contact_id, deal_id, app_id))
                        
                        bot.send_message(
                            user_id, 
                            "✅ Заявка успешно создана! Фото добавлено.\n"
                            "С вами свяжутся в ближайшее время."
                        )
                        
                    except Exception as e:
                        logger.error(f"Bitrix24 error: {e}")
                        bot.send_message(
                            user_id, 
                            "✅ Заявка создана, но возникла ошибка при интеграции с CRM.\n"
                            "Администратор уже уведомлен."
                        )
                else:
                    bot.send_message(
                        user_id, 
                        "📸 Фото получено. Теперь опишите проблему текстом."
                    )
            
            conn.commit()
            conn.close()
            
        except Exception as e:
            logger.error(f"Error handling photo: {e}")
            bot.reply_to(message, "❌ Ошибка при обработке фото")

    @bot.message_handler(commands=['start'])
    def handle_start(message):
        try:
            conn = get_db_connection()
            cursor = conn.cursor()
            
            # Регистрируем пользователя
            cursor.execute('''
                INSERT OR IGNORE INTO users (user_id, username, first_name, last_name)
                VALUES (?, ?, ?, ?)
            ''', (
                message.chat.id, 
                message.from_user.username, 
                message.from_user.first_name, 
                message.from_user.last_name
            ))
            
            # Сохраняем команду в историю
            cursor.execute('''
                INSERT INTO chat_history (user_id, message, timestamp, message_id, message_type)
                VALUES (?, ?, ?, ?, ?)
            ''', (message.chat.id, message.text, message.date, message.message_id, 'command'))
            
            conn.commit()
            conn.close()
            
            # Приветственное сообщение
            welcome_text = (
                "👋 Добро пожаловать!\n\n"
                "Я помогу вам создать заявку для службы поддержки.\n"
                "Просто опишите вашу проблему и при необходимости прикрепите фото.\n\n"
                "📝 Отправьте текст с описанием проблемы\n"
                "📸 Или пришлите фото\n"
                "🖼️ Можно отправить и то, и другое\n\n"
                "Для начала просто напишите вашу проблему..."
            )
            
            bot.send_message(message.chat.id, welcome_text)
            
        except Exception as e:
            logger.error(f"Error in start handler: {e}")
            bot.reply_to(message, "❌ Произошла ошибка при запуске бота")

    @bot.message_handler(commands=['application'])
    def handle_application_command(message):
        try:
            user_id = message.chat.id
            conn = get_db_connection()
            cursor = conn.cursor()
            
            # Сохраняем команду в историю
            cursor.execute('''
                INSERT INTO chat_history (user_id, message, timestamp, message_id, message_type)
                VALUES (?, ?, ?, ?, ?)
            ''', (user_id, message.text, message.date, message.message_id, 'command'))
            
            # Создаем черновик заявки
            cursor.execute('''
                INSERT INTO applications (user_id, status)
                VALUES (?, 'draft')
            ''', (user_id,))
            
            conn.commit()
            conn.close()
            
            bot.send_message(
                user_id,
                "📝 Создание новой заявки\n\n"
                "Опишите вашу проблему текстом или пришлите фото.\n"
                "Можно отправить и то, и другое."
            )
            
        except Exception as e:
            logger.error(f"Error in application command: {e}")
            bot.reply_to(message, "❌ Ошибка при создании заявки")

    # Обработчик для текстовых сообщений (создание/обновление заявки)
    @bot.message_handler(func=lambda message: True, content_types=['text'])
    def handle_text_for_application(message):
        try:
            if message.text.startswith('/'):
                return  # Пропускаем команды
                
            user_id = message.chat.id
            conn = get_db_connection()
            cursor = conn.cursor()
            
            # Сохраняем сообщение в историю
            cursor.execute('''
                INSERT INTO chat_history (user_id, message, timestamp, message_id, message_type)
                VALUES (?, ?, ?, ?, ?)
            ''', (user_id, message.text, message.date, message.message_id, 'text'))
            
            # Проверяем, есть ли активная заявка
            cursor.execute('''
                SELECT id, photo_path FROM applications 
                WHERE user_id = ? AND status = 'draft'
                ORDER BY created_at DESC LIMIT 1
            ''', (user_id,))
            
            application = cursor.fetchone()
            
            if application:
                app_id, photo_path = application
                # Обновляем заявку с описанием проблемы
                cursor.execute('''
                    UPDATE applications 
                    SET problem_description = ?, 
                        status = CASE WHEN photo_path IS NOT NULL THEN 'new' ELSE 'draft' END
                    WHERE id = ?
                ''', (message.text, app_id))
                
                # Если есть фото, завершаем заявку
                if photo_path:
                    try:
                        # Создаем контакт и сделку в Bitrix24
                        contact_id = create_contact(
                            user_id=user_id,
                            username=message.from_user.username,
                            first_name=message.from_user.first_name,
                            last_name=message.from_user.last_name
                        )
                        
                        deal_id = create_lead(
                            contact_id=contact_id,
                            description=message.text,
                            photo_path=photo_path
                        )
                        
                        # Обновляем заявку с ID из Bitrix24
                        cursor.execute('''
                            UPDATE applications 
                            SET b24_contact_id = ?, b24_deal_id = ?, status = 'processed'
                            WHERE id = ?
                        ''', (contact_id, deal_id, app_id))
                        
                        bot.send_message(
                            user_id, 
                            "✅ Заявка успешно создана!\n"
                            "С вами свяжутся в ближайшее время."
                        )
                        
                    except Exception as e:
                        logger.error(f"Bitrix24 error: {e}")
                        cursor.execute('''
                            UPDATE applications SET status = 'new' WHERE id = ?
                        ''', (app_id,))
                        bot.send_message(
                            user_id, 
                            "✅ Заявка создана, но возникла ошибка при интеграции с CRM.\n"
                            "Администратор уже уведомлен."
                        )
                else:
                    bot.send_message(
                        user_id, 
                        "📝 Описание проблемы сохранено.\n"
                        "Теперь можете отправить фото или отправьте ещё одно сообщение для дополнения заявки."
                    )
            else:
                # Создаем новую заявку
                cursor.execute('''
                    INSERT INTO applications (user_id, problem_description, status)
                    VALUES (?, ?, 'new')
                ''', (user_id, message.text))
                
                try:
                    # Создаем контакт и сделку в Bitrix24
                    contact_id = create_contact(
                        user_id=user_id,
                        username=message.from_user.username,
                        first_name=message.from_user.first_name,
                        last_name=message.from_user.last_name
                    )
                    
                    deal_id = create_lead(
                        contact_id=contact_id,
                        description=message.text,
                        photo_path=None
                    )
                    
                    cursor.execute('''
                        UPDATE applications 
                        SET b24_contact_id = ?, b24_deal_id = ?, status = 'processed'
                        WHERE user_id = ? AND status = 'new'
                    ''', (contact_id, deal_id, user_id))
                    
                    bot.send_message(
                        user_id, 
                        "✅ Заявка успешно создана!\n"
                        "С вами свяжутся в ближайшее время."
                    )
                    
                except Exception as e:
                    logger.error(f"Bitrix24 error: {e}")
                    bot.send_message(
                        user_id, 
                        "✅ Заявка создана, но возникла ошибка при интеграции с CRM.\n"
                        "Администратор уже уведомлен."
                    )
            
            conn.commit()
            conn.close()
            
        except Exception as e:
            logger.error(f"Error handling text for application: {e}")
            bot.reply_to(message, "❌ Ошибка при обработке заявки")
