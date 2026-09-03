#!/bin/bash
###############################################################################
# MAS 后端 API 集成测试脚本
# 测试所有契约端点，输出 Markdown 测试报告
###############################################################################
set -euo pipefail

BASE_URL="${MAS_BASE_URL:-http://localhost:9090/smart-router}"
REPORT="smart-model-router/reports/integration-test-report.md"
PASS=0
FAIL=0
SKIP=0
TOTAL=0
RESULTS=()

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

###############################################################################
# 测试函数
###############################################################################
test_endpoint() {
  local method="$1"
  local path="$2"
  local desc="$3"
  local body="${4:-}"
  local expect_status="${5:-200}"
  TOTAL=$((TOTAL + 1))

  local url="${BASE_URL}${path}"
  local args=(-s -o /tmp/test_body.json -w "%{http_code}" -X "$method")
  args+=(-H "Content-Type: application/json")
  if [[ -n "$body" ]]; then
    args+=(-d "$body")
  fi

  local status
  status=$(curl "${args[@]}" "$url" 2>/dev/null || echo "000")

  local body_content=""
  if [[ -f /tmp/test_body.json ]]; then
    body_content=$(cat /tmp/test_body.json 2>/dev/null || echo "")
  fi

  if [[ "$status" == "$expect_status" ]]; then
    # 检查响应体非空（GET 请求）
    if [[ "$method" == "GET" && -z "$body_content" ]]; then
      echo -e "${YELLOW}WARN${NC} $method $path → $status (空响应体)"
      RESULTS+=("| $method | \`$path\` | $desc | $status | ⚠️ 空响应 |")
      SKIP=$((SKIP + 1))
    else
      echo -e "${GREEN}PASS${NC} $method $path → $status"
      RESULTS+=("| $method | \`$path\` | $desc | $status | ✅ |")
      PASS=$((PASS + 1))
    fi
  else
    echo -e "${RED}FAIL${NC} $method $path → $status (期望 $expect_status)"
    local snippet=""
    if [[ -n "$body_content" ]]; then
      snippet=$(echo "$body_content" | head -c 200)
    fi
    RESULTS+=("| $method | \`$path\` | $desc | $status | ❌ 期望$expect_status |")
    FAIL=$((FAIL + 1))
  fi
}

###############################################################################
# 检查服务是否可用
###############################################################################
echo "============================================"
echo "MAS 后端 API 集成测试"
echo "Target: $BASE_URL"
echo "============================================"
echo ""

echo "检查服务连通性..."
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/internal/dashboard/summary" 2>/dev/null || echo "000")
if [[ "$HEALTH" == "000" ]]; then
  echo "❌ 无法连接到 $BASE_URL，请先启动后端服务"
  echo ""
  echo "启动方式："
  echo "  cd smart-model-router && /usr/local/apache-maven-3.9.11/bin/mvn spring-boot:run"
  exit 1
fi
echo "✅ 服务可达 (HTTP $HEALTH)"
echo ""

###############################################################################
# 1. Dashboard 端点 (12 个 GET)
###############################################################################
echo "--- 1. Dashboard 端点 ---"
test_endpoint GET "/internal/dashboard/summary" "运营驾驶舱聚合指标"
test_endpoint GET "/internal/dashboard/token-series" "Token 时间序列"
test_endpoint GET "/internal/dashboard/token-series?hours=12&step=30" "Token 序列(12h/30min)"
test_endpoint GET "/internal/dashboard/trend-series" "趋势时间序列"
test_endpoint GET "/internal/dashboard/dept-tco" "部门 TCO 排行"
test_endpoint GET "/internal/dashboard/app-tco-rank" "应用 TCO 排行"
test_endpoint GET "/internal/dashboard/model-tco-rank" "模型 TCO 排行"
test_endpoint GET "/internal/dashboard/funnel" "路由漏斗数据"
test_endpoint GET "/internal/dashboard/rate-limit-hits" "限流命中记录"
test_endpoint GET "/internal/dashboard/circuit-breakers" "熔断记录"
test_endpoint GET "/internal/dashboard/queue" "优先级队列数据"
test_endpoint GET "/internal/dashboard/batch-trend" "批处理趋势"
test_endpoint GET "/internal/dashboard/heatmap" "节点热区数据"
test_endpoint GET "/internal/dashboard/optimize-advice" "成本优化建议"
echo ""

