# ADR-0014:HR 後端 Feature Toggle(mock / h2 / external)

**狀態**:Accepted
**日期**:2026-05-04
**相關**:ADR-0007(User-HR Integration)

## Context

ADR-0007 定義了 `HrClient` 介面與 mock 模式,以便 dev 與 test 環境不需依賴真實的 HR 服務。隨著專案推進,出現新需求:

1. **demo / 評估場景**:讓非開發人員(產品 PM、客戶試用)看到較完整的員工資料(20+ 筆,涵蓋 9 個角色與離職案例),而不是 `MockHrClient` 內寫死的 5 筆。
2. **可編輯的測試資料**:測試人員需要新增 / 修改員工資料時,不必改 Kotlin 程式碼或重新打包,只要編輯 CSV 重啟即可。
3. **生產整合預留**:外部 HR REST API client 之後會出現,選擇機制要能擴。

僅靠單一 `MockHrClient`(編譯時硬編資料)無法滿足上述需求。

## Decision

新增 **runtime feature toggle**,以 `hr.mode` 屬性控制 `HrClient` 的具體實作:

| `hr.mode` | 實作 | 來源 | 預期使用情境 |
|---|---|---|---|
| `mock`(預設) | `MockHrClient` | Kotlin 內 5 筆 hard-coded 資料 | 單元測試、最快啟動的 dev |
| `h2` | `H2HrClient` | `test_data/hr_employees.csv`(可編輯)→ 啟動時載入 H2 in-memory DB | demo / 客戶評估、整合測試、需要較多種角色資料的 dev |
| `external` | (待實作) | 真實 HR REST API | production |

### 實作機制

- 兩個實作都帶 `@io.quarkus.arc.lookup.LookupIfProperty`,以 `hr.mode` 值決定哪個 bean 在 CDI 查找時可被解析。
- `MockHrClient` 額外加 `lookupIfMissing = true`,讓未設 `hr.mode` 也走 mock(向下相容)。
- `H2HrClient` 用 named datasource(`@DataSource("hr")`)獨立於 MongoDB,不與業務資料庫共用連線。
- `H2HrInitializer` 透過 `@Observes StartupEvent` 在啟動時建表 + 從 CSV 載入。
- CSV 載入策略:**先檔案系統(可編輯)**,缺檔則 fallback 到 classpath(jar 內)— 這讓 dev 與 production-style packaging 都能 work。

### CSV 格式

`test_data/hr_employees.csv` 是專案根目錄的純文字檔(UTF-8、無 BOM):

```
account_name,employee_no,display_name,email,active,default_roles
admin.system,EMP-00001,System Admin,admin@factory.example.com,true,ADMIN
leader.chen,EMP-00007,陳組長,leader.chen@factory.example.com,true,SHIFT_LEAD;GROUP_MANAGER
former.huang,EMP-00099,黃離職員工,,false,OPERATOR
```

- 多角色欄位用 `;` 分隔(避免與 CSV 主分隔符 `,` 衝突)
- 離職員工 `active=false`、email 可空
- 同一份 CSV 也鏡射一份到 `backend/src/main/resources/test_data/`,用作 jar 內建 fallback

## Consequences

### 正面

- 切換成本極低:`HR_MODE=h2` 環境變數 + 重啟即可。
- CSV 編輯流程自然,無 Kotlin 知識的 QA / PM 也能加員工。
- `MockHrResource` 已改注入 `HrClient` 介面,`/mock-hr/users/{accountName}` 對 mock 與 h2 模式行為一致。
- 多了一個整合測試 backbone(可載入更接近真實的多角色資料)。

### 負面 / 取捨

- 多一個依賴(`quarkus-jdbc-h2` + `quarkus-agroal`),約增 5 MB 包大小。
- 兩份 CSV(根目錄 + 內嵌 classpath)需要保持一致 — 透過 commit 時手動或 CI 步驟同步。
- H2 in-memory 資料**重啟即清空**,所以變更只能改 CSV。若未來需要持久化的 HR 資料庫,改用 `jdbc:h2:file:...` 即可,介面不變。
- `external` 模式尚未實作;若 prod 不小心配置 `HR_MODE=external` 會在啟動時 CDI 找不到 bean 失敗(預期行為,顯眼錯誤)。

## Alternatives Considered

### A. 在 MockHrClient 內讀 CSV(不引入 H2)

省一個依賴,但失去 SQL 查詢能力(未來若 HR 資料量擴張到 10 萬筆,直接用 in-memory map 不適合);也喪失「整合測試使用真實 SQL」的價值。

### B. 共用業務 MongoDB 存 HR

破壞 ADR-0007 的「HR 是外部來源系統,本地僅 projection」邊界;且測試啟動成本高(需 MongoDB)。

### C. 直接寫 file-based H2(`jdbc:h2:file:...`)

可持久化,但 dev 場景每次想 reset 還要刪檔。in-memory 配上「每次啟動載入 CSV」反而更好理解。

### D. Build-time profile 切換(`@IfBuildProfile("dev")`)

只能編譯期決定;無法不重 build 而切換。Runtime `@LookupIfProperty` 才能滿足「demo 環境臨時切換」需求。

## 後續

- 補一個 `ExternalHrClient`(真實 REST,搭配 fallback / circuit breaker / cache)— 對應 ADR-0007 的 prod 路徑。
- 觀察 H2 模式下啟動時間影響(目前 < 100 ms,可接受)。
- 若需要 HR 資料的搜尋 / 模糊比對(目前只有 exact `account_name` lookup),H2 模式下加索引或全文搜尋。
