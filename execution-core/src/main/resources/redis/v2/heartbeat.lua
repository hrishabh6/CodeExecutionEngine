-- KEYS[1] claim
-- ARGV[1] runnerId, ARGV[2] attemptToken, ARGV[3] newLeaseExpiryEpochSeconds, ARGV[4] ttlSeconds
local raw = redis.call('GET', KEYS[1])
if not raw then
  return {0, 'NO_CLAIM'}
end
local parts = {}
for part in string.gmatch(raw, '([^|]+)') do
  table.insert(parts, part)
end
if parts[1] ~= ARGV[1] or parts[2] ~= ARGV[2] then
  return {0, 'STALE'}
end
local updated = parts[1] .. '|' .. parts[2] .. '|' .. parts[3] .. '|' .. ARGV[3]
redis.call('SET', KEYS[1], updated, 'EX', tonumber(ARGV[4]))
return {1, 'OK'}
