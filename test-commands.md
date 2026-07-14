# SCB LC Invoice Checker — 测试命令清单

> 在**项目根目录**执行以下命令。前置：服务已启动（`./start-services.sh`，app 在 :8080，OCR 在 :8866）。
> live 命令走真实 DeepSeek LLM，差异字段的 `presented_value` 等字符串每次可能略有不同；要逐字段精确比对请用 `mvn test`（Mock 掉 LLM+OCR）。

## 前置

```bash
export SPRING_AI_API_KEY=<你的key>
./start-services.sh
```

## 自动化套件（确定性，不依赖服务）

```bash
mvn test
```

---

## 一、合规场景（期望 compliant=true，discrepancies=[]）

### 1. compliant-digital — 主基线 LC + 合规数字发票（原生文本层，不走 OCR）

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-compliant-digital.pdf;type=application/pdf' | jq .
```

### 2. compliant-scanned — 主基线 LC + 合规扫描发票（需 OCR sidecar）

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-compliant-scanned.pdf;type=application/pdf' | jq .
```

### 3. import-reference-compliant — 进口参考基线 LC + 含 LC 号的合规发票

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/LC-DEMO-2026-0002-import-reference-fixed.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-import-reference-compliant.pdf;type=application/pdf' | jq .
```

### 4. compliant-mt700-valid — 第二种合法 MT700 形状 + 合规发票（解析兼容性）

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/MT700_Valid.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-compliant-mt700-valid.pdf;type=application/pdf' | jq .
```

---

## 二、差异场景（期望 compliant=false）

### 5. amount-exceeds — 发票金额 63000 > 容差上限 60375

差异字段：`totalAmount` · UCP 600 Art. 18(b)

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-amount-exceeds.pdf;type=application/pdf' | jq .
```

### 6. goods-model-mismatch — REFINED SUGAR → BROWN SUGAR

差异字段：`goodsDescription` · UCP 600 Art. 18(c)

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-goods-model-mismatch.pdf;type=application/pdf' | jq .
```

### 7. goods-quantity-mismatch — 100 MT → 80 MT（总额保持不变）

差异字段：`goodsDescription` + `quantity` · UCP 600 Art. 18(c) + 30(b)

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-goods-quantity-mismatch.pdf;type=application/pdf' | jq .
```

### 8. seller-mismatch — 受益人 XYZ EXPORT CO., LTD. → ACME SUGAR TRADING LTD

差异字段：`issuerName` · UCP 600 Art. 18(a)(i)

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-seller-mismatch.pdf;type=application/pdf' | jq .
```

### 9. buyer-mismatch — 申请人 ABC IMPORTERS PTE LTD → NORDIC TRADING ASIA PTE LTD

差异字段：`applicantName` · UCP 600 Art. 18(a)(ii)

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-buyer-mismatch.pdf;type=application/pdf' | jq .
```

### 10. loading-location-mismatch — 装货港 PORT OF SINGAPORE → Port of Shanghai

差异字段：`portOfLoading` · UCP 600 Art. 14(d)

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-loading-location-mismatch.pdf;type=application/pdf' | jq .
```

### 11. destination-mismatch — 卸货港 PORT OF HAMBURG → Port of Rotterdam

差异字段：`portOfDischarge` · UCP 600 Art. 14(d)

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-destination-mismatch.pdf;type=application/pdf' | jq .
```

### 12. currency-mismatch — USD → EUR（金额数字不变）

差异字段：`currency` · UCP 600 Art. 18(a)(iii)

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-currency-mismatch.pdf;type=application/pdf' | jq .
```

### 13. import-reference-missing — LC :46A: 要求注 LC 号，但发票缺失

差异字段：`lcReferenceNumber`（presented_value=null）· ISBP 821 Preliminary Consideration (viii) / LC :46A

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/LC-DEMO-2026-0002-import-reference-fixed.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-import-reference-missing.pdf;type=application/pdf' | jq .
```

---

## 三、错误场景（期望 HTTP 422，body `{"error":"UNPROCESSABLE_ENTITY",...}`）

### 14. invalid-mt700-missing-32b — MT700 缺必填字段 :32B:（纯解析错误，不触发 LLM）

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/MT700_Invalid_Missing32B.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-compliant-digital.pdf;type=application/pdf' | jq .
```

### 15. unreadable-pdf — 损坏 PDF 无法渲染/抽取（PDF 错误，不触发 LLM）

```bash
curl -s -X POST 'http://localhost:8080/checks' \
  -F 'lc=<docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700' \
  -F 'invoice=@docs/test_fixtures/invoices/invoice-unreadable.pdf;type=application/pdf' | jq .
```

---

## 四、仅单测覆盖（live 无法复现）

### 16. ocr-insufficient — OCR 返回内容不足

仅由单测覆盖（Mock OCR 返回内容低于 100 字符阈值）。live 下真实 OCR sidecar 会正常识别该扫描发票 → 返回 compliant，**不会** 422，故无 live 命令。定向跑该单测：

```bash
mvn -Dtest='InvoiceCheckControllerIntegrationTest#ocrInsufficientReturns422' test
```

---

## 附：查看某次检查的 runId 与中间产物

成功响应会带响应头 `X-Check-Run-Id`。拿到 runId 后可查最终报告与各阶段产物：

```bash
# 最终报告
curl -s 'http://localhost:8080/checks/<runId>' | jq .

# 中间产物（stage 取值：lc_parsed / invoice_extracted / pdf_text / check_results / final_report）
curl -s 'http://localhost:8080/checks/<runId>/artifacts/lc_parsed' | jq .
```
