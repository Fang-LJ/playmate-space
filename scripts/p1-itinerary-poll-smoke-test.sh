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

METADATA=$(api "$BASE_URL/api/itineraries/type-metadata" -H "Authorization: Bearer $TA")
[[ $(printf '%s' "$METADATA" | jq -r '.data | length') == 6 ]] || fail "六种行程类型元数据"
[[ $(printf '%s' "$METADATA" | jq -r '.data[0].type') == TRANSPORT
  && $(printf '%s' "$METADATA" | jq -r '.data[5].type') == OTHER ]] || fail "行程类型元数据顺序"
pass "六种行程类型元数据"

INVALID_MIX=$(api -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"creationMode":"DIRECT","title":"非法混写","itineraryType":"TRANSPORT","itineraryDate":"2026-07-18","startTime":"09:00","endTime":"10:00","restaurantName":"不应保存"}')
[[ "$INVALID_MIX" == 400 ]] || fail "交通行程混写餐厅字段应被拒绝"
INVALID_TEMPLATE=$(api -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"title":"非法模板","purpose":"CREATE_ITINERARY","decisionType":"RESTAURANT","voteType":"SINGLE","itineraryTemplate":{"title":"候选晚餐","itineraryType":"MEAL","itineraryDate":"2026-07-18","transportMode":"自驾"},"options":[{"optionText":"A","resultPayload":{"restaurantName":"A"}},{"optionText":"B","resultPayload":{"restaurantName":"B"}}]}')
[[ "$INVALID_TEMPLATE" == 400 ]] || fail "生成行程模板混写其他类型字段应被拒绝"
LODGING=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"creationMode":"DIRECT","title":"跨夜住宿","itineraryType":"LODGING","itineraryDate":"2026-07-18","startTime":"21:00","endTime":"09:00","locationName":"亚朵酒店","address":"幸福路 88 号"}')
[[ $(printf '%s' "$LODGING" | jq -r '.data.itineraryType') == LODGING ]] || fail "住宿跨午夜规则"
pass "类型字段混写校验与住宿跨午夜"

SWITCH_SOURCE=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"creationMode":"DIRECT","title":"类型切换测试","itineraryType":"TRANSPORT","itineraryDate":"2026-07-18","startTime":"11:00","endTime":"12:00","transportMode":"自驾","departureName":"杭州","destinationName":"桐庐","routeDetail":"高速集合","description":"保留备注"}')
SWITCH_ID=$(printf '%s' "$SWITCH_SOURCE" | jq -r '.data.itineraryId')
SWITCHED=$(api -X PUT "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$SWITCH_ID" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"itineraryType":"MEAL","mealType":"火锅","restaurantName":"江边火锅","address":"滨江路 1 号"}')
[[ $(printf '%s' "$SWITCHED" | jq -r '.data.transportMode') == null
  && $(printf '%s' "$SWITCHED" | jq -r '.data.departureName') == null
  && $(printf '%s' "$SWITCHED" | jq -r '.data.destinationName') == null
  && $(printf '%s' "$SWITCHED" | jq -r '.data.mealType') == 火锅
  && $(printf '%s' "$SWITCHED" | jq -r '.data.title') == 类型切换测试
  && $(printf '%s' "$SWITCHED" | jq -r '.data.description | contains("高速集合")') == true ]] || fail "类型切换字段清理或历史路线归并"
pass "类型切换清理旧字段并保留通用信息"

