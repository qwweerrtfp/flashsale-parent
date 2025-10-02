--KEYS[1]=stockKey, KEYS[2]=buyKey
--ARGV[1]=userId, ARGV[2]=qty

local stockKey = KEYS[1]
local buyKey = KEYS[2]

local userId = ARGV[1]
local qty = tonumber(ARGV[2])

local cur = redis.call('HGET', buyKey, userId)
cur = tonumber(cur) or 0
if cur < qty then
    return -1
end

redis.call('INCRBY', stockKey, qty);
redis.call('HINCRBY', buyKey, userId, -qty);
return 0