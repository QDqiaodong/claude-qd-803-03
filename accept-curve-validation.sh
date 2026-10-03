#!/usr/bin/env bash
# 工艺曲线「草稿校验与保存同口径」验收脚本。用法：BASE=http://localhost:8283 ./accept-curve-validation.sh
# 覆盖：换布种后预检按草稿拦截、切回原布种重算、预检不落库、失败保留可继续编辑、
#       保存与刷新一致、零时长/升温超限/温度上限/布种档案缺失均指出节点与原因。
set -u

BASE="${BASE:-http://localhost:8283}"
API="$BASE/api"
CODE="PC-ACC-$(date +%s)"

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

# 合规节点（全棉 3.00℃/min 内）：30→60/20min=1.50，保温，60→45/15min
NODES_OK='[{"stage":"RAMP","targetTemp":60,"durationMin":20},{"stage":"HOLD","targetTemp":60,"durationMin":15},{"stage":"COOL","targetTemp":45,"durationMin":15}]'
# 真丝合规节点（≤1.00℃/min）：30→60/40min=0.75
NODES_SILK_OK='[{"stage":"RAMP","targetTemp":60,"durationMin":40},{"stage":"HOLD","targetTemp":60,"durationMin":15},{"stage":"COOL","targetTemp":45,"durationMin":15}]'

echo "0. 建验收专用曲线 $CODE（COTTON / 起始 30℃）"
created=$(request POST /curves "{\"curveCode\":\"$CODE\",\"curveName\":\"验收-草稿口径\",\"fabricType\":\"COTTON\",\"startTemp\":30,\"nodes\":$NODES_OK}")
echo "$created" | grep -q '"ok":true' || fail "创建曲线失败：$created"
CID=$(echo "$created" | json_field 'd["data"]["id"]')
echo "$created" | grep -q '"fabricType":"COTTON"' || fail "创建后布种异常：$created"

echo "1. 草稿改真丝 → 预检按新布种（1.00℃/min）拦截，指出第几段与原因"
db_before=$(request GET "/curves/$CID")
v=$(request POST "/curves/$CID/validate" "{\"fabricType\":\"SILK\",\"startTemp\":30,\"nodes\":$NODES_OK}")
echo "$v" | grep -q '"valid":false' || fail "预检未按真丝拦截：$v"
echo "$v" | grep -q '第 1 段升温速率 1.50℃/min 超过布种 真丝（SILK） 允许上限 1.00℃/min' || fail "预检未指出段与原因：$v"
echo "$v" | grep -q '"fabricType":"SILK"' || fail "预检未按草稿布种口径返回：$v"

echo "2. 预检不落库：预检前后库内数据完全一致"
db_after=$(request GET "/curves/$CID")
[ "$db_before" = "$db_after" ] || fail "预检后库内数据发生变化：$db_after"
echo "$db_after" | grep -q '"fabricType":"COTTON"' || fail "预检后库内布种被改：$db_after"
echo "$db_after" | grep -q '"totalMinutes":50' || fail "预检后库内总时长被改：$db_after"

echo "3. 同一草稿保存 → 按新布种拒绝，文案与预检一致，且库内不变"
s=$(request PUT "/curves/$CID/nodes" "{\"fabricType\":\"SILK\",\"startTemp\":30,\"nodes\":$NODES_OK}")
echo "$s" | grep -q '"ok":false' || fail "保存未被拒绝：$s"
echo "$s" | grep -q '第 1 段升温速率 1.50℃/min 超过布种 真丝（SILK） 允许上限 1.00℃/min' || fail "保存拒绝文案与预检不一致：$s"
save_after=$(request GET "/curves/$CID")
[ "$db_before" = "$save_after" ] || fail "保存失败后库内数据发生变化：$save_after"

