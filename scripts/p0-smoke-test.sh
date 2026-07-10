#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
API_BASE="${BASE_URL%/}/api"
RUN_ID="$(date +%Y%m%d%H%M%S)"
TEST_IMAGE="/tmp/playmate-p0-smoke-${RUN_ID}.png"

A_TOKEN=""
B_TOKEN=""
C_TOKEN=""
A_USER_ID=""
B_USER_ID=""
C_USER_ID=""
ACTIVITY_ID=""
SHARE_CODE=""
ENDED_ACTIVITY_ID=""
ENDED_SHARE_CODE=""
CANCELED_ACTIVITY_ID=""
CANCELED_SHARE_CODE=""

pass() {
  printf '[PASS] %s\n' "$1"
}

fail() {
  printf '[FAIL] %s\n' "$1" >&2
  print_summary >&2
  exit 1
}

print_summary() {
  cat <<EOF

P0 smoke test summary:
  runId: ${RUN_ID}
  userA: ${A_USER_ID:-}
  userB: ${B_USER_ID:-}
  userC: ${C_USER_ID:-}
  activityId: ${ACTIVITY_ID:-}
  shareCode: ${SHARE_CODE:-}
  endedActivityId: ${ENDED_ACTIVITY_ID:-}
  endedShareCode: ${ENDED_SHARE_CODE:-}
  canceledActivityId: ${CANCELED_ACTIVITY_ID:-}
  canceledShareCode: ${CANCELED_SHARE_CODE:-}
EOF
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "缺少命令：$1。请先安装后再运行脚本。"
  fi
}

json_get() {
  local json="$1"
  local filter="$2"
  printf '%s' "$json" | jq -er "$filter"
}

assert_api_success() {
  local json="$1"
  local step="$2"
  local code
  code="$(json_get "$json" '.code')"
  if [[ "$code" != "SUCCESS" ]]; then
    printf '%s\n' "$json" >&2
    fail "${step}：接口 code 不是 SUCCESS，而是 $code"
  fi
}

assert_http_status() {
  local actual="$1"
  local expected="$2"
  local step="$3"
  if [[ "$actual" != "$expected" ]]; then
    fail "${step}：HTTP 状态码期望 $expected，实际 $actual"
  fi
}

assert_equals() {
  local actual="$1"
  local expected="$2"
  local step="$3"
  if [[ "$actual" != "$expected" ]]; then
    fail "${step}：期望 $expected，实际 $actual"
  fi
}

assert_non_empty() {
  local value="$1"
  local step="$2"
  if [[ -z "$value" || "$value" == "null" ]]; then
    fail "${step}：结果为空"
  fi
}

post_json() {
  local url="$1"
  local token="$2"
  local payload="$3"
  if [[ -n "$token" ]]; then
    curl -sS -X POST "$url" \
      -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' \
      -d "$payload"
  else
    curl -sS -X POST "$url" \
      -H 'Content-Type: application/json' \
      -d "$payload"
  fi
}

put_json() {
  local url="$1"
  local token="$2"
  local payload="$3"
  curl -sS -X PUT "$url" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -d "$payload"
}

get_json() {
  local url="$1"
  local token="${2:-}"
  if [[ -n "$token" ]]; then
    curl -sS "$url" -H "Authorization: Bearer $token"
  else
    curl -sS "$url"
  fi
}

http_status() {
  local method="$1"
  local url="$2"
  local token="${3:-}"
  local payload="${4:-}"
  local output_file="$5"
  if [[ -n "$token" && -n "$payload" ]]; then
    curl -sS -o "$output_file" -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' \
      -d "$payload"
  elif [[ -n "$token" ]]; then
    curl -sS -o "$output_file" -w '%{http_code}' -X "$method" "$url" \
      -H "Authorization: Bearer $token"
  elif [[ -n "$payload" ]]; then
    curl -sS -o "$output_file" -w '%{http_code}' -X "$method" "$url" \
      -H 'Content-Type: application/json' \
      -d "$payload"
  else
    curl -sS -o "$output_file" -w '%{http_code}' -X "$method" "$url"
  fi
}

