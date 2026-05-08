# ADR-0015:JWT Cookie + CSRF Model(httpOnly cookie + double-submit token)

**狀態**:Accepted
**日期**:2026-05-08
**相關**:ADR-0005(Organization 多租戶 — stateless JWT 前提)、ADR-0007(User-HR Integration)、`docs/spec/requirements.md` §FR-1.5 ~ §FR-1.8(本 ADR 的 spec 投影)
**前情**:M4 code review 條目 **S-009**(P1 Security)點名「JWT 存於 localStorage,XSS 可讀,違反 OWASP cheat-sheet」;本 ADR 為 M5.3 / M5.5 hardening 的設計拍板。

## Context

M1 ~ M4 的 auth 設計為:

- Login 響應 body 回 `{ accessToken, refreshToken, expiresIn, tokenType }`(`TokenPair` schema)
- Frontend `client.ts` 將兩個 token **存於 `localStorage`**,每個 request 帶 `Authorization: Bearer <accessToken>` header
- Logout 將 refresh jti 加入 `revoked_tokens` 黑名單

M4 code review 點名兩個風險:

1. **XSS exfiltration**:任何能執行 JS 的 XSS(即便最小範圍)都能讀走 `localStorage.refreshToken`(7 天有效),冒用該帳號至 token 過期。
2. **CSRF 雖目前無**(因 Bearer header 不會被瀏覽器自動帶上),但若改 cookie 模式必須同時補 CSRF 防護,否則跨站表單可冒名 mutating。

修補的限制條件:

- **不能違背 ADR-0005 stateless JWT 前提**:不引入 server-side session 儲存(會破多租戶 horizontal scaling 假設)
- **過渡期須容忍既有 frontend / BDD test**:M5.3 backend 改完後,M5.5 frontend 才會切換;期間 BDD 與 client.ts 仍走 Bearer header 路徑,**backend 必須雙模並存**
- **dev 環境的痛點**:frontend `:5173` ↔ backend `:8080` 跨 port,SameSite=Strict cookie 無法帶過去;需要 dev / prod 行為差異

## Decision

採用「**httpOnly cookie 存 token + double-submit cookie pattern 防 CSRF**」三層模型,雙模兼容(過渡期):

> **Decision 一句話**:Refresh token 改 httpOnly + Secure + SameSite=Strict cookie(Path=`/v1/auth`);access token 雙模兼容(cookie 優先 + Bearer fallback,Path=`/v1`);CSRF 採 double-submit cookie pattern(`XSRF-TOKEN` cookie 配 `X-XSRF-TOKEN` header,server 逐字比對)。

### 模型摘要(細節參 §FR-1.5 ~ §FR-1.8)

| 維度 | 決策 |
|---|---|
| Refresh token 儲存 | httpOnly + Secure + SameSite=Strict cookie;Path=`/v1/auth`;Max-Age=7d;dev SameSite=Lax |
| Access token transport | 雙模:cookie(httpOnly,Path=`/v1`,15m)+ Bearer header;resolver 順序 cookie 優先,fallback header;同時帶不同值 → cookie 為準 |
| CSRF 防護 | Double-submit:`XSRF-TOKEN` cookie(non-httpOnly)+ `X-XSRF-TOKEN` header;server 逐字比對;不等 → 403 `CSRF_TOKEN_MISMATCH` |
| 豁免清單 | GET / HEAD / OPTIONS;`POST /v1/auth/login`;`/q/health` / `/v1/health` |
| Logout | 三 cookie 全部 Max-Age=0 清除 + revoke refresh jti |
| Refresh 行為 | 優先讀 cookie,fallback body;舊 refresh jti 立即黑名單,新 jti rotate;access + XSRF 同步重發 |

### 實作要點(M5.3 backend / M5.5 frontend 對接)

- **Backend**:`AuthResource` 在 login / refresh 響應加 `Set-Cookie` headers;新增 `CsrfFilter`(JAX-RS `ContainerRequestFilter`)逐字比對 cookie ↔ header;新增 `JwtCookieResolver` filter(在 SmallRye JWT 的 token extraction 前生效,讓 `quarkus.smallrye-jwt.locations` 同時覆蓋 cookie + header)。
- **Frontend**(M5.5):`client.ts` 移除 `localStorage` 存取,改靠瀏覽器 cookie jar 自動帶;mutating request 前讀 `XSRF-TOKEN` cookie 並填入 `X-XSRF-TOKEN` header;axios `withCredentials: true` 啟用。
- **BDD step def**(M5.5):從手動帶 Bearer header 改為依賴瀏覽器 cookie jar(Playwright `BrowserContext` 自動處理)。
- **OpenAPI**:加 `cookieAuth` securityScheme(`type: apiKey, in: cookie, name: access_token`);global `security` 改為 `[bearerAuth, cookieAuth]`(OpenAPI 多項為 OR);`components.parameters.CsrfHeader` 集中定義 + 全 mutating 端點 `$ref`。

## Alternatives Considered

### A. SameSite=Strict cookie + 不做 double-submit token

**思路**:現代瀏覽器(Chrome ≥ 80、Firefox、Safari)對 SameSite=Strict cookie 不會在跨站 request 中帶上,理論上已擋大部分 CSRF;省去 double-submit 複雜度。