SINGLE=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"title":"单选改投测试","purpose":"GENERAL","decisionType":"RESTAURANT","voteType":"SINGLE","allowModify":true,"options":[{"optionText":"选项 A"},{"optionText":"选项 B"}]}')
SINGLE_ID=$(printf '%s' "$SINGLE" | jq -r '.data.pollId'); S1=$(printf '%s' "$SINGLE" | jq -r '.data.options[0].optionId'); S2=$(printf '%s' "$SINGLE" | jq -r '.data.options[1].optionId')
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$SINGLE_ID/votes" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionIds\":[$S1]}" >/dev/null
TODO_BEFORE=$(api "$BASE_URL/api/users/me/activity-todos" -H "Authorization: Bearer $TA" | jq -r '.data.todoCount')
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$SINGLE_ID/votes" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionIds\":[$S2]}" >/dev/null
SINGLE_DETAIL=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$SINGLE_ID" -H "Authorization: Bearer $TA")
[[ $(printf '%s' "$SINGLE_DETAIL" | jq -r '.data.currentUserOptionIds | length') == 1 && $(printf '%s' "$SINGLE_DETAIL" | jq -r '.data.currentUserOptionIds[0]') == "$S2" ]] || fail "单选改投只保留最新选项"
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$SINGLE_ID/votes" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionIds\":[$S2]}" >/dev/null
[[ $(api "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$SINGLE_ID" -H "Authorization: Bearer $TA" | jq -r '.data.options[] | select(.optionId == '"$S2"') | .voteCount') == 1 ]] || fail "重复单选不产生重复记录"
TODO_AFTER=$(api "$BASE_URL/api/users/me/activity-todos" -H "Authorization: Bearer $TA" | jq -r '.data.todoCount')
[[ "$TODO_AFTER" -le "$TODO_BEFORE" ]] || fail "完成投票后待办数量未减少"
pass "单选改投、重复提交与动态待办"

MULTI=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"title":"多选测试","purpose":"GENERAL","decisionType":"CONTENT","voteType":"MULTIPLE","allowModify":true,"options":[{"optionText":"选项 A"},{"optionText":"选项 B"}]}')
MULTI_ID=$(printf '%s' "$MULTI" | jq -r '.data.pollId'); M1=$(printf '%s' "$MULTI" | jq -r '.data.options[0].optionId'); M2=$(printf '%s' "$MULTI" | jq -r '.data.options[1].optionId')
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$MULTI_ID/votes" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionIds\":[$M1,$M2]}" >/dev/null
[[ $(api "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$MULTI_ID" -H "Authorization: Bearer $TA" | jq -r '.data.currentUserOptionIds | length') == 2 ]] || fail "多选保留多个不同选项"
pass "多选投票"
DIRECT=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"creationMode":"DIRECT","title":"集合","itineraryType":"TRANSPORT","itineraryDate":"2026-07-18","startTime":"09:00","endTime":"10:00","allDay":false}')
DIRECT_ID=$(printf '%s' "$DIRECT"|jq -r '.data.itineraryId'); [[ "$DIRECT_ID" != null ]] || fail "创建普通行程"; pass "创建普通行程"
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$DIRECT_ID/cancel" -H "Authorization: Bearer $TA" >/dev/null
[[ $(api "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" | jq --argjson itineraryId "$DIRECT_ID" '[.data[] | select(.itineraryId == $itineraryId)] | length') == 0 ]] || fail "默认行程列表不应展示已取消行程"
[[ $(api "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries?includeCanceled=true" -H "Authorization: Bearer $TA" | jq --argjson itineraryId "$DIRECT_ID" '[.data[] | select(.itineraryId == $itineraryId and .planningStatus == "CANCELED")] | length') == 1 ]] || fail "查看全部行程应展示已取消行程"
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$DIRECT_ID/restore" -H "Authorization: Bearer $TA" >/dev/null
[[ $(api "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$DIRECT_ID" -H "Authorization: Bearer $TA" | jq -r '.data.itinerary.planningStatus') == CONFIRMED ]] || fail "已取消行程恢复失败"
DISPOSABLE=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"creationMode":"DIRECT","title":"待删除行程","itineraryType":"OTHER","itineraryDate":"2026-07-19","allDay":true}')
DISPOSABLE_ID=$(printf '%s' "$DISPOSABLE"|jq -r '.data.itineraryId'); [[ "$DISPOSABLE_ID" != null ]] || fail "创建待删除行程"
STATUS=$(api -o /dev/null -w '%{http_code}' -X DELETE "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$DISPOSABLE_ID" -H "Authorization: Bearer $TA")
[[ "$STATUS" == 200 ]] || fail "物理删除行程"
STATUS=$(api -o /dev/null -w '%{http_code}' "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$DISPOSABLE_ID" -H "Authorization: Bearer $TA")
[[ "$STATUS" == 404 ]] || fail "已删除行程不应继续可查"
pass "行程取消默认隐藏、查看全部、恢复与物理删除"

