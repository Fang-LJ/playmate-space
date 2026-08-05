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

EXPENSE=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"P2 测试晚餐\",\"category\":\"FOOD\",\"amount\":\"99.01\",\"payerUserId\":$USER_A,\"expenseTime\":\"2026-08-05T18:00:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A},{\"userId\":$USER_B}]}")
assert_code "$EXPENSE" '创建均摊账单'
EXPENSE_ID=$(jq -r '.data.expenseId' <<<"$EXPENSE")
[[ $(jq -r '[.data.shares[].shareAmount | (. * 100 | round)] | add' <<<"$EXPENSE") == "9901" ]] || fail '均摊金额没有精确汇总到总额'
pass '创建两人均摊账单并校验分摊金额'

SUMMARY_A=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/settlements/summary" -H "Authorization: Bearer $TOKEN_A")
assert_code "$SUMMARY_A" '创建者查看结算摘要'
FROM_ID=$(jq -r '.data.suggestions[0].fromUserId' <<<"$SUMMARY_A")
TO_ID=$(jq -r '.data.suggestions[0].toUserId' <<<"$SUMMARY_A")
AMOUNT=$(jq -r '.data.suggestions[0].amount' <<<"$SUMMARY_A")
[[ "$FROM_ID" == "$USER_B" && "$TO_ID" == "$USER_A" && "$AMOUNT" == "49.50" ]] || { echo "$SUMMARY_A"; fail '结算建议不符合两人均摊结果'; }
pass '结算建议正确生成'

COMPLETE=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/settlements/complete" -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' \
  -d "{\"fromUserId\":$FROM_ID,\"toUserId\":$TO_ID,\"amount\":\"$AMOUNT\"}")
assert_code "$COMPLETE" '用户 B 标记已转账'
SETTLEMENT_ID=$(jq -r '.data.settlementId' <<<"$COMPLETE")
pass '用户 B 标记已转账'

AFTER_COMPLETE=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/settlements/summary" -H "Authorization: Bearer $TOKEN_A")
assert_code "$AFTER_COMPLETE" '完成后刷新结算'
[[ $(jq -r '.data.suggestions | length' <<<"$AFTER_COMPLETE") == "0" ]] || fail '已完成转账仍出现在结算建议'
pass '完成记录已纳入实时结算'

CANCEL=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/settlements/$SETTLEMENT_ID/cancel" -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' -d '{"reason":"P2 联调撤销"}')
assert_code "$CANCEL" '撤销转账记录'
AFTER_CANCEL=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/settlements/summary" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.suggestions | length' <<<"$AFTER_CANCEL") == "1" ]] || fail '撤销后未重新生成结算建议'
pass '撤销后重新参与动态结算'

REMOVE=$(api -X DELETE "$BASE_URL/api/activities/$ACTIVITY_ID/members/$(api "$BASE_URL/api/activities/$ACTIVITY_ID/members" -H "Authorization: Bearer $TOKEN_A" | jq -r ".data[] | select(.userId == $USER_B) | .memberId")" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.code' <<<"$REMOVE") != "SUCCESS" ]] || fail '未结清成员被错误移除'
pass '未结清成员不可移除'

VOID=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/$EXPENSE_ID/void" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{"reason":"P2 联调作废"}')
assert_code "$VOID" '作废账单'
VOID_SUMMARY=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/summary" -H "Authorization: Bearer $TOKEN_A")
[[ $(jq -r '.data.totalExpenseAmount' <<<"$VOID_SUMMARY") == "0.00" ]] || fail '作废账单仍计入总支出'
pass '作废账单不再参与费用计算'

echo "[PASS] P2 expense smoke complete activityId=$ACTIVITY_ID expenseId=$EXPENSE_ID settlementId=$SETTLEMENT_ID userA=$USER_A userB=$USER_B"
