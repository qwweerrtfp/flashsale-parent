-- ARGV[1]=productId, ARGV[2]=userId, ARGV[3]=qty, ARGV[4]=limitPerUser

local pid   = ARGV[1]
local uid   = ARGV[2]
local qty   = tonumber(ARGV[3])
local limit = tonumber(ARGV[4]) or 999999

local stockKey = 'flash:stock:' .. pid          -- String: 剩余库存
local buyKey   = 'flash:buy:' .. pid         -- Hash:   uid -> 用户累计购买数

-- 1) 校验库存是否足够
local stock = tonumber(redis.call('GET', stockKey) or '0')
if stock < qty then
  return 1   -- 库存不足
end

-- 2) 校验每人限购：累计购买数 + 本次数量 <= limit
local bought = tonumber(redis.call('HGET', buyKey, uid) or '0')
if (bought + qty) > limit then
  return 2   -- 超过每人限购
end

-- 3) 通过：扣库存 & 增加累计购买数（原子）
redis.call('DECRBY', stockKey, qty)
redis.call('HINCRBY', buyKey, uid, qty)
return 0