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
  -d "{\"title\":\"越权代记\",\"category\":\"FOOD\",\"amount\":\"10.00\",\"payerUserId\":$USER_A,\"expenseTime\":\"2026-08-05T17:00:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A},{\"userId\":$USER_B}],\"clientRequestId\":\"illegal-$stamp\"}")
assert_error "$ILLEGAL_PAYER" '普通成员被错误允许代他人记账'
pass '普通成员只能记录自己付款'

EXPENSE=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"P2 测试晚餐\",\"category\":\"FOOD\",\"amount\":\"99.01\",\"payerUserId\":$USER_B,\"expenseTime\":\"2026-08-05T18:00:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A},{\"userId\":$USER_B}],\"clientRequestId\":\"expense-$stamp\"}")
assert_code "$EXPENSE" '创建者代成员创建均摊账单'
EXPENSE_ID=$(jq -r '.data.expenseId' <<<"$EXPENSE")
VERSION=$(jq -r '.data.version' <<<"$EXPENSE")
[[ $(jq -r '[.data.shares[].shareAmount | (. * 100 | round)] | add' <<<"$EXPENSE") == "9901" ]] || fail '均摊金额没有精确汇总到总额'
pass '创建者代记账与均摊尾差正确'

IDEMPOTENT=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"不同载荷也不得覆盖\",\"category\":\"OTHER\",\"amount\":\"1.00\",\"payerUserId\":$USER_A,\"expenseTime\":\"2026-08-05T18:05:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A}],\"clientRequestId\":\"expense-$stamp\"}")
assert_code "$IDEMPOTENT" '幂等重试'
[[ $(jq -r '.data.expenseId' <<<"$IDEMPOTENT") == "$EXPENSE_ID" ]] || fail '幂等重试返回了不同账单'
[[ $(jq -r '.data.title' <<<"$IDEMPOTENT") == "P2 测试晚餐" ]] || fail '幂等重试覆盖了首次请求数据'
pass 'clientRequestId 重试返回原账单且不覆盖数据'

DASHBOARD=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
assert_code "$DASHBOARD" '获取费用 dashboard'
[[ $(jq -r '.data.summary.totalExpenseAmount' <<<"$DASHBOARD") == "99.01" ]] || fail 'dashboard 总金额错误'
[[ $(jq -r '.data.summary.expenseCount' <<<"$DASHBOARD") == "1" ]] || fail 'dashboard 账单数错误'
[[ $(jq -r '.data.summary.participantCount' <<<"$DASHBOARD") == "2" ]] || fail 'dashboard 参与人数错误'
[[ $(jq -r '.data.suggestions | length' <<<"$DASHBOARD") == "1" ]] || fail 'dashboard 转账建议数量错误'
[[ $(jq -r '[.data.members[].netAmount] | map(tonumber) | add' <<<"$DASHBOARD") == "0" ]] || fail '成员净额合计不为零'
[[ $(jq -r '.data.financeVersion' <<<"$DASHBOARD") == "1" ]] || fail '首次新增后 financeVersion 应为 1'
pass 'dashboard、成员净额和建议正确'

SUMMARY=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/summary" -H "Authorization: Bearer $TOKEN_A")
assert_code "$SUMMARY" '获取轻量费用摘要'
[[ $(jq -r '.data.expenseCount' <<<"$SUMMARY") == "1" ]] || fail '轻量摘要账单数错误'
[[ $(jq -r '.data.suggestionCount' <<<"$SUMMARY") == "1" ]] || fail '轻量摘要建议数错误'
[[ $(jq -r '.data.recentExpenses | length' <<<"$SUMMARY") == "1" ]] || fail '轻量摘要最近账单错误'
[[ $(jq -r '.data.financeVersion' <<<"$SUMMARY") == "1" ]] || fail 'summary financeVersion 错误'
pass '活动详情轻量摘要正确'

LIST=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A")
assert_code "$LIST" '查询有效账单列表'
[[ $(jq -r '.data | length' <<<"$LIST") == "1" ]] || fail '有效账单列表数量错误'

