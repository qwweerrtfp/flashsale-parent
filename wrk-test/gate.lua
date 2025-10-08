-- wrk -t8 -c200 -d60s -s gate.lua http://localhost:8081
wrk.method = "POST"
wrk.headers["Content-Type"] = "application/x-www-form-urlencoded"
local path = "/products/gate-purchase"

math.randomseed(os.time())
local productId = 1

function request()
  local uid = math.random(1, 1000000000)
  local body = string.format("productId=%d&userId=%d&quantity=1", productId, uid)
  return wrk.format(nil, path, nil, body)
end