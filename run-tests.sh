#!/usr/bin/env bash
#
# SCB LC Invoice Checker — 自测命令集 (self-test command set)
# =============================================================================
#
# 服务启动后，用这个脚本对全部测试用例场景做自测。每个场景既可以直接复制
# 脚本里打印出来的 curl 命令单独跑，也可以用子命令一键跑全部并汇总 PASS/FAIL。
#
# 两种测试模式 (two modes)：
#   1) mvn   —— 确定性自动化套件 (Mock 掉 LLM + OCR，不需要真实服务)。
#               这是 pass/fail 的“事实来源”，期望值逐字段精确比对。
#   2) live  —— 对已启动的服务发真实 curl (走真实 DeepSeek LLM + 真实 OCR)。
#               用 start-services.sh 拉起服务后再跑。
#
# live 模式的诚实说明 (重要)：
#   - live 路径由真实 LLM 抽取发票字段，discrepancy 的 presented_value / lc_value
#     等字符串每次可能略有差异；本脚本只对“稳定信号”做硬断言：
#       * HTTP 状态码 (200 / 422)
#       * compliant 布尔值
#       * 422 时的 error == UNPROCESSABLE_ENTITY
#   - 差异字段 (discrepancy.field) 若与期望不符，只打黄色 WARNING，不计入 FAIL
#     (这通常是 LLM 抽取漂移，重跑或看 mvn 结果即可)。
#   - 要逐字段精确比对，请用 `./run-tests.sh mvn`。
#
# ocr-insufficient 场景说明：
#   - 该场景要求 OCR 返回内容不足，只有单测层 (Mock OCR) 能复现，真实 OCR sidecar
#     会把 scanned 发票正常识别 → live 下会返回 compliant，而不是 422。
#   - 因此本脚本不把它列为 live 场景；它由 `mvn test` 覆盖。
#
# 前置条件 (prerequisites)：
#   - JDK 21 + Maven 3.9 (mvn 模式)
#   - live 模式：先 `export SPRING_AI_API_KEY=<DeepSeek key>` 再 `./start-services.sh`
#     (app on :8080, OCR sidecar on :8866)。scanned 场景必须有 OCR sidecar。
#
# 用法 (usage)：
#   ./run-tests.sh                # 默认 = live，跑全部 live 场景
#   ./run-tests.sh check          # 只检查 app + OCR 是否在线
#   ./run-tests.sh mvn            # 跑确定性自动化套件 (mvn test)
#   ./run-tests.sh list           # 列出全部 live 场景 id
#   ./run-tests.sh <id>           # 只跑一个场景，例：./run-tests.sh amount-exceeds
#   ./run-tests.sh artifacts      # 跑 compliant-digital 并展示 runId/中间产物拉取
#   BASE_URL=http://host:port ./run-tests.sh   # 指定服务地址
# =============================================================================
set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8080}"
OCR_URL="${OCR_URL:-http://localhost:8866}"
FIX="docs/test_fixtures"
TMP_RESP="$(mktemp -t scb_resp.XXXXXX).json"
trap 'rm -f "$TMP_RESP"' EXIT

# ---- colors ----
if [ -t 1 ]; then
  C_GREEN=$'\033[32m'; C_RED=$'\033[31m'; C_YELLOW=$'\033[33m'
  C_CYAN=$'\033[36m';  C_DIM=$'\033[2m';  C_BOLD=$'\033[1m'; C_RESET=$'\033[0m'
else
  C_GREEN=''; C_RED=''; C_YELLOW=''; C_CYAN=''; C_DIM=''; C_BOLD=''; C_RESET=''
fi

PASS=0; WARN=0; FAIL=0