UPDATE=$(api -X PUT "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/$EXPENSE_ID" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"P2 更新晚餐\",\"category\":\"FOOD\",\"amount\":\"100.00\",\"payerUserId\":$USER_B,\"expenseTime\":\"2026-08-05T18:30:00\",\"splitMode\":\"CUSTOM\",\"shares\":[{\"userId\":$USER_A,\"shareAmount\":\"40.00\"},{\"userId\":$USER_B,\"shareAmount\":\"60.00\"}],\"version\":$VERSION}")
assert_code "$UPDATE" '按当前版本编辑账单'
NEW_VERSION=$(jq -r '.data.version' <<<"$UPDATE")
[[ "$NEW_VERSION" == "$((VERSION + 1))" ]] || fail '账单版本未递增'
DASHBOARD_AFTER_UPDATE=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.financeVersion' <<<"$DASHBOARD_AFTER_UPDATE") == "2" ]] || fail '编辑后 financeVersion 应为 2'
pass '账单编辑与版本递增正确'

STALE=$(api -X PUT "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/$EXPENSE_ID" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"旧版本覆盖\",\"category\":\"FOOD\",\"amount\":\"100.00\",\"payerUserId\":$USER_B,\"expenseTime\":\"2026-08-05T18:30:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A},{\"userId\":$USER_B}],\"version\":$VERSION}")
assert_error "$STALE" '旧版本编辑被错误接受'
[[ $(jq -r '.message' <<<"$STALE") == *"账单已被其他成员修改"* ]] || { echo "$STALE"; fail '旧版本冲突提示不明确'; }
pass '数据库原子乐观锁拒绝旧版本'
AFTER_STALE=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.financeVersion' <<<"$AFTER_STALE") == "2" ]] || fail '失败编辑不应增加 financeVersion'

BAD_CUSTOM=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"错误分摊\",\"category\":\"OTHER\",\"amount\":\"20.00\",\"payerUserId\":$USER_A,\"expenseTime\":\"2026-08-05T19:00:00\",\"splitMode\":\"CUSTOM\",\"shares\":[{\"userId\":$USER_A,\"shareAmount\":\"9.00\"},{\"userId\":$USER_B,\"shareAmount\":\"9.00\"}],\"clientRequestId\":\"bad-custom-$stamp\"}")
assert_error "$BAD_CUSTOM" '错误自定义分摊被接受'
pass '自定义分摊合计校验正确'

VOID=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/$EXPENSE_ID/void" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d "{\"expectedVersion\":$NEW_VERSION,\"reason\":\"P2 联调作废\"}")
assert_code "$VOID" '作废账单'
AFTER_VOID_LIST=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data | length' <<<"$AFTER_VOID_LIST") == "0" ]] || fail '作废账单仍出现在默认列表'
AFTER_VOID=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.summary.totalExpenseAmount' <<<"$AFTER_VOID") == "0.00" ]] || fail '作废账单仍计入 dashboard'
[[ $(jq -r '.data.suggestions | length' <<<"$AFTER_VOID") == "0" ]] || fail '作废账单仍影响结算建议'
[[ $(jq -r '.data.financeVersion' <<<"$AFTER_VOID") == "3" ]] || fail '作废后 financeVersion 应为 3'
pass 'VOID 账单不进入列表、总额与结算'

REPEAT_VOID=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/$EXPENSE_ID/void" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d "{\"expectedVersion\":$NEW_VERSION,\"reason\":\"重复作废\"}")
assert_error "$REPEAT_VOID" '重复旧版本作废被错误接受'
AFTER_REPEAT_VOID=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.financeVersion' <<<"$AFTER_REPEAT_VOID") == "3" ]] || fail '作废冲突不应增加 financeVersion'
pass '作废 expectedVersion 冲突保护正确'

