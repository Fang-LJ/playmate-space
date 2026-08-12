#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${PLAYMATE_API_BASE_URL:-http://127.0.0.1:8080}"
command -v curl >/dev/null || { echo '[FAIL] curl is required'; exit 1; }
command -v jq >/dev/null || { echo '[FAIL] jq is required'; exit 1; }
curl -fsS "$BASE_URL/api/health" >/dev/null || { echo '[FAIL] backend unavailable'; exit 1; }

stamp=$(date +%s)
pass() { echo "[PASS] $1"; }
fail() { echo "[FAIL] $1"; exit 1; }
api() { curl -sS "$@"; }
assert_code() { [[ $(jq -r '.code' <<<"$1") == "SUCCESS" ]] || { echo "$1"; fail "$2"; }; }
assert_error() { [[ $(jq -r '.code' <<<"$1") != "SUCCESS" ]] || { echo "$1"; fail "$2"; }; }
login() {
  api -X POST "$BASE_URL/api/auth/wx-login" -H 'Content-Type: application/json' \
    -d "{\"mockOpenid\":\"p2_expense_$1_$stamp\",\"nickname\":\"P2 $1\"}"
}
token() { jq -r '.data.token' <<<"$1"; }
user_id() { jq -r '.data.userId' <<<"$1"; }

LOGIN_A=$(login A); assert_code "$LOGIN_A" '用户 A 登录'; TOKEN_A=$(token "$LOGIN_A"); USER_A=$(user_id "$LOGIN_A")
LOGIN_B=$(login B); assert_code "$LOGIN_B" '用户 B 登录'; TOKEN_B=$(token "$LOGIN_B"); USER_B=$(user_id "$LOGIN_B")

CREATE=$(api -X POST "$BASE_URL/api/activities" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"name\":\"P2 费用联调 $stamp\",\"type\":\"TRAVEL\",\"startDate\":\"2026-08-05\",\"endDate\":\"2026-08-06\"}")
assert_code "$CREATE" '创建活动'
ACTIVITY_ID=$(jq -r '.data.activityId' <<<"$CREATE")
SHARE_CODE=$(jq -r '.data.shareCode' <<<"$CREATE")
pass '创建活动'

JOIN=$(api -X POST "$BASE_URL/api/activity-invites/$SHARE_CODE/join" -H "Authorization: Bearer $TOKEN_B")
assert_code "$JOIN" '用户 B 加入活动'
pass '用户 B 加入活动'

ILLEGAL_PAYER=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' \
  -d "{\"title\":\"越权代记\",\"category\":\"FOOD\",\"amount\":\"10.00\",\"payerUserId\":$USER_A,\"expenseTime\":\"2026-08-05T17:00:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A},{\"userId\":$USER_B}]}")
assert_error "$ILLEGAL_PAYER" '普通成员被错误允许代他人记账'
pass '普通成员只能记录自己付款'

EXPENSE=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"P2 测试晚餐\",\"category\":\"FOOD\",\"amount\":\"99.01\",\"payerUserId\":$USER_B,\"expenseTime\":\"2026-08-05T18:00:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A},{\"userId\":$USER_B}]}")
assert_code "$EXPENSE" '创建者代成员创建均摊账单'
EXPENSE_ID=$(jq -r '.data.expenseId' <<<"$EXPENSE")
VERSION=$(jq -r '.data.version' <<<"$EXPENSE")
[[ $(jq -r '[.data.shares[].shareAmount | (. * 100 | round)] | add' <<<"$EXPENSE") == "9901" ]] || fail '均摊金额没有精确汇总到总额'
pass '创建者代记账与均摊尾差正确'

DASHBOARD=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
assert_code "$DASHBOARD" '获取费用 dashboard'
[[ $(jq -r '.data.summary.totalExpenseAmount' <<<"$DASHBOARD") == "99.01" ]] || fail 'dashboard 总金额错误'
[[ $(jq -r '.data.summary.expenseCount' <<<"$DASHBOARD") == "1" ]] || fail 'dashboard 账单数错误'
[[ $(jq -r '.data.summary.participantCount' <<<"$DASHBOARD") == "2" ]] || fail 'dashboard 参与人数错误'
[[ $(jq -r '.data.suggestions | length' <<<"$DASHBOARD") == "1" ]] || fail 'dashboard 转账建议数量错误'
[[ $(jq -r '[.data.members[].netAmount] | map(tonumber) | add' <<<"$DASHBOARD") == "0" ]] || fail '成员净额合计不为零'
pass 'dashboard、成员净额和建议正确'