# ---- scenario table ----
# id | lc file (under $FIX/lc) | invoice pdf (under $FIX/invoices) | exp_http | exp_compliant | exp_fields(comma)
SCENARIOS=(
  "compliant-digital|SWIFT_MT700_Sample_Compliant.mt700|invoice-compliant-digital.pdf|200|true|"
  "compliant-scanned|SWIFT_MT700_Sample_Compliant.mt700|invoice-compliant-scanned.pdf|200|true|"
  "amount-exceeds|SWIFT_MT700_Sample_Compliant.mt700|invoice-amount-exceeds.pdf|200|false|invoice_amount"
  "goods-model-mismatch|SWIFT_MT700_Sample_Compliant.mt700|invoice-goods-model-mismatch.pdf|200|~false|goods_description"
  "goods-quantity-mismatch|SWIFT_MT700_Sample_Compliant.mt700|invoice-goods-quantity-mismatch.pdf|200|false|goods_description,quantity"
  "seller-mismatch|SWIFT_MT700_Sample_Compliant.mt700|invoice-seller-mismatch.pdf|200|false|issuer_name"
  "buyer-mismatch|SWIFT_MT700_Sample_Compliant.mt700|invoice-buyer-mismatch.pdf|200|false|applicant_name"
  "loading-location-mismatch|SWIFT_MT700_Sample_Compliant.mt700|invoice-loading-location-mismatch.pdf|200|false|port_of_loading"
  "destination-mismatch|SWIFT_MT700_Sample_Compliant.mt700|invoice-destination-mismatch.pdf|200|false|port_of_discharge"
  "currency-mismatch|SWIFT_MT700_Sample_Compliant.mt700|invoice-currency-mismatch.pdf|200|false|currency"
  "import-reference-missing|LC-DEMO-2026-0002-import-reference-fixed.mt700|invoice-import-reference-missing.pdf|200|false|lc_reference_number"
  "import-reference-compliant|LC-DEMO-2026-0002-import-reference-fixed.mt700|invoice-import-reference-compliant.pdf|200|true|"
  "compliant-mt700-valid|MT700_Valid.mt700|invoice-compliant-mt700-valid.pdf|200|true|"
  "invalid-mt700-missing-32b|MT700_Invalid_Missing32B.mt700|invoice-compliant-digital.pdf|422||"
  "unreadable-pdf|SWIFT_MT700_Sample_Compliant.mt700|invoice-unreadable.pdf|422||"
)

# ---- helpers ----
print_summary() {
  echo ""
  echo "${C_BOLD}================ SUMMARY ================"
  echo "  PASS: ${C_GREEN}$PASS${C_RESET}${C_BOLD}   WARN: ${C_YELLOW}$WARN${C_RESET}${C_BOLD}   FAIL: ${C_RED}$FAIL${C_RESET}${C_BOLD}"
  echo "=========================================${C_RESET}"
  [ "$FAIL" -eq 0 ] && echo "${C_GREEN}All hard-asserted checks passed.${C_RESET}" \
                     || echo "${C_RED}Some checks failed — see above.${C_RESET}"
}

check_services() {
  local ok=1 c
  echo "${C_BOLD}Service health check${C_RESET}"
  echo "App  ($BASE_URL):"
  c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE_URL/checks" 2>/dev/null || echo 000)
  if [ "$c" = "000" ]; then
    echo "  ${C_RED}DOWN (connection refused). 先执行 ./start-services.sh${C_RESET}"; ok=0
  else
    echo "  ${C_GREEN}UP (HTTP $c)${C_RESET}"
  fi
  echo "OCR  ($OCR_URL):"
  c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$OCR_URL/health" 2>/dev/null || echo 000)
  if [ "$c" = "000" ]; then
    echo "  ${C_YELLOW}DOWN — scanned-PDF 场景 (compliant-scanned) 会失败/降级${C_RESET}"
  else
    echo "  ${C_GREEN}UP (HTTP $c)${C_RESET}"
  fi
  return $ok
}