CONCURRENT_CREATE=$(api -X POST "$BASE_URL/api/activities" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"name\":\"P2 并发联调 $stamp\",\"type\":\"TRAVEL\",\"startDate\":\"2026-08-05\",\"endDate\":\"2026-08-06\"}")
assert_code "$CONCURRENT_CREATE" '创建并发活动'
CONCURRENT_ACTIVITY_ID=$(jq -r '.data.activityId' <<<"$CONCURRENT_CREATE")
CONCURRENT_SHARE_CODE=$(jq -r '.data.shareCode' <<<"$CONCURRENT_CREATE")
TMP_DIR=$(mktemp -d /tmp/playmate-finance-smoke.XXXXXX)
trap 'rm -rf "$TMP_DIR"' EXIT
create_concurrent_expense() {
  local request_id="$1" title="$2" output="$3"
  curl -sS -X POST "$BASE_URL/api/activities/$CONCURRENT_ACTIVITY_ID/expenses" \
    -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
    -d "{\"title\":\"$title\",\"category\":\"FOOD\",\"amount\":\"10.00\",\"payerUserId\":$USER_A,\"expenseTime\":\"2026-08-05T20:00:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A}],\"clientRequestId\":\"$request_id\"}" >"$output"
}

create_concurrent_expense "parallel-a-$stamp" '并发账单 A' "$TMP_DIR/create-a.json" & PID_A=$!
create_concurrent_expense "parallel-b-$stamp" '并发账单 B' "$TMP_DIR/create-b.json" & PID_B=$!
wait "$PID_A"; wait "$PID_B"
assert_code "$(cat "$TMP_DIR/create-a.json")" '并发创建 A'
assert_code "$(cat "$TMP_DIR/create-b.json")" '并发创建 B'
PARALLEL_DASHBOARD=$(api "$BASE_URL/api/activities/$CONCURRENT_ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.financeVersion' <<<"$PARALLEL_DASHBOARD") == "2" ]] || fail '并发不同新增发生 financeVersion 丢失'
[[ $(jq -r '.data.summary.expenseCount' <<<"$PARALLEL_DASHBOARD") == "2" ]] || fail '并发不同新增未生成两笔账单'
pass '同活动并发新增串行化且 financeVersion +2'

create_concurrent_expense "same-$stamp" '并发幂等账单' "$TMP_DIR/same-a.json" & PID_A=$!
create_concurrent_expense "same-$stamp" '不同载荷不得覆盖' "$TMP_DIR/same-b.json" & PID_B=$!
wait "$PID_A"; wait "$PID_B"
SAME_A=$(cat "$TMP_DIR/same-a.json"); SAME_B=$(cat "$TMP_DIR/same-b.json")
assert_code "$SAME_A" '并发幂等请求 A'; assert_code "$SAME_B" '并发幂等请求 B'
[[ $(jq -r '.data.expenseId' <<<"$SAME_A") == $(jq -r '.data.expenseId' <<<"$SAME_B") ]] || fail '并发相同幂等键生成了不同账单'
SAME_DASHBOARD=$(api "$BASE_URL/api/activities/$CONCURRENT_ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.financeVersion' <<<"$SAME_DASHBOARD") == "3" ]] || fail '并发幂等请求应只增加一次 financeVersion'
[[ $(jq -r '.data.summary.expenseCount' <<<"$SAME_DASHBOARD") == "3" ]] || fail '并发幂等请求生成了重复账单'
pass '并发相同 clientRequestId 仅创建一次且无 500'

JOIN_CONCURRENT=$(api -X POST "$BASE_URL/api/activity-invites/$CONCURRENT_SHARE_CODE/join" -H "Authorization: Bearer $TOKEN_B")
assert_code "$JOIN_CONCURRENT" '用户 B 加入并发活动'
DIFFERENT_USER_SAME_KEY=$(api -X POST "$BASE_URL/api/activities/$CONCURRENT_ACTIVITY_ID/expenses" \
  -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' \
  -d "{\"title\":\"用户 B 同键账单\",\"category\":\"FOOD\",\"amount\":\"10.00\",\"payerUserId\":$USER_B,\"expenseTime\":\"2026-08-05T20:30:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_B}],\"clientRequestId\":\"same-$stamp\"}")
assert_code "$DIFFERENT_USER_SAME_KEY" '不同用户使用相同 clientRequestId'
DIFFERENT_USER_DASHBOARD=$(api "$BASE_URL/api/activities/$CONCURRENT_ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.financeVersion' <<<"$DIFFERENT_USER_DASHBOARD") == "4" ]] || fail '不同用户相同幂等键应独立创建'
pass 'clientRequestId 按活动和创建人隔离'

RACE_EXPENSE_ID=$(jq -r '.data.expenseId' <<<"$SAME_A")
RACE_VERSION=$(jq -r '.data.version' <<<"$SAME_A")
update_race_expense() {
  local title="$1" version="$2" output="$3"
  curl -sS -X PUT "$BASE_URL/api/activities/$CONCURRENT_ACTIVITY_ID/expenses/$RACE_EXPENSE_ID" \
    -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
    -d "{\"title\":\"$title\",\"category\":\"FOOD\",\"amount\":\"10.00\",\"payerUserId\":$USER_A,\"expenseTime\":\"2026-08-05T20:00:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A}],\"version\":$version}" >"$output"
}
update_race_expense '并发编辑 A' "$RACE_VERSION" "$TMP_DIR/edit-a.json" & PID_A=$!
update_race_expense '并发编辑 B' "$RACE_VERSION" "$TMP_DIR/edit-b.json" & PID_B=$!
wait "$PID_A"; wait "$PID_B"
EDIT_SUCCESS_COUNT=$(jq -s '[.[] | select(.code == "SUCCESS")] | length' "$TMP_DIR/edit-a.json" "$TMP_DIR/edit-b.json")
[[ "$EDIT_SUCCESS_COUNT" == "1" ]] || fail '同版本并发编辑必须且只能成功一个'
AFTER_EDIT_RACE=$(api "$BASE_URL/api/activities/$CONCURRENT_ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.financeVersion' <<<"$AFTER_EDIT_RACE") == "5" ]] || fail '并发编辑应只增加一次 financeVersion'
pass '同一 expense.version 并发编辑仅一个成功'

RACE_DETAIL=$(api "$BASE_URL/api/activities/$CONCURRENT_ACTIVITY_ID/expenses/$RACE_EXPENSE_ID" -H "Authorization: Bearer $TOKEN_A")
RACE_LATEST_VERSION=$(jq -r '.data.version' <<<"$RACE_DETAIL")
update_race_expense '编辑与作废竞争' "$RACE_LATEST_VERSION" "$TMP_DIR/edit-void-edit.json" & PID_A=$!
curl -sS -X POST "$BASE_URL/api/activities/$CONCURRENT_ACTIVITY_ID/expenses/$RACE_EXPENSE_ID/void" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"expectedVersion\":$RACE_LATEST_VERSION,\"reason\":\"并发作废\"}" >"$TMP_DIR/edit-void-void.json" & PID_B=$!
wait "$PID_A"; wait "$PID_B"
EDIT_VOID_SUCCESS_COUNT=$(jq -s '[.[] | select(.code == "SUCCESS")] | length' "$TMP_DIR/edit-void-edit.json" "$TMP_DIR/edit-void-void.json")
[[ "$EDIT_VOID_SUCCESS_COUNT" == "1" ]] || fail '编辑与作废竞争必须且只能成功一个'
AFTER_EDIT_VOID=$(api "$BASE_URL/api/activities/$CONCURRENT_ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.financeVersion' <<<"$AFTER_EDIT_VOID") == "6" ]] || fail '编辑与作废竞争应只增加一次 financeVersion'
pass '编辑与作废遵循统一锁顺序且旧版本不能覆盖'

echo "[PASS] P2 expense smoke complete activityId=$ACTIVITY_ID expenseId=$EXPENSE_ID userA=$USER_A userB=$USER_B"
