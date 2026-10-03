#!/usr/bin/env bash
# 开缸执行单接口验收脚本。用法：BASE=http://localhost:8283 ./accept-execution-orders.sh
# 需要在后端设置 EXECUTION_FAIL_AFTER_PARAMS=true 后重启，才能执行“中途异常”用例。
set -u

BASE="${BASE:-http://localhost:8283}"
API="$BASE/api"
CART_ID="${CART_ID:-1}"
OPERATOR="${OPERATOR:-验收员}"

fail() { echo "❌ $*" >&2; exit 1; }
request() {
  local method=$1 path=$2 body=${3:-}
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" -H 'Content-Type: application/json' -d "$body" "$API$path"
  else
    curl -sS -X "$method" "$API$path"
  fi
}
json_field() { python3 -c 'import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1]))' "$1"; }

echo "1. 正常创建并下达"
preview=$(request POST /execution-orders/preview "{\"cartId\":$CART_ID}")
echo "$preview" | grep -q '"ok":true' || fail "预检失败：$preview"
echo "$preview" | grep -q '"valid":true' || fail "预检未通过：$preview"

created=$(request POST /execution-orders "{\"cartId\":$CART_ID,\"createdBy\":\"$OPERATOR\"}")
echo "$created" | grep -q '"status":"PENDING_CONFIRM"' || fail "未生成待确认单：$created"
order_id=$(echo "$created" | json_field 'd["data"]["id"]')

issued=$(request POST "/execution-orders/$order_id/issue" "{\"confirmedBy\":\"$OPERATOR\"}")
echo "$issued" | grep -q '"status":"ISSUED"' || fail "下达失败：$issued"
echo "$issued" | grep -q 'snapshotJson' || fail "下达后未保存快照"

repeat=$(request POST "/execution-orders/$order_id/issue" "{\"confirmedBy\":\"$OPERATOR\"}")
echo "$repeat" | grep -q '已由' || fail "重复下达未被拒绝：$repeat"

vat_id=$(echo "$issued" | json_field 'd["data"]["vatId"]')
vat=$(request GET "/vats/$vat_id")
echo "$vat" | grep -q '"status":"HEATING"' || echo "⚠ 染缸当前温度已等于/高于目标时可能为 HOLDING/COOLING，请人工确认状态：$vat"

echo "2. 失效复核：修改来源配方或曲线后，对新待确认单执行 GET /execution-orders"
echo "3. 并发重复：并行两次 POST /execution-orders/{id}/issue，应仅一次成功"
echo "4. 中途异常：开启 EXECUTION_FAIL_AFTER_PARAMS=true 后下达，确认 DB 回滚、Redis 恢复"
echo "✅ 脚本基础用例完成"