# run_case <id> <lc> <invoice> <exp_http> <exp_compliant> <exp_fields>
run_case() {
  local id="$1" lc="$2" inv="$3" exp_http="$4" exp_comp="$5" exp_fields="$6"
  local lc_path="$FIX/lc/$lc" inv_path="$FIX/invoices/$inv"

  echo "${C_CYAN}${C_BOLD}──────── $id ────────${C_RESET}"
  if [ ! -f "$lc_path" ];  then echo "${C_RED}[FAIL] missing LC: $lc_path${C_RESET}";  FAIL=$((FAIL+1)); return; fi
  if [ ! -f "$inv_path" ]; then echo "${C_RED}[FAIL] missing invoice: $inv_path${C_RESET}"; FAIL=$((FAIL+1)); return; fi

  # 打印可直接复制粘贴的 curl 命令
  echo "${C_DIM}curl -s -X POST '$BASE_URL/checks' \\${C_RESET}"
  echo "${C_DIM}  -F 'lc=<$lc_path' \\${C_RESET}"
  echo "${C_DIM}  -F 'invoice=@$inv_path;type=application/pdf'${C_RESET}"

  local hdr_tmp; hdr_tmp="$(mktemp -t scb_hdr.XXXXXX)"
  local code
  code=$(curl -s -o "$TMP_RESP" -D "$hdr_tmp" -w '%{http_code}' --max-time 120 \
        -X POST "$BASE_URL/checks" \
        -F "lc=<$lc_path" \
        -F "invoice=@$inv_path;type=application/pdf" 2>/dev/null || echo 000)
  local run_id; run_id=$(grep -i '^X-Check-Run-Id:' "$hdr_tmp" | tr -d '\r' | awk '{print $2}')
  rm -f "$hdr_tmp"

  # 响应体美化输出
  if [ "$code" = "000" ]; then
    echo "${C_RED}请求失败 (连接被拒绝/超时)。服务是否已启动？${C_RESET}"
    FAIL=$((FAIL+1)); echo ""; return
  fi
  if jq -e . "$TMP_RESP" >/dev/null 2>&1; then jq . "$TMP_RESP"; else cat "$TMP_RESP"; fi
  echo "${C_DIM}HTTP=$code  runId=${run_id:-N/A}${C_RESET}"

  # ---- 判定 ----
  local verdict="PASS" vcolor="$C_GREEN"
  if [ "$code" != "$exp_http" ]; then
    verdict="FAIL"; vcolor="$C_RED"
    echo "${C_DIM}  expect HTTP $exp_http, got $code${C_RESET}"
  elif [ "$exp_http" = "200" ]; then
    local comp; comp=$(jq -r 'if has("compliant") then (.compliant | tostring) else "N/A" end' "$TMP_RESP")
    # exp_comp 以 ~ 前缀 = 软断言：该用例的 compliant 依赖 LLM 驱动的 check（如货描），
    # 真实 LLM 可能漂移，不符只 WARN 不 FAIL（与脚本 LLM-漂移哲学一致）。
    local soft_comp=0 want_comp="$exp_comp"
    if [[ "$exp_comp" == "~"* ]]; then soft_comp=1; want_comp="${exp_comp:1}"; fi
    if [ "$comp" != "$want_comp" ]; then
      if [ "$soft_comp" = "1" ]; then
        if [ "$verdict" = "PASS" ]; then verdict="WARN"; vcolor="$C_YELLOW"; fi
        echo "${C_DIM}  WARN: compliant drift (expect $want_comp, got=$comp; LLM-driven check)${C_RESET}"
      else
        verdict="FAIL"; vcolor="$C_RED"
        echo "${C_DIM}  expect compliant=$want_comp, got=$comp${C_RESET}"
      fi
    elif [ "$want_comp" = "false" ] && [ -n "$exp_fields" ]; then
      # 差异字段：缺失算 WARN (LLM 抽取漂移)，不算硬 FAIL
      local got_csv; got_csv=",$(jq -r '.discrepancies[].field' "$TMP_RESP" | paste -sd, -),"
      local -a farr; IFS=',' read -ra farr <<< "$exp_fields"
      local f
      for f in "${farr[@]}"; do
        if ! echo "$got_csv" | grep -q ",$f,"; then
          if [ "$verdict" = "PASS" ]; then verdict="WARN"; vcolor="$C_YELLOW"; fi
          echo "${C_DIM}  WARN: expected discrepancy field '$f' not in response (got: ${got_csv#,})${C_RESET}"
        fi
      done
    fi
  elif [ "$exp_http" = "422" ]; then
    local err; err=$(jq -r '.error // "N/A"' "$TMP_RESP")
    if [ "$err" != "UNPROCESSABLE_ENTITY" ]; then
      verdict="FAIL"; vcolor="$C_RED"
      echo "${C_DIM}  expect error=UNPROCESSABLE_ENTITY, got=$err${C_RESET}"
    fi
  fi

  case "$verdict" in
    PASS) PASS=$((PASS+1));;
    WARN) WARN=$((WARN+1));;
    FAIL) FAIL=$((FAIL+1));;
  esac
  echo "${vcolor}${C_BOLD}[$verdict] $id${C_RESET}"
  echo ""
}

