#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${PLAYMATE_API_BASE_URL:-http://127.0.0.1:8080}"
for command in curl jq; do command -v "$command" >/dev/null || { echo "[FAIL] missing $command"; exit 1; }; done
curl -fsS "$BASE_URL/api/health" >/dev/null || { echo "[FAIL] backend unavailable"; exit 1; }
stamp=$(date +%Y%m%d%H%M%S)
pass(){ echo "[PASS] $1"; }
fail(){ echo "[FAIL] $1"; exit 1; }
login(){ curl -sS -X POST "$BASE_URL/api/auth/wx-login" -H 'Content-Type: application/json' -d "{\"mockOpenid\":\"p1_smoke_${1}_${stamp}\",\"nickname\":\"P1 Smoke ${1}\"}"; }
token(){ printf '%s' "$1" | jq -r '.data.token'; }
api(){ curl -sS "$@"; }

A=$(login a); B=$(login b); C=$(login c); TA=$(token "$A"); TB=$(token "$B"); TC=$(token "$C")
[[ "$TA" != null && "$TB" != null && "$TC" != null ]] || fail "mock login"
pass "A/B/C 登录"
ACT=$(api -X POST "$BASE_URL/api/activities" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"name\":\"P1 Smoke ${stamp}\",\"type\":\"TRAVEL\",\"startDate\":\"2026-07-18\",\"endDate\":\"2026-07-19\"}")
ACTIVITY_ID=$(printf '%s' "$ACT"|jq -r '.data.activityId'); CODE=$(printf '%s' "$ACT"|jq -r '.data.shareCode')
[[ "$ACTIVITY_ID" != null ]] || fail "创建活动"
for T in "$TB" "$TC"; do api -X POST "$BASE_URL/api/activity-invites/$CODE/join" -H "Authorization: Bearer $T" >/dev/null; done
pass "创建活动并邀请成员"
DIRECT=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"creationMode":"DIRECT","title":"集合","itineraryType":"TRANSPORT","itineraryDate":"2026-07-18","startTime":"09:00","endTime":"10:00","allDay":false}')
DIRECT_ID=$(printf '%s' "$DIRECT"|jq -r '.data.itineraryId'); [[ "$DIRECT_ID" != null ]] || fail "创建普通行程"; pass "创建普通行程"
WITH=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d '{"creationMode":"WITH_POLL","title":"晚餐","itineraryType":"MEAL","itineraryDate":"2026-07-18","startTime":"18:00","endTime":"20:00","allDay":false,"poll":{"title":"晚餐吃什么？","purpose":"UPDATE_ITINERARY","decisionType":"RESTAURANT","voteType":"SINGLE","allowModify":true,"options":[{"optionText":"火锅","resultPayload":{"title":"火锅晚餐","locationName":"湖滨火锅"}},{"optionText":"烧烤","resultPayload":{"title":"烧烤晚餐"}}]}}')
WITH_ID=$(printf '%s' "$WITH"|jq -r '.data.itineraryId'); POLL_ID=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA"|jq -r '.data[0].pollId'); [[ "$WITH_ID" != null && "$POLL_ID" != null ]] || fail "待决定行程与投票"; pass "待决定行程与投票事务创建"
DETAIL=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$POLL_ID" -H "Authorization: Bearer $TA"); O1=$(printf '%s' "$DETAIL"|jq -r '.data.options[0].optionId')
for T in "$TA" "$TB" "$TC"; do api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$POLL_ID/votes" -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d "{\"optionIds\":[$O1]}" >/dev/null; done
CLOSED=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$POLL_ID/close" -H "Authorization: Bearer $TB")
[[ $(printf '%s' "$CLOSED"|jq -r '.data.resultApplyStatus') == APPLIED ]] || fail "唯一胜出自动应用"
[[ $(api "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$WITH_ID" -H "Authorization: Bearer $TA"|jq -r '.data.itinerary.title') == 火锅晚餐 ]] || fail "投票更新行程"
pass "唯一胜出自动更新行程"
STATUS=$(api -o /dev/null -w '%{http_code}' -X PUT "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$DIRECT_ID" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d '{"title":"越权修改"}')
[[ "$STATUS" == 403 ]] || fail "普通成员越权校验"; pass "成员权限校验"
TIE=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"title":"并列测试","purpose":"CREATE_ITINERARY","decisionType":"PLACE","voteType":"SINGLE","itineraryTemplate":{"title":"候选活动","itineraryDate":"2026-07-19"},"options":[{"optionText":"A","resultPayload":{"locationName":"地点 A"}},{"optionText":"B","resultPayload":{"locationName":"地点 B"}}]}')
TIE_ID=$(printf '%s' "$TIE"|jq -r '.data.pollId'); T1=$(printf '%s' "$TIE"|jq -r '.data.options[0].optionId'); T2=$(printf '%s' "$TIE"|jq -r '.data.options[1].optionId')
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TIE_ID/votes" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionIds\":[$T1]}" >/dev/null; api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TIE_ID/votes" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d "{\"optionIds\":[$T2]}" >/dev/null
REVIEW=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TIE_ID/close" -H "Authorization: Bearer $TA")
[[ $(printf '%s' "$REVIEW"|jq -r '.data.resultApplyStatus') == REVIEW_REQUIRED ]] || fail "并列人工确认"; api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TIE_ID/apply-result" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionId\":$T1}" >/dev/null; pass "并列进入并完成人工确认"
SUMMARY=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/collaboration-summary" -H "Authorization: Bearer $TA"); [[ $(printf '%s' "$SUMMARY"|jq -r '.code') == SUCCESS ]] || fail "协作摘要"; [[ $(printf '%s' "$SUMMARY"|jq -r '.data.todoCount') == 0 ]] || fail "已处理投票不应保留待办"; pass "活动详情协作摘要"
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/end" -H "Authorization: Bearer $TA" >/dev/null
READ_ONLY=$(api -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"creationMode":"DIRECT","title":"不可创建","itineraryDate":"2026-07-19"}')
[[ "$READ_ONLY" == 403 ]] || fail "结束活动只读"; pass "结束活动行程投票只读"
echo "[PASS] P1 smoke complete activityId=$ACTIVITY_ID"
