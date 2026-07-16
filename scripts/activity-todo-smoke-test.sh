#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${PLAYMATE_API_BASE_URL:-http://127.0.0.1:8080}"
for command in curl jq; do command -v "$command" >/dev/null || { echo "[FAIL] missing $command"; exit 1; }; done
curl -fsS "$BASE_URL/api/health" >/dev/null || { echo "[FAIL] backend unavailable"; exit 1; }

stamp=$(date +%Y%m%d%H%M%S)
pass(){ echo "[PASS] $1"; }
fail(){ echo "[FAIL] $1"; exit 1; }
login(){ curl -sS -X POST "$BASE_URL/api/auth/wx-login" -H 'Content-Type: application/json' -d "{\"mockOpenid\":\"todo_smoke_${1}_${stamp}\",\"nickname\":\"Todo Smoke ${1}\"}"; }
token(){ jq -r '.data.token' <<<"$1"; }
api(){ curl -sS "$@"; }
todo_count(){ api "$BASE_URL/api/users/me/activity-todos" -H "Authorization: Bearer $1" | jq --argjson activityId "$2" --arg todoType "$3" '[.data.todos[] | select(.activityId == $activityId and .todoType == $todoType)] | length'; }

A=$(login a); B=$(login b); C=$(login c); TA=$(token "$A"); TB=$(token "$B"); TC=$(token "$C")
[[ "$TA" != null && "$TB" != null && "$TC" != null ]] || fail "mock login"
ACT=$(api -X POST "$BASE_URL/api/activities" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"name\":\"Todo Smoke ${stamp}\",\"type\":\"TRAVEL\",\"startDate\":\"2026-07-20\",\"endDate\":\"2026-07-21\"}")
ACTIVITY_ID=$(jq -r '.data.activityId' <<<"$ACT"); CODE=$(jq -r '.data.shareCode' <<<"$ACT")
[[ "$ACTIVITY_ID" != null ]] || fail "create activity"
for T in "$TB" "$TC"; do api -X POST "$BASE_URL/api/activity-invites/$CODE/join" -H "Authorization: Bearer $T" >/dev/null; done
pass "创建活动并加入有效成员"

POLL=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"title":"待办投票","purpose":"GENERAL","decisionType":"CONTENT","voteType":"SINGLE","options":[{"optionText":"A"},{"optionText":"B"}]}')
POLL_ID=$(jq -r '.data.pollId' <<<"$POLL"); O1=$(jq -r '.data.options[0].optionId' <<<"$POLL")
[[ "$POLL_ID" != null ]] || fail "create poll"
[[ $(todo_count "$TB" "$ACTIVITY_ID" "POLL_VOTE") == 1 && $(todo_count "$TC" "$ACTIVITY_ID" "POLL_VOTE") == 1 ]] || fail "成员收到唯一投票待办"
pass "投票待办创建且幂等可见"

api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$POLL_ID/votes" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d "{\"optionIds\":[$O1]}" >/dev/null
[[ $(todo_count "$TB" "$ACTIVITY_ID" "POLL_VOTE") == 0 && $(todo_count "$TC" "$ACTIVITY_ID" "POLL_VOTE") == 1 ]] || fail "投票后仅完成自己的待办"
pass "用户投票后自己的待办完成，其他成员不受影响"

api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$POLL_ID/close" -H "Authorization: Bearer $TA" >/dev/null
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$POLL_ID/close" -H "Authorization: Bearer $TA" >/dev/null
[[ $(todo_count "$TC" "$ACTIVITY_ID" "POLL_VOTE") == 0 ]] || fail "投票结束未关闭参与待办"
pass "结束投票后关闭全部参与待办"

CANCEL=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"title":"取消待办投票","purpose":"GENERAL","decisionType":"CONTENT","voteType":"SINGLE","options":[{"optionText":"A"},{"optionText":"B"}]}')
CANCEL_ID=$(jq -r '.data.pollId' <<<"$CANCEL")
[[ $(todo_count "$TC" "$ACTIVITY_ID" "POLL_VOTE") == 1 ]] || fail "取消投票前待办缺失"
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$CANCEL_ID/cancel" -H "Authorization: Bearer $TA" >/dev/null
[[ $(todo_count "$TC" "$ACTIVITY_ID" "POLL_VOTE") == 0 ]] || fail "取消投票后待办未关闭"
EXPIRED=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"title":"过期待办投票","purpose":"GENERAL","decisionType":"CONTENT","voteType":"SINGLE","deadline":"2020-01-01T00:00:00","options":[{"optionText":"A"},{"optionText":"B"}]}')
EXPIRED_ID=$(jq -r '.data.pollId' <<<"$EXPIRED")
api "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" >/dev/null
[[ $(todo_count "$TC" "$ACTIVITY_ID" "POLL_VOTE") == 0 ]] || fail "过期投票后待办未关闭"
pass "取消和过期投票均关闭参与待办"