run_all_live() {
  check_services || true
  echo ""
  echo "${C_BOLD}Running ${#SCENARIOS[@]} live scenarios against $BASE_URL ...${C_RESET}"
  echo ""
  local line id lc inv eh ec ef
  for line in "${SCENARIOS[@]}"; do
    IFS='|' read -r id lc inv eh ec ef <<< "$line"
    run_case "$id" "$lc" "$inv" "$eh" "$ec" "$ef"
  done
  print_summary
}

run_one() {
  local want="$1" line id lc inv eh ec ef found=0
  for line in "${SCENARIOS[@]}"; do
    IFS='|' read -r id lc inv eh ec ef <<< "$line"
    if [ "$id" = "$want" ]; then found=1; run_case "$id" "$lc" "$inv" "$eh" "$ec" "$ef"; break; fi
  done
  if [ "$found" -eq 0 ]; then
    echo "${C_RED}未知场景 id: $want${C_RESET}"
    echo "可用 id: ./run-tests.sh list"
    exit 1
  fi
  print_summary
}

list_scenarios() {
  echo "Live 场景 (id -> LC + invoice, 期望):"
  local line id lc inv eh ec ef
  for line in "${SCENARIOS[@]}"; do
    IFS='|' read -r id lc inv eh ec ef <<< "$line"
    if [ "$eh" = "422" ]; then
      printf "  %-28s 422  %s + %s\n" "$id" "$lc" "$inv"
    else
      printf "  %-28s %s  %s + %s\n" "$id" "$([ "$ec" = "true" ] && echo 'OK ' || echo 'BAD')" "$lc" "$inv"
    fi
  done
  echo ""
  echo "另: ocr-insufficient 仅由 mvn test 覆盖 (单测层，live 不可复现)。"
}

run_mvn() {
  echo "${C_BOLD}确定性自动化套件: mvn test (LLM + OCR 均 Mock)${C_RESET}"
  echo "期望: 全绿。这是逐字段精确比对的 pass/fail 事实来源。"
  echo ""
  mvn test
}

# 跑一个场景并展示如何用 runId 拉取最终报告 + 中间产物
demo_artifacts() {
  check_services || true
  echo ""
  echo "${C_BOLD}Artifacts demo: compliant-digital -> 拉取 runId / 中间产物${C_RESET}"
  local lc_path="$FIX/lc/SWIFT_MT700_Sample_Compliant.mt700"
  local inv_path="$FIX/invoices/invoice-compliant-digital.pdf"
  local hdr_tmp; hdr_tmp="$(mktemp -t scb_hdr.XXXXXX)"
  curl -s -o "$TMP_RESP" -D "$hdr_tmp" -w '%{http_code}\n' --max-time 120 \
    -X POST "$BASE_URL/checks" -F "lc=<$lc_path" -F "invoice=@$inv_path;type=application/pdf"
  local run_id; run_id=$(grep -i '^X-Check-Run-Id:' "$hdr_tmp" | tr -d '\r' | awk '{print $2}')
  rm -f "$hdr_tmp"
  echo "runId = ${C_CYAN}${run_id:-N/A}${C_RESET}"
  echo ""
  echo "${C_DIM}# 最终报告:${C_RESET}"
  echo "${C_DIM}curl -s '$BASE_URL/checks/$run_id' | jq .${C_RESET}"
  curl -s "$BASE_URL/checks/$run_id" | jq . 2>/dev/null || curl -s "$BASE_URL/checks/$run_id"
  echo ""
  local stage
  for stage in lc_parsed invoice_extracted pdf_text check_results final_report; do
    echo "${C_DIM}# artifact: $stage${C_RESET}"
    echo "${C_DIM}curl -s '$BASE_URL/checks/$run_id/artifacts/$stage' | jq .${C_RESET}"
    curl -s "$BASE_URL/checks/$run_id/artifacts/$stage" | jq . 2>/dev/null \
      || curl -s "$BASE_URL/checks/$run_id/artifacts/$stage"
    echo ""
  done
}

usage() {
  sed -n '2,60p' "$0"
}

# ---- dispatch ----
case "${1:-live}" in
  live)  run_all_live ;;
  check) check_services ;;
  mvn)   run_mvn ;;
  list)  list_scenarios ;;
  artifacts) demo_artifacts ;;
  -h|--help|help) usage ;;
  *)     run_one "$1" ;;
esac