**為何不選**:
- IE 11、舊版 Safari 不可靠(雖佔比小,但工廠現場 PDA / 平板可能裝舊瀏覽器)
- 部分 cross-origin scenario(同站不同子網域、特殊 redirect chain)仍有理論風險
- OWASP 2024 cheat-sheet 仍建議**深度防禦**:SameSite + double-submit 兩層
- 維護成本差異不大(double-submit filter 只 ~50 行 Kotlin)

### B. sessionStorage + Bearer header(維持原樣,只換儲存點)

**思路**:把 token 從 localStorage 搬到 sessionStorage(分頁關閉即失效),不改 cookie 模型。

**為何不選**:
- sessionStorage 仍是 JS 可讀,**沒解 S-009 XSS 根因**
- 工廠 PDA 一個 session 跨班可能達 12 小時,sessionStorage 期限限制反而干擾使用
- code-reviewer 在 review report 已明確要求 cookie 模式

### C. Server-side session(放棄 stateless JWT)

**思路**:用傳統 Java EE session 或 Redis session store;cookie 只帶 session id,token 在 server。

**為何不選**:
- **違背 ADR-0005** 多租戶 stateless 假設(horizontal scaling 需要 sticky session 或 shared store,提高運維成本)
- Multi-pod deployment 須引入 Redis,M5 階段不引入新基礎設施
- JWT claims(rootOrgId、orgPath、roles)目前直接用於授權判定,改 session 等於重寫整套 RBAC

### D. PASETO / OAuth 2.1 Authorization Code + PKCE

**思路**:採用更現代的 token 標準。

**為何不選**:
- 無 SSO/IdP 需求(MVP 內部系統,HR 自帶 user store);引入 OAuth 增加複雜度但無對應收益
- PASETO 良好但 Quarkus / SmallRye 生態 JWT 支援更成熟,降低運維風險

## Consequences

### 正面

- **XSS 無法讀 refresh / access token**(httpOnly),即便有 reflective XSS 也無法盜帳號(只能在受害者瀏覽器內當下發 request,影響面遠小於 token 失竊)
- **CSRF 阻斷**:Strict SameSite + double-submit 兩層深度防禦,符合 OWASP 2024 cheat-sheet
- **Frontend 改造小**:`client.ts` 加 `withCredentials: true` + 讀 cookie 填 header,不需處理 token expire / refresh logic(瀏覽器自動帶)
- **BDD 變直觀**:Playwright 自動管理 cookie jar,不再需要手動塞 header
- **過渡期穩**:雙模兼容,M5.3 backend 改完不影響既有 frontend / BDD 立即跑

### 負面 / 取捨

- **Dev 環境 SameSite 處理**:localhost 跨 port 必須 SameSite=Lax(prod=Strict);需在 `application.properties` 區分 profile 行為,新增 dev / prod 一個分歧點
- **Frontend 重寫**(M5.5):`client.ts` 大改;BDD step definitions 部分需配合(token-related steps 改用 cookie-aware helpers)
- **Cookie domain 跨環境**:prod 部署若採 sub-domain split(`api.factory-ops.example.com` vs `app.factory-ops.example.com`)需設 `Domain=.factory-ops.example.com`;**M5.3 暫不實作 cross-domain 支援**(同 origin 部署),M6 視部署形態再補
- **`X-XSRF-TOKEN` 在每個 mutating request 都要寫**:openapi.yaml 50+ 個端點需加 `$ref`;後續新增 mutating 端點若忘記,會被 CsrfFilter 擋;**M5.6 release 需在 PR template 加 reminder**
- **同時帶不同 token 的 ambiguity**:雖規格定義 cookie 為準,實作上 CsrfFilter / JwtCookieResolver 必須一致處理,避免 race / 不同 path 不同行為;backend test 需明確覆蓋此 case

### 風險

- **R1:雙模期內客戶端誤實作**(同時帶 Bearer header + cookie 但內容不同步) — 緩解:server 以 cookie 為準 + warn-level log;M6 全切 cookie 後此風險消失
- **R2:CSRF token rotation 與 access token 不同步** — 緩解:每次 login / refresh 同步 rotate;client 端 axios interceptor 在 401 後重 refresh 時自動重讀 cookie
- **R3:dev 環境 SameSite=Lax 在某些 redirect chain 下意外帶 cookie** — 緩解:dev 不部署到公網,且 dev profile 預設 `Secure=false` 也只允許本機;prod 切回 Strict
- **R4:既有資料無遷移問題**(token 為 stateless 短壽,無持久化資料) — 不需 migration

### 後續工作(出本期 scope,記錄於 backlog)

- **M5.3**(本期 backend builder 接續):落地 backend 7 個 endpoint 的 Set-Cookie + CsrfFilter
- **M5.5**(本期 frontend builder 接續):client.ts 重寫 + BDD step def 改造
- **M6 候選**:
  - 移除 `POST /v1/auth/refresh` 的 body 接收路徑(完成 cookie 全切換)
  - 移除 `POST /v1/auth/login` 響應 body 的 `accessToken` / `refreshToken` 欄位(目前保留為向前兼容)
  - `PUT /v1/auth/password` 觸發 user-scoped jti 黑名單(全裝置登出)
  - Cross-domain cookie 支援(若 deployment 拆 origin)

## 參考

- OWASP Cheat Sheet: [Cross-Site Request Forgery Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- OWASP Cheat Sheet: [JWT for Java](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)(localStorage 風險條目)
- RFC 6265bis: [Cookies: HTTP State Management Mechanism](https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis)(SameSite 語意)
- M4 code review:`docs/review/code-review-report.md` § S-009
- M5 plan:`docs/release/m5-plan.md` §3.3
