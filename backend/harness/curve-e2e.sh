#!/usr/bin/env bash
# 工艺曲线编辑器 · 接口端到端验收（H2 验证实例 :8399）
#
# 验收点：
#  A. 切布种为真丝后预检按新布种（SILK 1.0℃/min、95℃）重算——修复“预检按旧布种通过、保存按新布种拒绝”
#  B. 预检请求携带完整草稿（布种 + 起始温度 + 节点序列），且预检前后数据库不变（不落库）
#  C. 布种档案缺失 / 零时长节点 / 升温超限 / 温度上限 都指出节点序号与原因（可多项并存）
#  D. 失败（预检不通过或保存拒绝）后可继续编辑：修正后再预检/保存成功
#  E. 切回原布种会重算（原上限下通过）
#  F. 保存成功后刷新，GET 与保存返回一致（布种/起始温度/节点均落库）
set -u
B=http://127.0.0.1:8399/api/curves
PASS=0; FAIL=0
ok(){ echo "  [PASS] $1"; PASS=$((PASS+1)); }
bad(){ echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }
jqf(){ python3 -c "import json,sys;d=json.load(sys.stdin);print(eval(\"d$1\"))"; }

CID=1
# 曲线 1 种子：COTTON，起始 30℃；第 1 段 RAMP 30→60℃/20min=1.5℃/min；第 3 段 RAMP 60→95℃/25min=1.4℃/min
# SILK：上限 1.0℃/min，保温上限 95℃（见 data-h2.sql 种子）

snapshot(){ curl -s $B/$CID -o /tmp/curve-before.json && md5sum /tmp/curve-before.json | cut -d' ' -f1; }

echo "== A. 切布种为真丝并拖节点：预检必须按真丝上限（1.0℃/min）重算，不再按全棉放行 =="
# 把第 1 段时长压到 10 分钟：30→60℃/10min = 3.0℃/min（全棉 3.0 可过，真丝 1.0 必超）
cat > /tmp/draft-silk-bad.json <<'EOF'
{"fabricType":"SILK","startTemp":30,"nodes":[
 {"id":1,"stage":"RAMP","targetTemp":60,"durationMin":10,"memo":"入染后慢升"},
 {"id":2,"stage":"HOLD","targetTemp":60,"durationMin":15,"memo":"加盐促染"},
 {"id":3,"stage":"RAMP","targetTemp":95,"durationMin":25,"memo":"升至固色温度"},
 {"id":4,"stage":"HOLD","targetTemp":95,"durationMin":30,"memo":"加碱固色"},
 {"id":5,"stage":"COOL","targetTemp":60,"durationMin":15,"memo":"缓慢降温"},
 {"id":6,"stage":"COOL","targetTemp":45,"durationMin":10,"memo":"降至排液温度"}]}
EOF
BEFORE_MD5=$(snapshot)
V=$(curl -s -X POST $B/$CID/validate -H 'Content-Type: application/json' -d @/tmp/draft-silk-bad.json)
echo "$V" > /tmp/v-silk-bad.json
VALID=$(echo "$V" | jqf "['data']['valid']")
FAB=$(echo "$V" | jqf "['data']['fabricType']")
MAXR=$(echo "$V" | jqf "['data']['maxRampRate']")
SEG1_OVER=$(echo "$V" | jqf "['data']['segments'][0]['overLimit']")
ERR0=$(echo "$V" | jqf "['data']['errors'][0]['nodeIndex']")
[ "$FAB" = SILK ] && [ "$MAXR" = 1.0 ] && [ "$VALID" = False ] && [ "$SEG1_OVER" = True ] && [ "$ERR0" = 1 ] \
  && ok "真丝草稿预检按真丝上限重算：fabricType=SILK、maxRampRate=1.0、valid=false、第1节点升温超限" \
  || bad "预检仍按旧布种口径：fab=$FAB max=$MAXR valid=$VALID seg1over=$SEG1_OVER err0=$ERR0"

# 同样草稿按保存链路必须被拒绝（HTTP 400 + ok:false），且消息点名第 1 个节点与真丝上限
SAVE=$(curl -s -o /tmp/save-bad.json -w '%{http_code}' -X PUT $B/$CID/nodes -H 'Content-Type: application/json' -d @/tmp/draft-silk-bad.json)
SAVE_OK=$(jqf "['ok']" < /tmp/save-bad.json)
SAVE_MSG=$(jqf "['message']" < /tmp/save-bad.json)
[ "$SAVE" = 400 ] && [ "$SAVE_OK" = False ] && echo "$SAVE_MSG" | grep -q "第 1 个节点" && echo "$SAVE_MSG" | grep -q "1.0" \
  && ok "保存链路同口径拒绝（400）并指出第 1 节点/真丝上限：$SAVE_MSG" \
  || bad "保存未按真丝口径拒绝：http=$SAVE ok=$SAVE_OK msg=$SAVE_MSG"