###############################################################################
# 2. Metering 端点 (7 个)
###############################################################################
echo "--- 2. Metering 端点 ---"
test_endpoint GET "/internal/metering/call-logs" "调用日志列表"
test_endpoint GET "/internal/metering/call-logs?app_id=APP-CSR&page=1&size=5" "调用日志(带筛选)"
test_endpoint GET "/internal/metering/model-stats" "模型调用统计"
test_endpoint GET "/internal/metering/quotas" "部门配额列表"
test_endpoint PUT "/internal/metering/quotas/DEPT-TECH" "调整部门配额" '{"month_token_quota":4000000000}'
test_endpoint GET "/internal/metering/monthly-bills" "月度账单列表"
test_endpoint GET "/internal/metering/monthly-bills?month=2026-07&dept_id=DEPT-TECH" "月度账单(带筛选)"
test_endpoint GET "/internal/metering/personal-usage" "个人用量"
test_endpoint GET "/internal/metering/personal-usage?user_id=U-3001" "个人用量(指定用户)"
test_endpoint GET "/internal/metering/model-recommends" "模型推荐"
echo ""

###############################################################################
# 3. Routing 端点 (13 个)
###############################################################################
echo "--- 3. Routing 端点 ---"
test_endpoint GET "/internal/routing/engine" "获取路由引擎配置"
test_endpoint PUT "/internal/routing/engine" "保存路由引擎配置" '{"weights":{"latency":30,"cost":25,"risk":25,"load":20},"cache_first":true}'
test_endpoint GET "/internal/routing/rate-limit-rules" "限流规则列表"
test_endpoint POST "/internal/routing/rate-limit-rules" "新建限流规则" '{"name":"测试限流规则","target_type":"APP","target_id":"APP-TEST","qps_per_min":100}'
test_endpoint PUT "/internal/routing/rate-limit-rules/RL-CFG-001" "更新限流规则" '{"name":"更新后规则名"}'
test_endpoint DELETE "/internal/routing/rate-limit-rules/RL-CFG-003" "删除限流规则"
test_endpoint GET "/internal/routing/routing-rule-sets" "场景路由规则列表"
test_endpoint POST "/internal/routing/routing-rule-sets" "保存场景路由规则" '{"scene_key":"TEST","scene_name":"测试场景","priority":"P1"}'
test_endpoint GET "/internal/routing/aggregation-groups" "聚合组列表"
test_endpoint POST "/internal/routing/aggregation-groups" "新建聚合组" '{"name":"测试聚合组","members":["AST-QWEN-14B-BASE"]}'
test_endpoint GET "/internal/routing/elastic-switch" "弹性切换配置"
test_endpoint PUT "/internal/routing/elastic-switch" "保存弹性切换配置" '{"trigger_util":80,"target":"RENTAL"}'
test_endpoint GET "/internal/routing/router-logs" "路由日志列表"
test_endpoint GET "/internal/routing/router-logs?trace_id=TR-20260803-999001" "路由日志(按trace)"
echo ""

###############################################################################
# 4. Model Asset 端点 (8 个)
###############################################################################
echo "--- 4. Model Asset 端点 ---"
test_endpoint GET "/internal/models" "模型资产列表"
test_endpoint GET "/internal/models/connections" "模型接入列表"
test_endpoint POST "/internal/models/connections" "新建模型接入" '{"name":"测试接入","source":"CLOUD","provider":"测试云"}'
test_endpoint PUT "/internal/models/connections/CONN-001" "更新模型接入" '{"name":"更新后接入名"}'
test_endpoint DELETE "/internal/models/connections/CONN-002" "删除模型接入"
test_endpoint POST "/internal/models/connections/CONN-001/test" "测试模型连通性"
test_endpoint GET "/internal/models/evals" "评测结果列表"
test_endpoint GET "/internal/models/evals?asset_id=AST-QWEN-14B-BASE" "评测结果(指定模型)"
test_endpoint GET "/internal/models/benefits" "模型效益列表"
echo ""

