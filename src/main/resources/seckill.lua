local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 1. 判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

-- 2. 判断用户是否下单
if(redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 3. 扣减库存
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
-- 4. Send message to the queue
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0