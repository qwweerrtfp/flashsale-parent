wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
local path = "/orders"

math.randomseed(os.time())
local productId = 1

function request()
  local uid = math.random(1, 1000000000)
  wrk.headers["X-User-Id"] = tostring(uid)
  local body = string.format('{"productId":%d,"quantity":1}', productId)
  return wrk.format(nil, path, nil, body)
end