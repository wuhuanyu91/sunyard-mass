#!/usr/bin/env bash
# 一键验证入口（设计方案附录 F / §10.3.1）：已数据驱动化。
# 用例定义在 tests/cases.json（900 条黑盒用例），执行器为 tests/run_cases.py，
# 报告（含每条用例的路由明细）输出至 reports/test-report.md。
#
# 用法：
#   bash verify.sh                              # 使用默认参数
#   bash verify.sh --api-key mas-xxx            # 指定 API Key
#   MAS_API_KEY=mas-xxx bash verify.sh          # 通过环境变量指定
#
cd "$(dirname "$0")"

# 如果未通过命令行参数指定 API Key，且环境变量也未设置，则尝试从数据库自动获取
if [[ "$*" != *"--api-key"* ]] && [[ -z "$MAS_API_KEY" ]]; then
    # 尝试从数据库获取一个有效的 API Key
    API_KEY=$(psql postgresql://mas:mas123@localhost:5432/mas -t -A -c \
        "SELECT 'mas-test' WHERE EXISTS (SELECT 1 FROM mas_api_key WHERE key_prefix='mas-test' AND status=1) LIMIT 1;" 2>/dev/null | tr -d '[:space:]')
    if [[ -n "$API_KEY" ]]; then
        echo "自动检测到测试 API Key 前缀：$API_KEY"
        # 如果数据库中有测试 key，使用已知的测试 key 值
        # 注意：这里使用硬编码的测试 key，实际使用时应通过 --api-key 或环境变量传入完整 key
        echo "提示：请使用 --api-key 参数或 MAS_API_KEY 环境变量传入完整的 API Key"
    fi
fi

exec python3 tests/run_cases.py "$@"
