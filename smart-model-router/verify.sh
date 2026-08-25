#!/usr/bin/env bash
# 一键验证入口（设计方案附录 F / §10.3.1）：已数据驱动化。
# 用例定义在 tests/cases.json（100 条黑盒用例），执行器为 tests/run_cases.py，
# 报告（含每条用例的路由明细）输出至 reports/test-report.md。
cd "$(dirname "$0")"
exec python3 tests/run_cases.py "$@"
