#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量插入 user_account 表的 mock 数据。

用法示例：
  python insert_mock_users.py \
    --host 127.0.0.1 --port 3306 \
    --user root --password 123456 --database demo \
    --count 10000 --batch-size 1000

表结构要求（MySQL/MariaDB）与题主提供一致：
  create table user_account (...)
  unique (phone)
"""

import argparse
import hashlib
import os
import random
import string
import time
from datetime import datetime, timedelta

import pymysql


def gen_phone(i: int, seed: int) -> str:
    """
    生成 11 位中国大陆风格手机号，确保当前批次内唯一：
    格式：199 + 2位seed + 6位序号
    例如：199 37 000123
    """
    return f"199{seed:02d}{i:06d}"  # 3+2+6 = 11 位


def maybe_password(i: int) -> str | None:
    """
    20% 概率返回 None（表示无密码），其余返回带盐的 sha256 哈希：
    格式：sha256$<salt>$<hex>
    """
    if random.random() < 0.2:
        return None
    salt = ''.join(random.choices(string.ascii_letters + string.digits, k=8))
    raw = f"Passw0rd!_{i}_{salt}".encode("utf-8")
    digest = hashlib.sha256(raw).hexdigest()
    return f"sha256${salt}${digest}"


def random_datetimes(days_back: int = 365) -> tuple[datetime, datetime]:
    """
    随机生成 create_time 与 update_time：
    - create_time：最近 days_back 天内的任意时间
    - update_time：create_time 之后 0~30 天内的任意时间（有时等于 create_time）
    """
    now = datetime.now()
    create_time = now - timedelta(
        days=random.randint(0, days_back),
        hours=random.randint(0, 23),
        minutes=random.randint(0, 59),
        seconds=random.randint(0, 59),
    )
    update_time = create_time + timedelta(days=random.randint(0, 30),
                                          hours=random.randint(0, 23),
                                          minutes=random.randint(0, 59),
                                          seconds=random.randint(0, 59))
    if update_time > now:
        update_time = now
    return create_time, update_time


def build_row(i: int, seed: int) -> tuple:
    phone = gen_phone(i, seed)
    password_hash = maybe_password(i)
    nickname = f"user_{seed:02d}_{i:06d}"
    # 头像留空，使用列默认值也行；这里演示填一个可辨识的占位
    avatar_url = ""
    status = 1 if random.random() < 0.95 else 0
    create_time, update_time = random_datetimes(365)
    return (phone, password_hash, nickname, avatar_url, status, create_time, update_time)


def main():
    parser = argparse.ArgumentParser(description="Insert mock users into user_account.")
    parser.add_argument("--host", default=os.getenv("DB_HOST", "127.0.0.1"), help="数据库主机地址")
    parser.add_argument("--port", type=int, default=int(os.getenv("DB_PORT", 3306)), help="数据库端口")
    parser.add_argument("--user", default=os.getenv("DB_USER", "root"), help="数据库用户名")
    parser.add_argument("--password", default=os.getenv("DB_PASSWORD", "whc7777777"), help="数据库密码")
    parser.add_argument("--database", default=os.getenv("DB_NAME", "fs_user"), help="数据库名")
    parser.add_argument("--count", type=int, default=10, help="要插入的条数")
    parser.add_argument("--batch-size", type=int, default=1, help="每批提交条数")
    parser.add_argument("--table", default="user_account", help="目标表名")
    args = parser.parse_args()

    # 2 位 seed，尽量避免与历史运行的手机号段冲突（仍建议你手动调整号段以完全规避碰撞）
    seed = (int(time.time()) % 90) + 10  # 10~99

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

    sql = f"""
    INSERT INTO {args.table}
        (phone, password_hash, nickname, avatar_url, status, create_time, update_time)
    VALUES
        (%s, %s, %s, %s, %s, %s, %s)
    """

    try:
        with conn.cursor() as cur:
            total = args.count
            batch = args.batch_size
            start_i = 0

            while start_i < total:
                end_i = min(start_i + batch, total)
                rows = [build_row(i, seed) for i in range(start_i, end_i)]
                cur.executemany(sql, rows)
                conn.commit()
                print(f"Committed rows {start_i} ~ {end_i - 1}")
                start_i = end_i

        print(f"✅ Done. Inserted {args.count} rows into `{args.table}`.")
        print(f"📱 Phone range seed={seed:02d}, e.g. {gen_phone(0, seed)} ~ {gen_phone(args.count - 1, seed)}")

    except pymysql.err.IntegrityError as e:
        # 最常见是唯一键冲突（手机号已存在）
        conn.rollback()
        print("❌ IntegrityError（可能是手机号唯一键冲突）。已回滚本批次。")
        print(f"Error: {e}")
        print("解决思路：\n  1) 换一个 seed（--seed 未暴露，直接重跑即可自动变更；或改 gen_phone 的号段逻辑）\n  2) 或清理表中已有的冲突数据。")
    except Exception as e:
        conn.rollback()
        print("❌ 出错，已回滚。")
        print(repr(e))
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()