BEFORE_ITINERARY=$(api "$BASE_URL/api/users/me/activity-todos" -H "Authorization: Bearer $TC" | jq -r '.data.todoCount')
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"creationMode":"DIRECT","title":"不计入待办的行程","itineraryType":"ACTIVITY","itineraryDate":"2026-07-21","startTime":"09:00","endTime":"10:00","allDay":false}' >/dev/null
AFTER_ITINERARY=$(api "$BASE_URL/api/users/me/activity-todos" -H "Authorization: Bearer $TC" | jq -r '.data.todoCount')
[[ "$BEFORE_ITINERARY" == "$AFTER_ITINERARY" ]] || fail "行程被错误计入待办"
pass "当前和即将开始的行程不计入待办"

REVIEW=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"title":"结果确认投票","purpose":"CREATE_ITINERARY","decisionType":"PLACE","voteType":"SINGLE","itineraryTemplate":{"title":"待确认行程","itineraryDate":"2026-07-21"},"options":[{"optionText":"地点 A","resultPayload":{"locationName":"A"}},{"optionText":"地点 B","resultPayload":{"locationName":"B"}}]}')
REVIEW_ID=$(jq -r '.data.pollId' <<<"$REVIEW"); R1=$(jq -r '.data.options[0].optionId' <<<"$REVIEW"); R2=$(jq -r '.data.options[1].optionId' <<<"$REVIEW")
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$REVIEW_ID/votes" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionIds\":[$R1]}" >/dev/null
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$REVIEW_ID/votes" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d "{\"optionIds\":[$R2]}" >/dev/null
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$REVIEW_ID/close" -H "Authorization: Bearer $TA" >/dev/null
[[ $(todo_count "$TA" "$ACTIVITY_ID" "POLL_RESULT_CONFIRM") == 1 && $(todo_count "$TC" "$ACTIVITY_ID" "POLL_RESULT_CONFIRM") == 0 ]] || fail "结果确认待办权限分配错误"
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$REVIEW_ID/apply-result" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionId\":$R1}" >/dev/null
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$REVIEW_ID/apply-result" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionId\":$R1}" >/dev/null
[[ $(todo_count "$TA" "$ACTIVITY_ID" "POLL_RESULT_CONFIRM") == 0 ]] || fail "结果应用后确认待办未关闭"
pass "结果确认待办仅分配给有权限成员并在应用后关闭"

REMINDER=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/reminders" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"title":"集合提醒","content":"请准时到达"}')
TODO_ID=$(jq -r '.data' <<<"$REMINDER"); [[ "$TODO_ID" != null ]] || fail "发布人工提醒"
[[ $(todo_count "$TB" "$ACTIVITY_ID" "MANUAL_REMINDER") == 1 && $(todo_count "$TC" "$ACTIVITY_ID" "MANUAL_REMINDER") == 1 ]] || fail "人工提醒未分配给成员"
api -X POST "$BASE_URL/api/activity-todos/$TODO_ID/ack" -H "Authorization: Bearer $TB" >/dev/null
api -X POST "$BASE_URL/api/activity-todos/$TODO_ID/ack" -H "Authorization: Bearer $TB" >/dev/null
[[ $(todo_count "$TB" "$ACTIVITY_ID" "MANUAL_REMINDER") == 0 && $(todo_count "$TC" "$ACTIVITY_ID" "MANUAL_REMINDER") == 1 ]] || fail "人工提醒确认未独立生效"
ACK=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/reminders/$TODO_ID/ack-status" -H "Authorization: Bearer $TA")
[[ $(jq -r '.data.acknowledgedCount' <<<"$ACK") == 1 ]] || fail "创建者无法获取确认情况"
pass "人工提醒发布、独立确认和确认情况查询"

OUTSIDER=$(login outsider); TO=$(token "$OUTSIDER")
STATUS=$(api -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/activity-todos/$TODO_ID/ack" -H "Authorization: Bearer $TO")
[[ "$STATUS" == 403 ]] || fail "非活动成员不应确认待办"
pass "非活动成员不能读取或确认待办"

api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/cancel" -H "Authorization: Bearer $TA" >/dev/null
[[ $(todo_count "$TC" "$ACTIVITY_ID" "MANUAL_REMINDER") == 0 ]] || fail "活动取消后待办未关闭"
STATUS=$(api -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/activity-todos/$TODO_ID/ack" -H "Authorization: Bearer $TC")
[[ "$STATUS" == 403 || "$STATUS" == 400 ]] || fail "取消活动后仍允许确认待办"
pass "活动取消后未完成待办取消"
echo "[PASS] activity todo smoke complete activityId=$ACTIVITY_ID todoId=$TODO_ID"
