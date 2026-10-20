-- KEYS[1] ready, KEYS[2] processing, KEYS[3] task, KEYS[4] status, KEYS[5] queued zset
-- ARGV[1] executionId, ARGV[2] taskJson, ARGV[3] statusJson, ARGV[4] contentHash
-- ARGV[5] capacity, ARGV[6] taskTtlSeconds, ARGV[7] statusTtlSeconds, ARGV[8] nowMillis, ARGV[9] fingerprint
local ready = KEYS[1]
local processing = KEYS[2]
local taskKey = KEYS[3]
local statusKey = KEYS[4]
local queued = KEYS[5]
local contentHash = ARGV[4]

if redis.call('EXISTS', taskKey) == 1 then
  local existingHash = redis.call('HGET', taskKey .. ':meta', 'contentHash')
  if existingHash == contentHash then
    return {1, 'IDEMPOTENT'}
  end
  return {0, 'CONFLICT'}
end

local readyLen = redis.call('LLEN', ready)
local processingLen = redis.call('LLEN', processing)
if readyLen + processingLen >= tonumber(ARGV[5]) then
  return {0, 'QUEUE_FULL'}
end

redis.call('SET', taskKey, ARGV[2], 'EX', tonumber(ARGV[6]))
redis.call('HSET', taskKey .. ':meta', 'contentHash', contentHash, 'fingerprint', ARGV[9])
redis.call('EXPIRE', taskKey .. ':meta', tonumber(ARGV[6]))
redis.call('SET', statusKey, ARGV[3], 'EX', tonumber(ARGV[7]))
redis.call('ZADD', queued, ARGV[8], ARGV[1])
redis.call('LPUSH', ready, ARGV[1])
return {1, 'ENQUEUED'}
