-- Atomically updates the status of a reservation in Redis.
-- This script ensures that the status is only updated if the current status
-- matches the expected old status, preventing race conditions.
--
-- @param KEYS[1] The key for the reservation object in Redis.
-- @param ARGV[1] The expected current status (JSON encoded).
-- @param ARGV[2] The new status to set (JSON encoded).
--
-- @return 0 if the update was successful.
-- @return -1 if the update failed (e.g., old status mismatch, invalid input).
-- @return -999 if the reservation key was not found.

local id = KEYS[1];
local inputOldStatus = cjson.decode(ARGV[1]);
local newStatus = cjson.decode(ARGV[2]);
local reservation = redis.call('get', id);
if reservation == false then return -999; end;
if inputOldStatus == false then return -1; end;
if newStatus == false then return -1; end
local obj = cjson.decode(reservation);
local oldStatus = obj.status;
if oldStatus ~= inputOldStatus then return -1; end;
obj.status = newStatus;
obj.version = obj.version+1;
local encObj = cjson.encode(obj);
if redis.call('set', KEYS[1], encObj) then return 0; end;
return -1;