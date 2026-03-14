--KEYS[1]=stockKey, KEYS[2]=buyKey
--ARGV[1]=userId, ARGV[2]=qty

local stockKey = KEYS[1]
local buyKey = KEYS[2]

local userId = ARGV[1]
local qty = tonumber(ARGV[2])

-- 先拿到该用户当前已占用的购买数量。
local cur = redis.call('HGET', buyKey, userId)
cur = tonumber(cur) or 0
if cur < qty then
    -- 回补数量不应超过当前占用数，返回 -1 供上层记录异常。
    return -1
end

-- 返还库存并同步扣减该用户的累计占用数。
redis.call('INCRBY', stockKey, qty);
redis.call('HINCRBY', buyKey, userId, -qty);
return 0
