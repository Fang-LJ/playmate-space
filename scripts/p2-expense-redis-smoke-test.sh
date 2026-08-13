#!/usr/bin/env bash
set -euo pipefail

# Optional cache verification. It only clears playmate's finance snapshot keys and never removes Docker volumes.
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${PLAYMATE_ENV_FILE:-$ROOT_DIR/deploy/.env}"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.local.yml"
BASE_URL="${PLAYMATE_REDIS_SMOKE_BASE_URL:-http://127.0.0.1:18080}"
PORT="${BASE_URL##*:}"
LOG_FILE="${TMPDIR:-/tmp}/playmate-redis-smoke-$$.log"
RUN_ID="$(date +%s)"
REDIS_WAS_RUNNING=false
SERVER_PID=""

pass() { printf '[PASS] %s\n' "$1"; }
fail() { printf '[FAIL] %s\n' "$1" >&2; exit 1; }
api() { curl -sS "$@"; }
assert_success() { [[ "$(jq -r '.code' <<<"$1")" == "SUCCESS" ]] || { printf '%s\n' "$1" >&2; fail "$2"; }; }

cleanup() {
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
    wait "$SERVER_PID" 2>/dev/null || true
  fi
  if [[ "$REDIS_WAS_RUNNING" == true ]]; then
    docker start playmate-redis >/dev/null 2>&1 || true
  else
    docker stop playmate-redis >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

for command in curl jq docker; do command -v "$command" >/dev/null 2>&1 || fail "缺少命令：$command"; done
[[ -f "$ENV_FILE" ]] || fail "找不到环境文件：$ENV_FILE。请先执行 cp deploy/.env.example deploy/.env"

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
REDIS_PASSWORD="${REDIS_PASSWORD:-playmate_redis_dev_password}"

if docker inspect -f '{{.State.Running}}' playmate-redis 2>/dev/null | grep -qx true; then
  REDIS_WAS_RUNNING=true
fi
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" --profile optional up -d playmate-redis >/dev/null

for _ in {1..20}; do
  if docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" playmate-redis redis-cli ping 2>/dev/null | grep -qx PONG; then break; fi
  sleep 1
done
docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" playmate-redis redis-cli ping | grep -qx PONG || fail 'Redis 未就绪'

# Only this cache namespace is removed. MySQL, MinIO and Redis volumes are untouched.
CACHE_KEYS=()
while IFS= read -r cache_key; do
  [[ -n "$cache_key" ]] && CACHE_KEYS+=("$cache_key")
done < <(docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" playmate-redis redis-cli --scan --pattern 'playmate:finance:snapshot:*')
if [[ ${#CACHE_KEYS[@]} -gt 0 ]]; then
  docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" playmate-redis redis-cli DEL "${CACHE_KEYS[@]}" >/dev/null
fi
pass '可选 Redis 已启动，旧 SettlementSnapshot 缓存已清理'

(
  cd "$ROOT_DIR/playmate-server"
  exec env SPRING_PROFILES_ACTIVE=local SERVER_PORT="$PORT" PLAYMATE_FINANCE_CACHE_ENABLED=true \
    mvn -s ../docs/maven-central-settings.xml spring-boot:run >"$LOG_FILE" 2>&1
) &
SERVER_PID=$!
for _ in {1..45}; do
  if curl -fsS "$BASE_URL/api/health" >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -fsS "$BASE_URL/api/health" >/dev/null || { tail -80 "$LOG_FILE" >&2; fail "隔离后端未能启动"; }
pass "隔离后端已启动：$BASE_URL"

login() {
  local suffix="$1"
  api -X POST "$BASE_URL/api/auth/wx-login" -H 'Content-Type: application/json' \
    -d "{\"mockOpenid\":\"redis_smoke_${suffix}_${RUN_ID}\",\"nickname\":\"Redis 联调${suffix}\"}"
}

LOGIN_A=$(login A); assert_success "$LOGIN_A" '用户 A 登录'
LOGIN_B=$(login B); assert_success "$LOGIN_B" '用户 B 登录'
TOKEN_A=$(jq -r '.data.token' <<<"$LOGIN_A"); USER_A=$(jq -r '.data.userId' <<<"$LOGIN_A")
TOKEN_B=$(jq -r '.data.token' <<<"$LOGIN_B"); USER_B=$(jq -r '.data.userId' <<<"$LOGIN_B")

CREATE=$(api -X POST "$BASE_URL/api/activities" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Redis Snapshot 联调 $RUN_ID\",\"type\":\"TRAVEL\",\"startDate\":\"2026-08-13\",\"endDate\":\"2026-08-14\"}")
assert_success "$CREATE" '创建活动'
ACTIVITY_ID=$(jq -r '.data.activityId' <<<"$CREATE"); SHARE_CODE=$(jq -r '.data.shareCode' <<<"$CREATE")
JOIN=$(api -X POST "$BASE_URL/api/activity-invites/$SHARE_CODE/join" -H "Authorization: Bearer $TOKEN_B")
assert_success "$JOIN" '用户 B 加入活动'

create_expense() {
  local title="$1" amount="$2" request_id="$3"
  api -X POST "$BASE_URL/api/activities/$ACTIVITY_ID/expenses" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
    -d "{\"title\":\"$title\",\"category\":\"FOOD\",\"amount\":\"$amount\",\"payerUserId\":$USER_A,\"expenseTime\":\"2026-08-13T12:00:00\",\"splitMode\":\"EQUAL\",\"shares\":[{\"userId\":$USER_A},{\"userId\":$USER_B}],\"clientRequestId\":\"$request_id\"}"
}

FIRST_EXPENSE=$(create_expense 'Redis 首笔账单' '100.00' "redis-first-$RUN_ID"); assert_success "$FIRST_EXPENSE" '创建第一笔账单'
DASHBOARD_V1=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
assert_success "$DASHBOARD_V1" '首次 dashboard'
VERSION_1=$(jq -r '.data.financeVersion' <<<"$DASHBOARD_V1")
KEY_1="playmate:finance:snapshot:v1:$ACTIVITY_ID:$VERSION_1"
docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" playmate-redis redis-cli EXISTS "$KEY_1" | grep -qx 1 || fail '首次 dashboard 没有写入 Redis Snapshot'
pass "首次 dashboard 写入缓存：$KEY_1"

DASHBOARD_HIT=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
assert_success "$DASHBOARD_HIT" '第二次 dashboard 缓存命中'
[[ "$(jq -r '.data.financeVersion' <<<"$DASHBOARD_HIT")" == "$VERSION_1" ]] || fail '缓存命中 financeVersion 错误'
pass '第二次 dashboard 返回同一版本快照'

SECOND_EXPENSE=$(create_expense 'Redis 第二笔账单' '20.00' "redis-second-$RUN_ID"); assert_success "$SECOND_EXPENSE" '创建第二笔账单'
DASHBOARD_V2=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
assert_success "$DASHBOARD_V2" '版本递增后的 dashboard'
VERSION_2=$(jq -r '.data.financeVersion' <<<"$DASHBOARD_V2")
[[ "$VERSION_2" == "$((VERSION_1 + 1))" ]] || fail '新增账单后 financeVersion 未递增'
KEY_2="playmate:finance:snapshot:v1:$ACTIVITY_ID:$VERSION_2"
docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" playmate-redis redis-cli EXISTS "$KEY_2" | grep -qx 1 || fail '新版本没有写入新缓存 key'
pass "financeVersion 更新后使用新 key：$KEY_2"

docker stop playmate-redis >/dev/null
FALLBACK_DASHBOARD=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
FALLBACK_SUMMARY=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/summary" -H "Authorization: Bearer $TOKEN_A")
assert_success "$FALLBACK_DASHBOARD" 'Redis 宕机后的 dashboard 回源'
assert_success "$FALLBACK_SUMMARY" 'Redis 宕机后的 summary 回源'
pass 'Redis 宕机时 summary/dashboard 均自动回源 MySQL'

docker start playmate-redis >/dev/null
for _ in {1..20}; do
  if docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" playmate-redis redis-cli ping 2>/dev/null | grep -qx PONG; then break; fi
  sleep 1
done
docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" playmate-redis redis-cli SET "$KEY_2" 'invalid-json' >/dev/null

# Redis is reachable before Lettuce's existing connection necessarily finishes reconnecting.
# Retry the request briefly so this assertion verifies cache self-healing rather than client reconnect timing.
CACHE_RECOVERED=false
for _ in {1..10}; do
  RECOVERED=$(api "$BASE_URL/api/activities/$ACTIVITY_ID/expenses/dashboard" -H "Authorization: Bearer $TOKEN_A")
  assert_success "$RECOVERED" '损坏缓存后的 dashboard 回源'
  CACHE_VALUE=$(docker exec -e REDISCLI_AUTH="$REDIS_PASSWORD" playmate-redis redis-cli --raw GET "$KEY_2")
  if jq -e '.schemaVersion == 1 and .financeVersion == '"$VERSION_2" >/dev/null <<<"$CACHE_VALUE"; then
    CACHE_RECOVERED=true
    break
  fi
  sleep 1
done
[[ "$CACHE_RECOVERED" == true ]] || fail '损坏 JSON 未被重建为有效 SettlementSnapshot'
pass '损坏 JSON 已忽略、回源并重建'

echo "[PASS] P2 Redis cache smoke complete activityId=$ACTIVITY_ID version=$VERSION_2 key=$KEY_2"