echo "== B. 预检不落库：预检/保存失败前后曲线快照一致（布种仍为 COTTON） =="
AFTER_MD5=$(snapshot)
AFTER_FAB=$(jqf "['data']['fabricType']" < /tmp/curve-before.json)
[ "$BEFORE_MD5" = "$AFTER_MD5" ] && [ "$AFTER_FAB" = COTTON ] \
  && ok "预检与被拒保存均未落库（快照一致，布种仍为 COTTON）" \
  || bad "数据库被预检/失败保存改动：$BEFORE_MD5 vs $AFTER_MD5 fab=$AFTER_FAB"

echo "== C. 四类问题都指出节点与原因，且可同次聚合 =="
# 1) 布种档案缺失
cat > /tmp/draft-nofab.json <<'EOF'
{"fabricType":"GHOST_FABRIC","startTemp":30,"nodes":[
 {"stage":"RAMP","targetTemp":60,"durationMin":20}]}
EOF
V=$(curl -s -X POST $B/$CID/validate -H 'Content-Type: application/json' -d @/tmp/draft-nofab.json)
echo "$V" | jqf "['data']['errors'][0]['code']" | grep -q FABRIC_PROFILE_MISSING \
  && ok "布种档案缺失被指出：$(echo "$V" | jqf "['data']['errors'][0]['message']")" \
  || bad "未指出布种档案缺失：$V"
# 保存链路同样拒绝
SC=$(curl -s -o /dev/null -w '%{http_code}' -X PUT $B/$CID/nodes -H 'Content-Type: application/json' -d @/tmp/draft-nofab.json)
[ "$SC" = 400 ] && ok "档案缺失时保存同样拒绝（400）" || bad "档案缺失保存未拒绝：$SC"

# 2) 零时长 + 3) 升温超限 + 4) 温度上限，同一份真丝草稿三项并存（另带起始温度越限共四项）
cat > /tmp/draft-multi.json <<'EOF'
{"fabricType":"SILK","startTemp":100,"nodes":[
 {"stage":"RAMP","targetTemp":60,"durationMin":0},
 {"stage":"RAMP","targetTemp":120,"durationMin":10}]}
EOF
V=$(curl -s -X POST $B/$CID/validate -H 'Content-Type: application/json' -d @/tmp/draft-multi.json)
echo "$V" > /tmp/v-multi.json
python3 - <<'PYEOF'
import json
d = json.load(open('/tmp/v-multi.json'))['data']
codes = {(e['nodeIndex'], e['code']) for e in d['errors']}
need = {(0,'START_TEMP_OVER_LIMIT'), (1,'ZERO_DURATION'), (2,'RAMP_OVER_LIMIT'), (2,'TEMP_OVER_LIMIT')}
missing = need - codes
assert d['valid'] is False, d
assert not missing, f"缺少问题项: {missing}; 实际: {codes}"
msgs = ' | '.join(e['message'] for e in d['errors'])
for kw in ('第 1 个节点', '第 2 个节点', '起始温度', '120', '大于 0'):
    assert kw in msgs, kw
print("  [PASS] 起始温度/零时长(节点1)/升温超限+温度上限(节点2) 同次聚合，均带节点序号与原因")
PYEOF
[ $? -ne 0 ] && bad "多问题聚合不符合预期" && PASS=$((PASS-1+0))

echo "== D. 失败后可继续编辑：把第 1 段延长到 35 分钟（30→60/35≈0.86℃/min）后预检转通过 =="
cat > /tmp/draft-silk-ok.json <<'EOF'
{"fabricType":"SILK","startTemp":30,"nodes":[
 {"id":1,"stage":"RAMP","targetTemp":60,"durationMin":35,"memo":"入染后慢升"},
 {"id":2,"stage":"HOLD","targetTemp":60,"durationMin":15,"memo":"加盐促染"},
 {"id":3,"stage":"RAMP","targetTemp":95,"durationMin":40,"memo":"升至固色温度"},
 {"id":4,"stage":"HOLD","targetTemp":95,"durationMin":30,"memo":"加碱固色"},
 {"id":5,"stage":"COOL","targetTemp":60,"durationMin":15,"memo":"缓慢降温"},
 {"id":6,"stage":"COOL","targetTemp":45,"durationMin":10,"memo":"降至排液温度"}]}
