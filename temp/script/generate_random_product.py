#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
向 flash_product 表插入 mock 数据（只写入“无默认值”的列）：
  只插入：title, description, flash_price_cents, origin_price_cents, stock, start_time, end_time
  不插入（走默认值）：subtitle, images, sold, limit_per_user, status, version, create_time, update_time

示例：
  python insert_mock_flash_products_mincols.py \
    --host 127.0.0.1 --port 3306 \
    --user root --password 123456 --database fs_user \
    --count 10000 --batch-size 1000
"""

import argparse
import os
import random
from datetime import datetime, timedelta

import pymysql


def rand_sentence(min_words=6, max_words=14) -> str:
    words = ["限时", "爆款", "直降", "高性能", "旗舰", "热卖", "轻薄", "耐用", "舒适", "专业",
             "人气", "品质", "补贴", "必买", "口碑", "严选", "到手价", "限量", "抢购", "清仓"]
    n = random.randint(min_words, max_words)
    return "，".join(random.sample(words, k=min(n, len(words)))) + "。"


def build_price() -> tuple[int, int]:
    """返回 (flash_price_cents, origin_price_cents)，确保 flash < origin。"""
    origin = random.randint(50, 2_000_000)  # 50 ~ 2,000,000.00 分（即 0.5 元~20000 元）
    discount = random.uniform(0.05, 0.9)
    flash = max(100, int(origin * discount))
    flash = min(flash, origin - 1)
    return flash, origin


def build_times_draft() -> tuple[datetime, datetime]:
    """
    因为 status 走默认值=1(DRAFT)，时间给未来段，保证语义一致：
      start_time: 未来 1~15 天内
      end_time:   start_time + 30~120 分钟
    """
    now = datetime.now()
    start = now + timedelta(days=random.randint(1, 15),
                            hours=random.randint(0, 23),
                            minutes=random.randint(0, 59))
    end = start + timedelta(minutes=random.randint(30, 120))
    return start, end


def build_row(idx: int) -> tuple:
    title = f"限时秒杀·{random.choice(['手机','耳机','键盘','路由器','显示器','行李箱','跑鞋','咖啡机'])} #{idx:05d}"
    description = f"{title} —— {rand_sentence(10, 20)}支持7天无理由，售后无忧。"
    flash_cents, origin_cents = build_price()
    stock = random.randint(50, 5000)
    start_time, end_time = build_times_draft()
    return (title, description, flash_cents, origin_cents, stock, start_time, end_time)


def main():
    parser = argparse.ArgumentParser(description="Insert mock flash_product rows with minimal non-default columns.")
    parser.add_argument("--host", default=os.getenv("DB_HOST", "127.0.0.1"), help="数据库主机地址")
    parser.add_argument("--port", type=int, default=int(os.getenv("DB_PORT", 3306)), help="数据库端口")
    parser.add_argument("--user", default=os.getenv("DB_USER", "root"), help="数据库用户名")
    parser.add_argument("--password", default=os.getenv("DB_PASSWORD", "whc7777777"), help="数据库密码")
    parser.add_argument("--database", default=os.getenv("DB_NAME", "fs_product"), help="数据库名")
    parser.add_argument("--count", type=int, default=1000, help="要插入的条数")
    parser.add_argument("--batch-size", type=int, default=1000, help="每批提交条数")
    parser.add_argument("--table", default="flash_product", help="目标表名")
    parser.add_argument("--seed", type=int, default=None, help="随机种子（可复现）")
    args = parser.parse_args()

    if args.seed is not None:
        random.seed(args.seed)

    conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
        cursorclass=pymysql.cursors.Cursor,
    )

    # 只包含“无默认值”的列
    sql = f"""
    INSERT INTO {args.table}
      (title, description, flash_price_cents, origin_price_cents, stock, start_time, end_time)
    VALUES
      (%s, %s, %s, %s, %s, %s, %s)
    """

    try:
        with conn.cursor() as cur:
            total, batch = args.count, args.batch_size
            i = 0
            while i < total:
                j = min(i + batch, total)
                rows = [build_row(k) for k in range(i, j)]
                cur.executemany(sql, rows)
                conn.commit()
                print(f"Committed rows {i} ~ {j - 1}")
                i = j

        print(f"✅ Done. Inserted {args.count} rows into `{args.table}`（默认列均由数据库填充）。")

    except Exception:
        conn.rollback()
        print("❌ 出错，已回滚本批次。")
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()