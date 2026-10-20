-- KEYS[1] task, KEYS[2] claim, KEYS[3] status, KEYS[4] queued zset
-- ARGV[1] executionId, ARGV[2] runnerId, ARGV[3] attemptToken, ARGV[4] attemptGeneration
-- ARGV[5] leaseExpiryEpochSeconds, ARGV[6] processingStatusJson, ARGV[7] ttlSeconds
local taskKey = KEYS[1]
local claimKey = KEYS[2]
local statusKey = KEYS[3]
local queued = KEYS[4]

if redis.call('EXISTS', taskKey) == 0 then
  return {0, 'NO_TASK'}
end

local claimJson = ARGV[2] .. '|' .. ARGV[3] .. '|' .. ARGV[4] .. '|' .. ARGV[5]
redis.call('SET', claimKey, claimJson, 'EX', tonumber(ARGV[7]))
redis.call('SET', statusKey, ARGV[6], 'EX', tonumber(ARGV[7]))
redis.call('ZREM', queued, ARGV[1])
return {1, 'CLAIMED'}
