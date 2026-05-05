# 測試資料(test_data)

開發與測試用的種子資料,**不進生產環境**。

---

## hr_employees.csv

`HR_MODE=h2` 時由 `H2HrInitializer` 在後端啟動時載入到 H2 in-memory database。

### 欄位

| 欄位 | 型別 | 必填 | 說明 |
|---|---|---|---|
| `account_name` | VARCHAR(30) | ✓ | 對 HR 服務的查詢鍵(系統 PK) |
| `employee_no` | VARCHAR(20) | ✓ | 工號 |
| `display_name` | VARCHAR(100) | ✓ | 顯示名稱(支援中文 / Unicode) |
| `email` | VARCHAR(255) | | 公司信箱(離職員工可空) |
| `active` | BOOLEAN | ✓ | 是否在職(false 等同已離職,系統下次同步會踢出 group) |
| `default_roles` | VARCHAR(500) | ✓ | 預設角色,**多角色用 `;` 分隔**(例:`SHIFT_LEAD;GROUP_MANAGER`) |

### 編輯規則

- 編碼 UTF-8(無 BOM)
- 第一行**必須**是 header(欄位名)
- `account_name` 在檔案內必須 unique
- 後端啟動時會 **truncate + reload** 整張表(冪等)
- 編輯後重啟後端即生效;不需重 build

### 角色清單(對應 spec §2)

`OPERATOR / SHIFT_LEAD / ENGINEER / QA / GROUP_ADMIN / GROUP_MANAGER / ORG_MANAGER / ORG_ADMIN / ADMIN`

---

## 切換 HR 後端

| 模式 | 說明 | 切換方式 |
|---|---|---|
| `mock` | 寫死在 `MockHrClient` 的 5 筆假資料(預設) | `HR_MODE=mock` |
| `h2` | 從本檔載入到 H2 in-memory DB,可任意編輯 | `HR_MODE=h2` |
| `external` | 真實 HR REST API(production) | `HR_MODE=external`(暫未實作 client) |

設定 in `.env`、`application-dev.properties`,或環境變數。

---

## 注意

- 此目錄**不應**包含真實員工資料(只能放 fake / synthetic data)
- Production 部署時可不打包此目錄(`.dockerignore`)
- 若要新增其他測試資料(範本、組織、初始任務),請以類似 `*.csv` / `*.json` 形式放進本目錄並寫對應 initializer
