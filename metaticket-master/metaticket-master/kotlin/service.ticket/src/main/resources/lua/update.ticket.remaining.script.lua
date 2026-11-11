-- Atomically updates the remaining ticket capacity in Redis.
-- This script is used to increase or decrease the number of available tickets
-- and ensures that the capacity never drops below zero.
--
-- @param KEYS[1] The Redis key for the remaining ticket capacity.
-- @param ARGV[1] The quantity to change the capacity by (can be a negative number to decrease).
--
-- @return The new remaining capacity if the update is successful.
-- @return -1 if the operation would result in a negative capacity (insufficient tickets) or if the Redis 'set' command fails.
-- @return -999 if the ticket capacity key is not found.

local remCap = redis.call('get', KEYS[1]);
if not remCap then return -999; end

remCap = tonumber(remCap)
local qty = tonumber(ARGV[1])
local tmp = remCap + qty

if tmp < 0 then return -1; end

if redis.call('set', KEYS[1], tmp) then return tmp; end
return -1;