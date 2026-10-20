-- KEYS[1] task, KEYS[2] claim, KEYS[3] status, KEYS[4] result, KEYS[5] processing
-- ARGV[1] executionId, ARGV[2] attemptToken, ARGV[3] resultJson, ARGV[4] statusJson
-- ARGV[5] resultTtlSeconds, ARGV[6] statusTtlSeconds
local claimKey = KEYS[2]
local raw = redis.call('GET', claimKey)
if not raw then
  return {0, 'NO_CLAIM'}
end
local token = nil
for part in string.gmatch(raw, '([^|]+)') do
  if token == nil then
    -- runnerId skipped
  elseif token == 'SECOND' then
    -- attempt token
  end
end
local parts = {}
for part in string.gmatch(raw, '([^|]+)') do
  table.insert(parts, part)
end
if parts[2] ~= ARGV[2] then
  return {0, 'STALE_TOKEN'}
end

local existing = redis.call('GET', KEYS[4])
if existing and existing == ARGV[3] then
  return {1, 'IDEMPOTENT'}
end
if existing and existing ~= ARGV[3] then
  return {0, 'CONFLICTING_TERMINAL'}
end

redis.call('SET', KEYS[4], ARGV[3], 'EX', tonumber(ARGV[5]))
redis.call('SET', KEYS[3], ARGV[4], 'EX', tonumber(ARGV[6]))
redis.call('LREM', KEYS[5], 1, ARGV[1])
redis.call('DEL', KEYS[1])
redis.call('DEL', KEYS[1] .. ':meta')
redis.call('DEL', claimKey)
return {1, 'COMMITTED'}