decode_base64_to_file() {
  local content="$1"
  local target="$2"
  if printf '' | base64 --decode >/dev/null 2>&1; then
    printf '%s' "$content" | base64 --decode > "$target"
  else
    printf '%s' "$content" | base64 -D > "$target"
  fi
}

create_test_png() {
  # 1x1 PNG image.
  decode_base64_to_file \
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=' \
    "$TEST_IMAGE"
}

login() {
  local suffix="$1"
  local nickname="$2"
  local response
  response="$(post_json "${API_BASE}/auth/wx-login" "" "{\"mockOpenid\":\"p0_smoke_user_${suffix}_${RUN_ID}\",\"nickname\":\"${nickname}\",\"avatarUrl\":\"\"}")"
  assert_api_success "$response" "用户 ${suffix} 登录"
  local token user_id
  token="$(json_get "$response" '.data.token')"
  user_id="$(json_get "$response" '.data.userId')"
  assert_non_empty "$token" "用户 ${suffix} token"
  assert_non_empty "$user_id" "用户 ${suffix} userId"
  printf '%s|%s' "$token" "$user_id"
}

create_activity() {
  local token="$1"
  local name="$2"
  local cover_file_id="${3:-null}"
  local payload
  payload="$(jq -n \
    --arg name "$name" \
    --arg type "TRAVEL" \
    --arg locationName "P0 联调城市" \
    --arg address "P0 联调地址" \
    --arg startDate "2026-07-05" \
    --arg endDate "2026-07-06" \
    --arg description "P0 smoke test 自动生成活动" \
    --argjson coverFileId "$cover_file_id" \
    '{name:$name,type:$type,coverFileId:$coverFileId,locationName:$locationName,address:$address,startDate:$startDate,endDate:$endDate,description:$description}')"
  local response
  response="$(post_json "${API_BASE}/activities" "$token" "$payload")"
  assert_api_success "$response" "创建活动：$name"
  local activity_id share_code
  activity_id="$(json_get "$response" '.data.activityId')"
  share_code="$(json_get "$response" '.data.shareCode')"
  assert_non_empty "$activity_id" "创建活动 activityId"
  assert_non_empty "$share_code" "创建活动 shareCode"
  printf '%s|%s' "$activity_id" "$share_code"
}

