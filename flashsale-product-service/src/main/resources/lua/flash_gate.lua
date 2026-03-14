-- KEYS[1]=stockKey, KEYS[2]=buyKey
-- ARGV[1]=userId, ARGV[2]=qty, ARGV[3]=limitPerUser

-- 库存 key 使用 String 保存剩余库存。
local stockKey = KEYS[1]
-- 用户累计购买数使用 Hash，field=userId，value=累计已占用数量。
local buyKey   = KEYS[2]

local uid   = ARGV[1]
local qty   = tonumber(ARGV[2])
local limit = tonumber(ARGV[3]) or 999999

-- 1) 先判断库存是否足够。
local stock = tonumber(redis.call('GET', stockKey) or '0')
if stock < qty then
  return 1   -- 库存不足
end

-- 2) 再判断本次购买后是否会超出限购。
local bought = tonumber(redis.call('HGET', buyKey, uid) or '0')
if (bought + qty) > limit then
  return 2   -- 超过每人限购
end

-- 3) 在同一段 Lua 中完成扣库存和累计加数，确保原子性。
redis.call('DECRBY', stockKey, qty)
redis.call('HINCRBY', buyKey, uid, qty)
return 0