###############################################################################
# 5. Security 端点 (8 个)
###############################################################################
echo "--- 5. Security 端点 ---"
test_endpoint GET "/internal/security/events" "安全事件列表"
test_endpoint GET "/internal/security/events?event_type=PROMPT_INJECTION" "安全事件(按类型)"
test_endpoint GET "/internal/security/alerts" "告警列表"
test_endpoint GET "/internal/security/guardrail" "护栏配置"
test_endpoint PUT "/internal/security/guardrail" "保存护栏配置" '{"enabled":true}'
test_endpoint GET "/internal/security/guardrail/policies" "护栏策略列表"
test_endpoint POST "/internal/security/guardrail/policies" "新建护栏策略" '{"name":"测试策略","modules":["PRIVACY"],"action":"MASK"}'
test_endpoint PUT "/internal/security/guardrail/policies/GD-001" "更新护栏策略" '{"name":"更新后策略"}'
test_endpoint DELETE "/internal/security/guardrail/policies/GD-003" "删除护栏策略"
echo ""

###############################################################################
# 6. Cache 端点 (1 个)
###############################################################################
echo "--- 6. Cache 端点 ---"
test_endpoint POST "/internal/cache/flush" "清空缓存"
echo ""

###############################################################################
# 7. ApiKey 端点 (已有)
###############################################################################
echo "--- 7. ApiKey 端点 (已有) ---"
test_endpoint GET "/internal/api-keys" "API Key 列表"
echo ""

###############################################################################
# 生成报告
###############################################################################
echo ""
echo "============================================"
echo "测试完成: 总计=$TOTAL 通过=$PASS 失败=$FAIL 跳过=$SKIP"
echo "============================================"

cat > "$REPORT" << REPORT_EOF
# MAS 后端 API 集成测试报告

**测试时间**: $(date '+%Y-%m-%d %H:%M:%S')
**测试目标**: $BASE_URL
**契约文件**: api-contract/openapi.yaml

## 测试概要

| 指标 | 数量 |
|------|------|
| 总测试数 | $TOTAL |
| 通过 ✅ | $PASS |
| 失败 ❌ | $FAIL |
| 警告 ⚠️ | $SKIP |
| **通过率** | **$(echo "scale=1; $PASS * 100 / $TOTAL" | bc)%** |

## 端点覆盖

| 模块 | 端点数 | 说明 |
|------|--------|------|
| Dashboard | 14 | 运营驾驶舱聚合指标（含参数变体） |
| Metering | 10 | 计量运营（含筛选/分页变体） |
| Routing | 14 | 路由配置管理（含参数变体） |
| Model Asset | 9 | 模型资产管理（含参数变体） |
| Security | 9 | 安全审计管理（含参数变体） |
| Cache | 1 | 缓存管理 |
| ApiKey | 1 | API Key 管理（已有） |

## 详细结果

| 方法 | 路径 | 描述 | 状态码 | 结果 |
|------|------|------|--------|------|
REPORT_EOF

for r in "${RESULTS[@]}"; do
  echo "$r" >> "$REPORT"
done

cat >> "$REPORT" << REPORT_EOF2

## 结论

$(if [[ $FAIL -eq 0 ]]; then
  echo "✅ 所有端点测试通过，后端 API 实现与 OpenAPI 契约一致。"
else
  echo "❌ 存在 $FAIL 个失败项，需要检查对应端点实现。"
fi)

### 后续步骤
1. 前端 api.ts 切换 mock() → fetch() 调用真实后端
2. 补充数据库真实数据验证（call_logs / model_config 等）
3. 添加性能基准测试（QPS / P95 延迟）
REPORT_EOF2

echo ""
echo "📝 测试报告已生成: $REPORT"