main() {
  require_command curl
  require_command jq

  local health
  health="$(get_json "${API_BASE}/health")"
  assert_api_success "$health" "health 接口"
  pass "health 接口可用"

  local login_pair
  login_pair="$(login "a" "P0 联调用户A")"
  A_TOKEN="${login_pair%%|*}"
  A_USER_ID="${login_pair##*|}"
  pass "用户 A 登录成功"

  create_test_png
  local upload_response
  upload_response="$(curl -sS -X POST "${API_BASE}/files/upload" \
    -H "Authorization: Bearer ${A_TOKEN}" \
    -F "fileType=ACTIVITY_COVER" \
    -F "file=@${TEST_IMAGE};type=image/png")"
  assert_api_success "$upload_response" "用户 A 上传测试图片"
  local file_id
  file_id="$(json_get "$upload_response" '.data.fileId')"
  assert_non_empty "$file_id" "上传文件 fileId"
  pass "用户 A 上传测试图片成功，fileId=${file_id}"

  local activity_pair
  activity_pair="$(create_activity "$A_TOKEN" "P0 Smoke Test 活动 ${RUN_ID}" "$file_id")"
  ACTIVITY_ID="${activity_pair%%|*}"
  SHARE_CODE="${activity_pair##*|}"
  pass "用户 A 创建活动成功，activityId=${ACTIVITY_ID}，shareCode=${SHARE_CODE}"

  local list_response
  list_response="$(get_json "${API_BASE}/activities" "$A_TOKEN")"
  assert_api_success "$list_response" "用户 A 查询活动列表"
  local list_count
  list_count="$(printf '%s' "$list_response" | jq -er --argjson id "$ACTIVITY_ID" '[.data[] | select(.activityId == $id)] | length')"
  assert_equals "$list_count" "1" "活动列表包含新活动"
  pass "用户 A 查询活动列表能看到新活动"

  local detail_response
  detail_response="$(get_json "${API_BASE}/activities/${ACTIVITY_ID}" "$A_TOKEN")"
  assert_api_success "$detail_response" "用户 A 查询活动详情"
  assert_equals "$(json_get "$detail_response" '.data.activityId')" "$ACTIVITY_ID" "活动详情 activityId"
  assert_equals "$(json_get "$detail_response" '.data.memberCount')" "1" "创建后 memberCount"
  pass "用户 A 查询活动详情成功"

  local invite_public_response
  invite_public_response="$(get_json "${API_BASE}/activity-invites/${SHARE_CODE}")"
  assert_api_success "$invite_public_response" "未登录查询邀请信息"
  assert_equals "$(json_get "$invite_public_response" '.data.shareCode')" "$SHARE_CODE" "邀请信息 shareCode"
  local leaked_members
  leaked_members="$(printf '%s' "$invite_public_response" | jq '[paths | map(tostring) | join(".") | select(test("members|openid|memberList"))] | length')"
  assert_equals "$leaked_members" "0" "邀请信息不泄露成员列表或 openid"
  pass "未登录能查看有限邀请信息，且不泄露完整成员列表"

  login_pair="$(login "b" "P0 联调用户B")"
  B_TOKEN="${login_pair%%|*}"
  B_USER_ID="${login_pair##*|}"
  pass "用户 B 登录成功"

  local join_response
  join_response="$(post_json "${API_BASE}/activity-invites/${SHARE_CODE}/join" "$B_TOKEN" '{}')"
  assert_api_success "$join_response" "用户 B 加入活动"
  assert_equals "$(json_get "$join_response" '.data.memberStatus')" "ACTIVE" "B 加入成员状态"
  pass "用户 B 通过 shareCode 加入活动成功"

  local join_again_response
  join_again_response="$(post_json "${API_BASE}/activity-invites/${SHARE_CODE}/join" "$B_TOKEN" '{}')"
  assert_api_success "$join_again_response" "用户 B 重复加入活动"
  assert_equals "$(json_get "$join_again_response" '.data.memberStatus')" "ACTIVE" "B 重复加入成员状态"

  local detail_after_join
  detail_after_join="$(get_json "${API_BASE}/activities/${ACTIVITY_ID}" "$A_TOKEN")"
  assert_api_success "$detail_after_join" "重复加入后查询活动详情"
  assert_equals "$(json_get "$detail_after_join" '.data.memberCount')" "2" "重复加入不重复增加 member_count"
  pass "用户 B 重复加入不会重复插入成员"

  local members_response
  members_response="$(get_json "${API_BASE}/activities/${ACTIVITY_ID}/members" "$A_TOKEN")"
  assert_api_success "$members_response" "用户 A 查询成员列表"
  assert_equals "$(json_get "$members_response" '.data | length')" "2" "成员列表人数"
  assert_equals "$(printf '%s' "$members_response" | jq -er --argjson uid "$A_USER_ID" '.data[] | select(.userId == $uid) | .role')" "CREATOR" "A 是创建者"
  assert_equals "$(printf '%s' "$members_response" | jq -er --argjson uid "$B_USER_ID" '.data[] | select(.userId == $uid) | .role')" "MEMBER" "B 是普通成员"
  local member_openid_count
  member_openid_count="$(printf '%s' "$members_response" | jq '[paths | map(tostring) | join(".") | select(test("openid"))] | length')"
  assert_equals "$member_openid_count" "0" "成员列表不返回 openid"
  pass "用户 A 查询成员列表能看到 A=CREATOR、B=MEMBER，且不返回 openid"

  local a_member_id b_member_id
  a_member_id="$(printf '%s' "$members_response" | jq -er --argjson uid "$A_USER_ID" '.data[] | select(.userId == $uid) | .memberId')"
  b_member_id="$(printf '%s' "$members_response" | jq -er --argjson uid "$B_USER_ID" '.data[] | select(.userId == $uid) | .memberId')"
  assert_non_empty "$a_member_id" "A memberId"
  assert_non_empty "$b_member_id" "B memberId"

  local b_remove_a_status
  b_remove_a_status="$(http_status "DELETE" "${API_BASE}/activities/${ACTIVITY_ID}/members/${a_member_id}" "$B_TOKEN" "" "/tmp/playmate-p0-b-remove-a-${RUN_ID}.json")"
  assert_equals "$b_remove_a_status" "403" "普通成员不能移除别人"
  pass "普通成员不能移除别人"

  local a_remove_self_status
  a_remove_self_status="$(http_status "DELETE" "${API_BASE}/activities/${ACTIVITY_ID}/members/${a_member_id}" "$A_TOKEN" "" "/tmp/playmate-p0-a-remove-self-${RUN_ID}.json")"
  if [[ "$a_remove_self_status" != "400" && "$a_remove_self_status" != "403" ]]; then
    fail "创建者不能移除自己：HTTP 状态码期望 400 或 403，实际 ${a_remove_self_status}"
  fi
  pass "创建者不能移除自己"

  local nickname_response
  nickname_response="$(put_json "${API_BASE}/activities/${ACTIVITY_ID}/members/me/nickname" "$B_TOKEN" '{"nickname":"P0 烟测昵称B"}')"
  assert_api_success "$nickname_response" "用户 B 修改活动内昵称"
  assert_equals "$(json_get "$nickname_response" '.data.nickname')" "P0 烟测昵称B" "B 活动内昵称"
  pass "用户 B 修改自己的活动内昵称成功"

  local remove_response
  remove_response="$(curl -sS -X DELETE "${API_BASE}/activities/${ACTIVITY_ID}/members/${b_member_id}" -H "Authorization: Bearer ${A_TOKEN}")"
  assert_api_success "$remove_response" "用户 A 移除 B"
  pass "用户 A 移除 B 成功"

  local b_detail_status
  b_detail_status="$(http_status "GET" "${API_BASE}/activities/${ACTIVITY_ID}" "$B_TOKEN" "" "/tmp/playmate-p0-b-detail-${RUN_ID}.json")"
  assert_equals "$b_detail_status" "403" "B 被移除后不能访问活动详情"
  pass "B 被移除后访问活动详情返回 403"

  local b_members_status
  b_members_status="$(http_status "GET" "${API_BASE}/activities/${ACTIVITY_ID}/members" "$B_TOKEN" "" "/tmp/playmate-p0-b-members-${RUN_ID}.json")"
  assert_equals "$b_members_status" "403" "B 被移除后不能访问成员列表"
  pass "B 被移除后访问成员列表返回 403"

  local b_join_removed_status
  b_join_removed_status="$(http_status "POST" "${API_BASE}/activity-invites/${SHARE_CODE}/join" "$B_TOKEN" '{}' "/tmp/playmate-p0-b-join-removed-${RUN_ID}.json")"
  assert_equals "$b_join_removed_status" "403" "B 被移除后不能重新加入"
  pass "B 被移除后重新 join 返回 403"

  local members_after_remove
  members_after_remove="$(get_json "${API_BASE}/activities/${ACTIVITY_ID}/members" "$A_TOKEN")"
  assert_api_success "$members_after_remove" "移除后 A 查询成员列表"
  assert_equals "$(json_get "$members_after_remove" '.data | length')" "1" "移除后成员列表只返回 ACTIVE 成员"
  local removed_in_list_count
  removed_in_list_count="$(printf '%s' "$members_after_remove" | jq --argjson uid "$B_USER_ID" '[.data[] | select(.userId == $uid)] | length')"
  assert_equals "$removed_in_list_count" "0" "成员列表默认不返回 REMOVED 成员"
  local detail_after_remove
  detail_after_remove="$(get_json "${API_BASE}/activities/${ACTIVITY_ID}" "$A_TOKEN")"
  assert_api_success "$detail_after_remove" "移除后 A 查询详情"
  assert_equals "$(json_get "$detail_after_remove" '.data.memberCount')" "1" "member_count 从 2 回到 1"
  pass "member_count 从 2 回到 1，成员列表不返回 REMOVED 成员"

  local remove_again_response
  remove_again_response="$(curl -sS -X DELETE "${API_BASE}/activities/${ACTIVITY_ID}/members/${b_member_id}" -H "Authorization: Bearer ${A_TOKEN}")"
  assert_api_success "$remove_again_response" "重复移除已移除成员"
  local detail_after_remove_again
  detail_after_remove_again="$(get_json "${API_BASE}/activities/${ACTIVITY_ID}" "$A_TOKEN")"
  assert_api_success "$detail_after_remove_again" "重复移除后查询详情"
  assert_equals "$(json_get "$detail_after_remove_again" '.data.memberCount')" "1" "重复移除不重复减少 member_count"
  pass "重复移除不会重复减少 member_count，且 member_count 不小于 1"

  login_pair="$(login "c" "P0 联调用户C")"
  C_TOKEN="${login_pair%%|*}"
  C_USER_ID="${login_pair##*|}"
  pass "用户 C 登录成功"

  activity_pair="$(create_activity "$A_TOKEN" "P0 Smoke Test 已结束活动 ${RUN_ID}" "null")"
  ENDED_ACTIVITY_ID="${activity_pair%%|*}"
  ENDED_SHARE_CODE="${activity_pair##*|}"
  local end_response
  end_response="$(post_json "${API_BASE}/activities/${ENDED_ACTIVITY_ID}/end" "$A_TOKEN" '{}')"
  assert_api_success "$end_response" "结束活动"
  local c_join_ended_status
  c_join_ended_status="$(http_status "POST" "${API_BASE}/activity-invites/${ENDED_SHARE_CODE}/join" "$C_TOKEN" '{}' "/tmp/playmate-p0-c-join-ended-${RUN_ID}.json")"
  if [[ "$c_join_ended_status" != "400" && "$c_join_ended_status" != "403" ]]; then
    fail "已结束活动用户 C 不能加入：HTTP 状态码期望 400 或 403，实际 ${c_join_ended_status}"
  fi
  pass "创建另一个活动，结束后用户 C 不能加入"

  activity_pair="$(create_activity "$A_TOKEN" "P0 Smoke Test 已取消活动 ${RUN_ID}" "null")"
  CANCELED_ACTIVITY_ID="${activity_pair%%|*}"
  CANCELED_SHARE_CODE="${activity_pair##*|}"
  local cancel_response
  cancel_response="$(post_json "${API_BASE}/activities/${CANCELED_ACTIVITY_ID}/cancel" "$A_TOKEN" '{}')"
  assert_api_success "$cancel_response" "取消活动"
  local c_join_canceled_status
  c_join_canceled_status="$(http_status "POST" "${API_BASE}/activity-invites/${CANCELED_SHARE_CODE}/join" "$C_TOKEN" '{}' "/tmp/playmate-p0-c-join-canceled-${RUN_ID}.json")"
  if [[ "$c_join_canceled_status" != "400" && "$c_join_canceled_status" != "403" ]]; then
    fail "已取消活动用户 C 不能加入：HTTP 状态码期望 400 或 403，实际 ${c_join_canceled_status}"
  fi
  pass "创建另一个活动，取消后用户 C 不能加入"

  print_summary
  pass "P0 全链路 smoke test 通过"
}

main "$@"
