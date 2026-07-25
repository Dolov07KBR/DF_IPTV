"""Telegram-бот для продажи VPN-подписок.

Функции:
- каталог тарифов;
- создание заказа;
- подтверждение оплаты администратором;
- выдача ключа/инструкции клиенту;
- хранение заказов в SQLite.
"""

from __future__ import annotations

import logging
import os
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Dict, Optional

from telegram import InlineKeyboardButton, InlineKeyboardMarkup, Update
from telegram.ext import (
    Application,
    CallbackQueryHandler,
    CommandHandler,
    ContextTypes,
    MessageHandler,
    filters,
)

logging.basicConfig(
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    level=logging.INFO,
)
logger = logging.getLogger(__name__)

DB_PATH = os.getenv("DB_PATH", "vpn_bot.sqlite3")
ADMIN_CHAT_ID = int(os.getenv("ADMIN_CHAT_ID", "0"))


@dataclass(frozen=True)
class Plan:
    code: str
    title: str
    price: int
    duration_days: int


PLANS: Dict[str, Plan] = {
    "1m": Plan("1m", "1 месяц", 299, 30),
    "3m": Plan("3m", "3 месяца", 799, 90),
    "12m": Plan("12m", "12 месяцев", 2499, 365),
}


def get_db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> None:
    with get_db() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                username TEXT,
                plan_code TEXT NOT NULL,
                amount INTEGER NOT NULL,
                status TEXT NOT NULL,
                created_at TEXT NOT NULL,
                payment_proof TEXT,
                vpn_key TEXT
            )
            """
        )
        conn.commit()


def create_order(user_id: int, username: str, plan: Plan) -> int:
    with get_db() as conn:
        cur = conn.execute(
            """
            INSERT INTO orders(user_id, username, plan_code, amount, status, created_at)
            VALUES (?, ?, ?, ?, 'pending_payment', ?)
            """,
            (user_id, username, plan.code, plan.price, datetime.now(timezone.utc).isoformat()),
        )
        conn.commit()
        return int(cur.lastrowid)


def set_payment_proof(order_id: int, proof: str) -> None:
    with get_db() as conn:
        conn.execute(
            "UPDATE orders SET payment_proof = ?, status = 'paid_unconfirmed' WHERE id = ?",
            (proof, order_id),
        )
        conn.commit()


def set_order_approved(order_id: int, vpn_key: str) -> Optional[sqlite3.Row]:
    with get_db() as conn:
        conn.execute(
            "UPDATE orders SET status = 'approved', vpn_key = ? WHERE id = ?",
            (vpn_key, order_id),
        )
        conn.commit()
        row = conn.execute("SELECT * FROM orders WHERE id = ?", (order_id,)).fetchone()
    return row


def get_order(order_id: int) -> Optional[sqlite3.Row]:
    with get_db() as conn:
        return conn.execute("SELECT * FROM orders WHERE id = ?", (order_id,)).fetchone()


async def start(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    text = (
        "Привет! 👋\n"
        "Я бот для продажи VPN-подписок.\n\n"
        "Команды:\n"
        "/plans — посмотреть тарифы\n"
        "/myorders — мои заказы"
    )
    await update.message.reply_text(text)


async def plans(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    buttons = [
        [InlineKeyboardButton(f"{p.title} — {p.price} ₽", callback_data=f"buy:{p.code}")]
        for p in PLANS.values()
    ]
    await update.message.reply_text(
        "Выберите тариф:", reply_markup=InlineKeyboardMarkup(buttons)
    )


async def buy_callback(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()

    _, plan_code = query.data.split(":", 1)
    plan = PLANS.get(plan_code)
    if not plan:
        await query.edit_message_text("Тариф не найден.")
        return

    order_id = create_order(
        user_id=query.from_user.id,
        username=query.from_user.username or "",
        plan=plan,
    )
    context.user_data["awaiting_proof_for_order"] = order_id

    await query.edit_message_text(
        "Заказ создан ✅\n"
        f"Номер заказа: #{order_id}\n"
        f"Сумма к оплате: {plan.price} ₽\n\n"
        "Отправьте скриншот или текст с подтверждением оплаты в этот чат."
    )


async def on_payment_proof(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    order_id = context.user_data.get("awaiting_proof_for_order")
    if not order_id:
        return

    order = get_order(order_id)
    if not order or order["status"] != "pending_payment":
        context.user_data.pop("awaiting_proof_for_order", None)
        await update.message.reply_text("Заказ уже обработан или не найден.")
        return

    proof = update.message.text or "[media]"
    if update.message.photo:
        proof = f"photo_file_id:{update.message.photo[-1].file_id}"

    set_payment_proof(order_id, proof)
    context.user_data.pop("awaiting_proof_for_order", None)

    await update.message.reply_text(
        "Спасибо! Платёж отправлен на проверку администратору."
    )

    if ADMIN_CHAT_ID:
        keyboard = InlineKeyboardMarkup(
            [[InlineKeyboardButton("Подтвердить оплату", callback_data=f"approve:{order_id}")]]
        )
        await context.bot.send_message(
            chat_id=ADMIN_CHAT_ID,
            text=(
                "Новый платёж на проверку:\n"
                f"Заказ #{order_id}\n"
                f"Пользователь: @{order['username']} ({order['user_id']})\n"
                f"Тариф: {order['plan_code']}\n"
                f"Сумма: {order['amount']} ₽\n"
                f"Подтверждение: {proof}"
            ),
            reply_markup=keyboard,
        )


async def approve_callback(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    query = update.callback_query
    await query.answer()

    if query.from_user.id != ADMIN_CHAT_ID:
        await query.edit_message_text("Недостаточно прав.")
        return

    _, raw_order_id = query.data.split(":", 1)
    order_id = int(raw_order_id)
    key = f"vpn://example-config/order-{order_id}"
    order = set_order_approved(order_id, key)

    if not order:
        await query.edit_message_text("Заказ не найден.")
        return

    await context.bot.send_message(
        chat_id=order["user_id"],
        text=(
            f"Оплата заказа #{order_id} подтверждена ✅\n"
            "Ваш VPN-ключ:\n"
            f"{key}\n\n"
            "Инструкция: импортируйте ключ в приложение и подключитесь."
        ),
    )
    await query.edit_message_text(f"Заказ #{order_id} подтверждён и ключ отправлен клиенту.")


async def my_orders(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    with get_db() as conn:
        rows = conn.execute(
            "SELECT id, plan_code, amount, status, created_at FROM orders WHERE user_id = ? ORDER BY id DESC LIMIT 10",
            (update.effective_user.id,),
        ).fetchall()

    if not rows:
        await update.message.reply_text("У вас пока нет заказов.")
        return

    lines = ["Ваши последние заказы:"]
    for row in rows:
        lines.append(
            f"#{row['id']} | {row['plan_code']} | {row['amount']} ₽ | {row['status']} | {row['created_at'][:10]}"
        )

    await update.message.reply_text("\n".join(lines))


def main() -> None:
    token = os.getenv("BOT_TOKEN")
    if not token:
        raise RuntimeError("Не задан BOT_TOKEN")

    init_db()

    app = Application.builder().token(token).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CommandHandler("plans", plans))
    app.add_handler(CommandHandler("myorders", my_orders))
    app.add_handler(CallbackQueryHandler(buy_callback, pattern=r"^buy:"))
    app.add_handler(CallbackQueryHandler(approve_callback, pattern=r"^approve:"))
    app.add_handler(MessageHandler(filters.TEXT | filters.PHOTO, on_payment_proof))

    app.run_polling()


if __name__ == "__main__":
    main()
