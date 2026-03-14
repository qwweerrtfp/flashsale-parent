# 值得秒（FlashSale）

## 技术概述

- **架构**：Nacos(注册) + Gateway(鉴权/路由) + OpenFeign(RPC) + RabbitMQ(异步/延迟)  
- **热点链路**：Redis **Lua 闸口**（库存校验 + 限购累计 + 预扣减，原子）  
- **下单**：Lua 通过 → **同步落库**（单行 INSERT）→ 发送“**延迟关单**”消息  
- **支付**：Order 发起“支付”命令 → Payment **扣款成功** → 发 **支付成功事件** → Order **标记已支付**（并可 RPC **扣 DB 库存**）  
- **缓存**：统一 CacheClient（逻辑过期/负缓存/穿击穿治理/afterCommit 刷新）  
- **重试**：统一 **DLX+重试队列**（回原队列 2 次）+ 全局 **DLT**（最终失败）  
- **性能（单实例）**：  
  - Lua 闸口：**~20.3k QPS**，p99 ≈ **23.5ms**  
  - 下单接口（Lua+1次 Feign+1次 INSERT+发送延迟消息）：**~6.7k QPS**，P90 ≈ **71ms** / P99 ≈ **126ms**（16 线程/400 连接/60s，`wrk`）

---

## 模块与端口

| 模块 | 职责 | 典型端口 |
|---|---|---|
| gateway-service | JWT 鉴权、路由转发、限流（可选） | 9000 |
| user-service | 登录/发码/用户资料 | 9010 |
| product-service | 商品查询、Lua 闸口、库存回补/扣减 | 9020 |
| order-service | 下单、取消、超时、支付编排、事件消费 | 9030 |
| payment-service | 钱包账户、扣款、支付成功事件 | 9040 |
| nacos | 服务注册/配置 | 8848 |
| rabbitmq | 消息中间件 | 5672/15672 |
| mysql | 数据库 | 3306 |
| redis | 缓存中间件 | 6379 |

---

## 关键数据结构（Redis）

- `flash:stock:{productId}`：**String**，剩余库存  
- `flash:buy:{productId}`：**Hash**，`uid → 累计购买数`  
- `cache:product:{id}`：**String(JSON)**，商品快照（逻辑过期），示例：
  ```json
  {
    "data": {
      "id": 4,
      "title": "限时秒杀·键盘 #00003",
      "flashPriceCents": 2175,
      "originPriceCents": 2501,
      "stock": 2,
      "sold": 0,
      "limitPerUser": 1,
      "startTime": "2025-09-30T15:59:41",
      "endTime": "2027-11-17T03:59:41",
      "status": 2
    },
    "expireAt": 1759225037658
  }