SUMMARY=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/summary" -H "Authorization: Bearer $TOKEN_A")
assert_code "$SUMMARY" '获取轻量费用摘要'
[[ $(jq -r '.data.expenseCount' <<<"$SUMMARY") == "1" ]] || fail '轻量摘要账单数错误'
[[ $(jq -r '.data.suggestionCount' <<<"$SUMMARY") == "1" ]] || fail '轻量摘要建议数错误'
[[ $(jq -r '.data.recentExpenses | length' <<<"$SUMMARY") == "1" ]] || fail '轻量摘要最近账单错误'
pass '活动详情轻量摘要正确'

LIST=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A")
assert_code "$LIST" '查询有效账单列表'
[[ $(jq -r '.data | length' <<<"$LIST") == "1" ]] || fail '有效账单列表数量错误'

UPDATE=$(api -X PUT "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/$EXPENSE_ID" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"P2 更新晚餐\",\"category\":\"FOOD\",\"amount\":\"100.00\",\"payerUserId\":$USER_B,\"expenseTime\":\"2026-08-05T18:30:00\",\"splitMode\":\"CUSTOM\",\"shares\":[{\"userId\":$USER_A,\"shareAmount\":\"40.00\"},{\"userId\":$USER_B,\"shareAmount\":\"60.00\"}],\"version\":$VERSION}")
assert_code "$UPDATE" '按当前版本编辑账单'
NEW_VERSION=$(jq -r '.data.version' <<<"$UPDATE")
[[ "$NEW_VERSION" == "$((VERSION + 1))" ]] || fail '账单版本未递增'
pass '账单编辑与版本递增正确'

STALE=$(api -X PUT "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/$EXPENSE_ID" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"旧版本覆盖\",\"category\":\"FOOD\",\"amount\":\"100.00\",\"payerUserId\":$USER_B,\"expenseTime\":\"2026-08-05T18:30:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A},{\"userId\":$USER_B}],\"version\":$VERSION}")
assert_error "$STALE" '旧版本编辑被错误接受'
[[ $(jq -r '.message' <<<"$STALE") == *"账单已被其他成员修改"* ]] || { echo "$STALE"; fail '旧版本冲突提示不明确'; }
pass '数据库原子乐观锁拒绝旧版本'

BAD_CUSTOM=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"错误分摊\",\"category\":\"OTHER\",\"amount\":\"20.00\",\"payerUserId\":$USER_A,\"expenseTime\":\"2026-08-05T19:00:00\",\"splitMode\":\"CUSTOM\",\"shares\":[{\"userId\":$USER_A,\"shareAmount\":\"9.00\"},{\"userId\":$USER_B,\"shareAmount\":\"9.00\"}]}")
assert_error "$BAD_CUSTOM" '错误自定义分摊被接受'
pass '自定义分摊合计校验正确'

VOID=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/$EXPENSE_ID/void" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{"reason":"P2 联调作废"}')
assert_code "$VOID" '作废账单'
AFTER_VOID_LIST=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data | length' <<<"$AFTER_VOID_LIST") == "0" ]] || fail '作废账单仍出现在默认列表'
AFTER_VOID=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.summary.totalExpenseAmount' <<<"$AFTER_VOID") == "0.00" ]] || fail '作废账单仍计入 dashboard'
[[ $(jq -r '.data.suggestions | length' <<<"$AFTER_VOID") == "0" ]] || fail '作废账单仍影响结算建议'
pass 'VOID 账单不进入列表、总额与结算'

echo "[PASS] P2 expense smoke complete activityId=$ACTIVITY_ID expenseId=$EXPENSE_ID userA=$USER_A userB=$USER_B"