EOF
V=$(curl -s -X POST $B/$CID/validate -H 'Content-Type: application/json' -d @/tmp/draft-silk-ok.json)
VALID=$(echo "$V" | jqf "['data']['valid']")
[ "$VALID" = True ] && ok "放缓升温后预检通过（编辑内容保留并可继续调整）" || bad "放缓后仍不通过：$V"

echo "== E. 切回原布种会重算：同一份 10 分钟快升草稿，COTTON 下预检通过 =="
cat > /tmp/draft-cotton.json <<'EOF'
{"fabricType":"COTTON","startTemp":30,"nodes":[
 {"id":1,"stage":"RAMP","targetTemp":60,"durationMin":10,"memo":"入染后慢升"},
 {"id":2,"stage":"HOLD","targetTemp":60,"durationMin":15,"memo":"加盐促染"},
 {"id":3,"stage":"RAMP","targetTemp":95,"durationMin":25,"memo":"升至固色温度"},
 {"id":4,"stage":"HOLD","targetTemp":95,"durationMin":30,"memo":"加碱固色"},
 {"id":5,"stage":"COOL","targetTemp":60,"durationMin":15,"memo":"缓慢降温"},
 {"id":6,"stage":"COOL","targetTemp":45,"durationMin":10,"memo":"降至排液温度"}]}
EOF
V=$(curl -s -X POST $B/$CID/validate -H 'Content-Type: application/json' -d @/tmp/draft-cotton.json)
VALID=$(echo "$V" | jqf "['data']['valid']"); MAXR=$(echo "$V" | jqf "['data']['maxRampRate']")
[ "$VALID" = True ] && [ "$MAXR" = 3.0 ] && ok "切回全棉按 3.0℃/min 重算并通过（同一节点序列两种口径不同结论）" \
  || bad "切回全棉未重算通过：valid=$VALID max=$MAXR"

echo "== F. 保存成功后才刷新：落库结果与保存返回一致（布种 SILK / 起始 30 / 6 节点 / 第1段35min） =="
RES=$(curl -s -X PUT $B/$CID/nodes -H 'Content-Type: application/json' -d @/tmp/draft-silk-ok.json)
echo "$RES" > /tmp/save-ok.json
SCODE_FAB=$(echo "$RES" | jqf "['data']['fabricType']")
SCODE_D1=$(echo "$RES" | jqf "['data']['nodes'][0]['durationMin']")
SCODE_TOT=$(echo "$RES" | jqf "['data']['totalMinutes']")
sleep 0.2
GET=$(curl -s $B/$CID)
echo "$GET" > /tmp/curve-after.json
GET_FAB=$(echo "$GET" | jqf "['data']['fabricType']")
GET_ST=$(echo "$GET" | jqf "['data']['startTemp']")
GET_N=$(echo "$GET" | python3 -c "import json,sys;print(len(json.load(sys.stdin)['data']['nodes']))")
GET_D1=$(echo "$GET" | jqf "['data']['nodes'][0]['durationMin']")
GET_T3=$(echo "$GET" | jqf "['data']['nodes'][2]['targetTemp']")
[ "$SCODE_FAB" = SILK ] && [ "$GET_FAB" = SILK ] && [ "$GET_ST" = 30.0 ] \
  && [ "$GET_N" = 6 ] && [ "$GET_D1" = 35 ] && [ "$GET_T3" = 95.0 ] \
  && ok "保存返回与刷新后 GET 一致：SILK / 30℃ / 6节点 / 首段35min / 总${SCODE_TOT}min" \
  || bad "保存与刷新不一致：saveFab=$SCODE_FAB getFab=$GET_FAB start=$GET_ST n=$GET_N d1=$GET_D1 t3=$GET_T3"

# 已落库后再用纯已存口径预检（不带任何草稿字段）也应通过，证明确实按已保存的真丝草稿持久化
V=$(curl -s -X POST $B/$CID/validate -H 'Content-Type: application/json' -d '{}')
VALID=$(echo "$V" | jqf "['data']['valid']"); FAB=$(echo "$V" | jqf "['data']['fabricType']")
[ "$VALID" = True ] && [ "$FAB" = SILK ] && ok "落库后空载荷预检回退已存节点，真丝口径通过" \
  || bad "落库后回退预检异常：valid=$VALID fab=$FAB"

echo
echo "结果：PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = 0 ]