TRANSPORT=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"creationMode":"DIRECT","title":"周日返程","itineraryType":"TRANSPORT","itineraryDate":"2026-07-19","startTime":"09:00","endTime":"12:00","allDay":false,"transportMode":"高铁","departureName":"亚朵酒店","destinationName":"上海","routeDetail":"酒店集合后出发"}')
TRANSPORT_ID=$(printf '%s' "$TRANSPORT"|jq -r '.data.itineraryId')
[[ $(printf '%s' "$TRANSPORT" | jq -r '.data.routeDetail') == null
  && $(printf '%s' "$TRANSPORT" | jq -r '.data.description') == 酒店集合后出发 ]] || fail "routeDetail 未归并到 description"
INVALID_ROUTE_SCOPE=$(api -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"title\":\"非法路线范围\",\"purpose\":\"UPDATE_ITINERARY\",\"decisionType\":\"ROUTE\",\"decisionScope\":[\"departureName\",\"routeDetail\"],\"targetItineraryId\":$TRANSPORT_ID,\"voteType\":\"SINGLE\",\"options\":[{\"optionText\":\"A\",\"resultPayload\":{\"departureName\":\"杭州\"}},{\"optionText\":\"B\",\"resultPayload\":{\"departureName\":\"上海\"}}]}")
[[ "$INVALID_ROUTE_SCOPE" == 400 ]] || fail "新路线投票不应允许 routeDetail"
INVALID_TITLE_SCOPE=$(api -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"title\":\"非法标题范围\",\"purpose\":\"UPDATE_ITINERARY\",\"decisionType\":\"ITINERARY_NAME\",\"decisionScope\":[\"title\"],\"targetItineraryId\":$TRANSPORT_ID,\"voteType\":\"SINGLE\",\"options\":[{\"optionText\":\"A\",\"resultPayload\":{\"title\":\"新标题 A\"}},{\"optionText\":\"B\",\"resultPayload\":{\"title\":\"新标题 B\"}}]}")
[[ "$INVALID_TITLE_SCOPE" == 400 ]] || fail "新关联投票不应修改稳定标题"
INVALID_DECISION=$(api -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"title\":\"非法类型决策\",\"purpose\":\"UPDATE_ITINERARY\",\"decisionType\":\"RESTAURANT\",\"targetItineraryId\":$TRANSPORT_ID,\"voteType\":\"SINGLE\",\"options\":[{\"optionText\":\"A\",\"resultPayload\":{\"restaurantName\":\"A\"}},{\"optionText\":\"B\",\"resultPayload\":{\"restaurantName\":\"B\"}}]}")
[[ "$INVALID_DECISION" == 400 ]] || fail "交通行程不应接受餐厅决策"
pass "routeDetail 兼容归并、决策范围和稳定标题保护"
TRANSPORT_POLL=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"title\":\"返程交通方式\",\"purpose\":\"UPDATE_ITINERARY\",\"decisionType\":\"TRANSPORT\",\"decisionScope\":[\"transportMode\"],\"targetItineraryId\":$TRANSPORT_ID,\"voteType\":\"SINGLE\",\"options\":[{\"optionText\":\"自驾\",\"resultPayload\":{\"transportMode\":\"自驾\"}},{\"optionText\":\"高铁\",\"resultPayload\":{\"transportMode\":\"高铁\"}}]}")
TRANSPORT_POLL_ID=$(printf '%s' "$TRANSPORT_POLL"|jq -r '.data.pollId')
TRANSPORT_OPTION=$(printf '%s' "$TRANSPORT_POLL"|jq -r '.data.options[0].optionId')
for T in "$TA" "$TB" "$TC"; do api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TRANSPORT_POLL_ID/votes" -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d "{\"optionIds\":[$TRANSPORT_OPTION]}" >/dev/null; done
TRANSPORT_CLOSED=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TRANSPORT_POLL_ID/close" -H "Authorization: Bearer $TA")
[[ $(printf '%s' "$TRANSPORT_CLOSED"|jq -r '.data.resultApplyStatus') == APPLIED ]] || fail "交通投票自动应用"
TRANSPORT_DETAIL=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$TRANSPORT_ID" -H "Authorization: Bearer $TA")
[[ $(printf '%s' "$TRANSPORT_DETAIL"|jq -r '.data.itinerary.transportMode') == 自驾 ]] || fail "交通方式未更新"
[[ $(printf '%s' "$TRANSPORT_DETAIL"|jq -r '.data.itinerary.title') == 周日返程 ]] || fail "交通投票覆盖了行程名称"
[[ $(printf '%s' "$TRANSPORT_DETAIL"|jq -r '.data.itinerary.departureName') == 亚朵酒店 && $(printf '%s' "$TRANSPORT_DETAIL"|jq -r '.data.itinerary.destinationName') == 上海 ]] || fail "交通投票修改了出发地或目的地"
[[ $(printf '%s' "$TRANSPORT_CLOSED"|jq -r '.data.applicationHistory | length') == 1 ]] || fail "交通投票应用历史未保存"
pass "交通投票只更新交通方式并保存应用历史"

INVALID_STATUS=$(api -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"title\":\"非法字段测试\",\"purpose\":\"UPDATE_ITINERARY\",\"decisionType\":\"TRANSPORT\",\"decisionScope\":[\"transportMode\"],\"targetItineraryId\":$TRANSPORT_ID,\"voteType\":\"SINGLE\",\"options\":[{\"optionText\":\"A\",\"resultPayload\":{\"transportMode\":\"飞机\",\"title\":\"不应覆盖\"}},{\"optionText\":\"B\",\"resultPayload\":{\"transportMode\":\"高铁\"}}]}")
[[ "$INVALID_STATUS" == 400 ]] || fail "未授权 resultPayload 字段应被拒绝"
pass "投票选项字段白名单"

WITH=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d '{"creationMode":"WITH_POLL","title":"周日晚餐","itineraryType":"MEAL","itineraryDate":"2026-07-18","startTime":"18:00","endTime":"20:00","allDay":false,"poll":{"title":"晚餐吃什么？","purpose":"UPDATE_ITINERARY","decisionType":"RESTAURANT","decisionScope":["mealType","restaurantName","address"],"voteType":"SINGLE","allowModify":true,"options":[{"optionText":"海底捞","resultPayload":{"mealType":"火锅","restaurantName":"海底捞湖滨店","address":"湖滨路 88 号"}},{"optionText":"烧烤","resultPayload":{"mealType":"烧烤","restaurantName":"湖滨烧烤店","address":"湖滨路 18 号"}}]}}')
WITH_ID=$(printf '%s' "$WITH"|jq -r '.data.itineraryId'); POLL_ID=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA"|jq -r '.data[] | select(.title == "晚餐吃什么？") | .pollId'); [[ "$WITH_ID" != null && "$POLL_ID" != null && -n "$POLL_ID" ]] || fail "待决定行程与投票"; pass "待决定行程与投票事务创建"
DETAIL=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$POLL_ID" -H "Authorization: Bearer $TA"); O1=$(printf '%s' "$DETAIL"|jq -r '.data.options[0].optionId')
for T in "$TA" "$TB" "$TC"; do api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$POLL_ID/votes" -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d "{\"optionIds\":[$O1]}" >/dev/null; done
CLOSED=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$POLL_ID/close" -H "Authorization: Bearer $TB")
[[ $(printf '%s' "$CLOSED"|jq -r '.data.resultApplyStatus') == APPLIED ]] || fail "唯一胜出自动应用"
MEAL_DETAIL=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$WITH_ID" -H "Authorization: Bearer $TA")
[[ $(printf '%s' "$MEAL_DETAIL"|jq -r '.data.itinerary.title') == 周日晚餐 ]] || fail "餐厅投票覆盖了行程名称"
[[ $(printf '%s' "$MEAL_DETAIL"|jq -r '.data.itinerary.mealType') == 火锅 && $(printf '%s' "$MEAL_DETAIL"|jq -r '.data.itinerary.restaurantName') == 海底捞湖滨店 && $(printf '%s' "$MEAL_DETAIL"|jq -r '.data.itinerary.address') == "湖滨路 88 号" ]] || fail "餐厅投票字段应用错误"
pass "唯一胜出只更新餐厅相关字段"
STATUS=$(api -o /dev/null -w '%{http_code}' -X PUT "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries/$DIRECT_ID" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d '{"title":"越权修改"}')
[[ "$STATUS" == 403 ]] || fail "普通成员越权校验"; pass "成员权限校验"
OUTSIDER=$(login outsider); TO=$(token "$OUTSIDER")
STATUS=$(api -o /dev/null -w '%{http_code}' "$BASE_URL/api/activities/$ACTIVITY_ID/collaboration-summary" -H "Authorization: Bearer $TO")
[[ "$STATUS" == 403 ]] || fail "非成员不能读取其他活动待办"; pass "非成员待办权限校验"
OUTSIDER_TODOS=$(api "$BASE_URL/api/users/me/activity-todos" -H "Authorization: Bearer $TO")
[[ $(printf '%s' "$OUTSIDER_TODOS" | jq --argjson activityId "$ACTIVITY_ID" '[.data.todos[] | select(.activityId == $activityId)] | length') == 0 ]] || fail "待办聚合泄露非成员活动"
pass "待办聚合不泄露非成员活动"
TIE=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"title":"并列测试","purpose":"CREATE_ITINERARY","decisionType":"PLACE","voteType":"SINGLE","itineraryTemplate":{"title":"候选活动","itineraryDate":"2026-07-19"},"options":[{"optionText":"A","resultPayload":{"locationName":"地点 A"}},{"optionText":"B","resultPayload":{"locationName":"地点 B"}}]}')
TIE_ID=$(printf '%s' "$TIE"|jq -r '.data.pollId'); T1=$(printf '%s' "$TIE"|jq -r '.data.options[0].optionId'); T2=$(printf '%s' "$TIE"|jq -r '.data.options[1].optionId')
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TIE_ID/votes" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionIds\":[$T1]}" >/dev/null; api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TIE_ID/votes" -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' -d "{\"optionIds\":[$T2]}" >/dev/null
REVIEW=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TIE_ID/close" -H "Authorization: Bearer $TA")
[[ $(printf '%s' "$REVIEW"|jq -r '.data.resultApplyStatus') == REVIEW_REQUIRED ]] || fail "并列人工确认"
PREVIEW=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TIE_ID/result-preview?optionId=$T1" -H "Authorization: Bearer $TA")
[[ $(printf '%s' "$PREVIEW"|jq -r '.data.changedFields | length') == 1 && $(printf '%s' "$PREVIEW"|jq -r '.data.changedFields[0].field') == locationName ]] || fail "并列结果应用预览"
APPLIED=$(api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/polls/$TIE_ID/apply-result" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d "{\"optionId\":$T1}")
[[ $(printf '%s' "$APPLIED"|jq -r '.data.applicationHistory | length') == 1 ]] || fail "并列人工应用历史"
pass "并列进入人工确认、预览并保存应用历史"
SUMMARY=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/collaboration-summary" -H "Authorization: Bearer $TA"); [[ $(printf '%s' "$SUMMARY"|jq -r '.code') == SUCCESS ]] || fail "协作摘要"; [[ $(printf '%s' "$SUMMARY"|jq -r '.data.todoCount') == 0 ]] || fail "已处理投票不应保留待办"; pass "活动详情协作摘要"
api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/end" -H "Authorization: Bearer $TA" >/dev/null
READ_ONLY=$(api -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/itineraries" -H "Authorization: Bearer $TA" -H 'Content-Type: application/json' -d '{"creationMode":"DIRECT","title":"不可创建","itineraryDate":"2026-07-19"}')
[[ "$READ_ONLY" == 403 ]] || fail "结束活动只读"; pass "结束活动行程投票只读"
echo "[PASS] P1 smoke complete activityId=$ACTIVITY_ID"