echo "4. 失败后可继续编辑：切回原布种重算 → 预检放行"
v2=$(request POST "/curves/$CID/validate" "{\"fabricType\":\"COTTON\",\"startTemp\":30,\"nodes\":$NODES_OK}")
echo "$v2" | grep -q '"valid":true' || fail "切回原布种未重算放行：$v2"

echo "5. 零时长节点指出节点与原因"
v3=$(request POST "/curves/$CID/validate" '{"fabricType":"COTTON","startTemp":30,"nodes":[{"stage":"RAMP","targetTemp":60,"durationMin":20},{"stage":"HOLD","targetTemp":60,"durationMin":0}]}')
echo "$v3" | grep -q '第 2 个节点的时长必须大于 0 分钟' || fail "零时长未指出节点：$v3"
s3=$(request PUT "/curves/$CID/nodes" '{"fabricType":"COTTON","startTemp":30,"nodes":[{"stage":"RAMP","targetTemp":60,"durationMin":20},{"stage":"HOLD","targetTemp":60,"durationMin":0}]}')
echo "$s3" | grep -q '第 2 个节点的时长必须大于 0 分钟' || fail "保存链路零时长文案不一致：$s3"

echo "6. 温度上限指出节点与原因（真丝保温上限 95℃）"
v4=$(request POST "/curves/$CID/validate" '{"fabricType":"SILK","startTemp":30,"nodes":[{"stage":"RAMP","targetTemp":60,"durationMin":40},{"stage":"HOLD","targetTemp":98,"durationMin":15}]}')
echo "$v4" | grep -q '第 2 个节点温度 98℃ 超过布种 真丝（SILK） 允许的保温上限 95.0℃' || fail "温度上限未指出节点与原因：$v4"

echo "7. 布种档案缺失指出布种与原因"
v5=$(request POST "/curves/$CID/validate" "{\"fabricType\":\"GHOST\",\"startTemp\":30,\"nodes\":$NODES_OK}")
echo "$v5" | grep -q '"ok":false' || fail "缺失档案未报错：$v5"
echo "$v5" | grep -q '布种 GHOST 缺少有效工艺档案' || fail "缺失档案未指出布种：$v5"

echo "8. 起始温度草稿进入校验（30℃→2.50 放行；草稿 10℃→4.17 拦截）"
NODES_12='[{"stage":"RAMP","targetTemp":60,"durationMin":12}]'
v6=$(request POST "/curves/$CID/validate" "{\"fabricType\":\"COTTON\",\"startTemp\":30,\"nodes\":$NODES_12}")
echo "$v6" | grep -q '"valid":true' || fail "起始 30℃ 应放行：$v6"
v7=$(request POST "/curves/$CID/validate" "{\"fabricType\":\"COTTON\",\"startTemp\":10,\"nodes\":$NODES_12}")
echo "$v7" | grep -q '"valid":false' || fail "起始 10℃ 应拦截：$v7"
echo "$v7" | grep -q '4.17' || fail "拦截未体现草稿起始温度算出的速率：$v7"

echo "9. 保存成功后才刷新：真丝合规草稿保存 → 库内与刷新一致"
s9=$(request PUT "/curves/$CID/nodes" "{\"fabricType\":\"SILK\",\"startTemp\":30,\"nodes\":$NODES_SILK_OK}")
echo "$s9" | grep -q '"ok":true' || fail "合规草稿保存失败：$s9"
fresh=$(request GET "/curves/$CID")
echo "$fresh" | grep -q '"fabricType":"SILK"' || fail "刷新后布种未更新：$fresh"
echo "$fresh" | grep -q '"totalMinutes":70' || fail "刷新后总时长不符：$fresh"
echo "$fresh" | grep -q '"maxRampRate":1.00' || fail "刷新后峰值速率不符：$fresh"
echo "$fresh" | grep -q '"durationMin":40' || fail "刷新后节点序列不符：$fresh"

echo "✅ 工艺曲线草稿口径验收完成（曲线 $CODE 保留为真丝合规版本）"
