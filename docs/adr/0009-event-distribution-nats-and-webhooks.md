# ADR-0009: Domain Event 分發 — NATS + Webhook 雙通道

**狀態**: Accepted
**日期**: 2026-05-03
**決策者**: spec-architect
**相關需求**: §1、FR-Notification、Q-5(拍板)、Epic K(US-K1 ~ US-K3)

---

## Context(背景)

v1.1 規劃了「Domain Event」概念,但**未決定實際的散播機制**:
- v1.1 Q-5 列「逾期通知」為待確認(是否需要排程器主動推播?)
- v1.1 FR-8 列 Webhook 為「預留」
- v1.1 是 in-memory CDI events,跨 process 完全沒法散播

v1.1.1 Q-5 拍板:

> 「不需要定時掃描,請以 Web hook 與 NATS 方式提供 task 新建與變更通知」

這帶來明確要求:
1. **不做定時逾期掃描**(`overdue` 純衍生欄位,前端自算)
2. **NATS 是首選 message broker**(內部訂閱者用)
3. **Webhook 是首選的對外通知機制**(第三方訂閱用)
4. **Domain Event 必須跨 process 可達**(不能只是 in-memory)

需要決定:
1. **Topic 命名規則**
2. **at-least-once vs at-most-once**
3. **Webhook 重試策略 / 簽章 / 驗證**
4. **跨 process 一致性**(ActionRequest 寫入後 NATS 失敗怎辦?)
5. **冪等性**

---

## Decision(決策)

### 1. 雙通道並行:NATS(內部) + Webhook(對外)

每個 Domain Event 同時送到:
- **NATS JetStream**:topic `factory-ops.<context>.<event-kebab>`,內部訂閱者(Notification 服務、Analytics、未來模組)
- **Registered Webhooks**:HTTP POST 到 user 註冊的 `targetUrl`,by event filter

```
┌──────────────┐
│  Domain      │
│  Aggregate   │
│  Update      │
└───────┬──────┘
        │ emit DomainEvent
        ↓
┌──────────────────────────┐
│  EventPublisher          │
│  (transactional outbox)  │
└──────┬──────────┬────────┘
       │          │
       ↓          ↓
   NATS Pub    Webhook Worker
   (durable)  (HTTP retries)
```

### 2. Transactional Outbox 模式

**問題**:若 ActionRequest 寫入成功但 NATS 失敗(網路斷、broker 死),事件會遺失。

**解決**:
1. Aggregate update + outbox 寫入在**同一 MongoDB transaction**(若用 replica set;單機則用 $session)
2. 背景 `OutboxRelayWorker` 定期掃 outbox,推送到 NATS / Webhook,成功後標記 `dispatched`
3. 失敗會重試;outbox 是 source of truth

**Outbox Schema**:
```kotlin
data class OutboxEntry(
    val id: String,                  // = eventId
    val rootOrgId: OrgId?,           // 系統級事件可空
    val eventType: String,
    val occurredAt: Instant,
    val aggregateType: String,
    val aggregateId: String,
    val actorId: UserId?,
    val payload: Map<String, Any?>,
    val status: String,              // PENDING / DISPATCHED / FAILED
    val natsDispatchedAt: Instant?,
    val webhookAttempts: List<WebhookAttempt>,  // 每個 webhook 的重試紀錄
    val createdAt: Instant,
)
```

### 3. Topic 命名:`factory-ops.<context>.<event-kebab>`

| Context | 範例 |
|---|---|
| `task` | `factory-ops.task.created`、`factory-ops.task.assigned`、`factory-ops.task.owner-transferred`、`factory-ops.task.completed` |
| `project` | `factory-ops.project.created`、`factory-ops.project.status-changed` |
| `action-request` | `factory-ops.action-request.submitted`、`factory-ops.action-request.dispatched`、`factory-ops.action-request.relayed`、`factory-ops.action-request.triaged` |
| `org` | `factory-ops.org.created`、`factory-ops.org.leader-transferred` |
| `group` | `factory-ops.group.member-added`、`factory-ops.group.leader-transferred` |
| `user` | `factory-ops.user.hr-synced`、`factory-ops.user.deactivated` |
| `template` | `factory-ops.project-template.version-published`、`factory-ops.task-template.forked` |

**Subject hierarchy 規則**:
- 第一段固定 `factory-ops`(避免與其他系統撞)
- 第二段是 context(對應 aggregate 大類)
- 第三段 + 是 event 名稱(kebab-case)
- 訂閱可用 wildcard:`factory-ops.task.*`(所有 task 事件)、`factory-ops.>`(全部)

### 4. 統一 Payload Schema

```json
{
  "eventId": "01F8MECHZX3TBDSZ7XR8X1Z3M5",  // ULID
  "eventType": "task.assigned",
  "occurredAt": "2026-05-03T08:30:00.123Z",
  "rootOrgId": "660abc...",
  "orgPath": ["660abc...", "661xyz...", "662def...", "663ghi..."],
  "aggregateType": "Task",
  "aggregateId": "task-id-123",
  "actorId": "user-id-456",
  "payload": { /* event-specific */ }
}
```

- `eventId`:ULID(時間排序、collision-free,比 UUID 對訂閱端 dedup 友善)
- `orgPath`:從 root 到事件所屬 leaf 的 OrgId 鏈,訂閱端可做 ancestry filter

### 5. 送達保證:at-least-once

- **NATS JetStream**:`AckPolicy: All`,`MaxDeliver: 5`,死信進 stream `factory-ops.dead-letters`
- **Webhook**:指數退避重試 `1m / 5m / 30m / 2h / 6h`(共 5 次 attempt),最終失敗寫入 `webhook_dead_letters` collection
- **訂閱者責任**:以 `eventId` 去重(server 不保證單次送達,但保證最多重複發送 N 次)

### 6. Webhook 簽章:HMAC-SHA256

- 註冊時 server 生成 `secret`(32 bytes random,Base64)
- 發送時 header:
  ```
  X-Factory-Ops-Signature: sha256=<hex>
  X-Factory-Ops-Event-Id: <eventId>
  X-Factory-Ops-Event-Type: task.assigned
  X-Factory-Ops-Timestamp: <unix epoch>
  Content-Type: application/json
  ```
- HMAC: `HMAC-SHA256(secret, "{timestamp}.{body}")`,訂閱端驗證
- 防重放:`timestamp` 與 server now 差距 ≤ 5 分鐘

### 7. Webhook 註冊範圍

- Webhook 屬 root org(每 root org 各自註冊),由 `ORG_ADMIN` 維護
- 不允許訂閱跨 root org 事件(同 multi-tenancy 隔離)
- 系統級 webhook(訂閱所有 org 事件)留給 `ADMIN`(預留,MVP 不開放)

### 8. 移除「逾期定時掃描」

- 不做 `OverdueScannerJob`
- `overdue` 為衍生欄位:`overdue = (schedule.due != null) && (now > schedule.due) && (status != DONE && status != CANCELLED)`
- 前端 / API response 計算後返回(不存 DB)
- 若 user 想要「逾期通知」:訂閱 `factory-ops.task.due-soon`(? 見替代方案 D)

---

## Consequences

### 正面
- **跨 process 可達**:NATS 是 broker,任何訂閱者都能拿到事件
- **第三方整合容易**:Webhook 是業界標準,Slack / Line / 自建系統都接得起來
- **送達保證明確**:at-least-once + 訂閱端 dedup,業界共識做法
- **Outbox 防遺失**:即使 NATS 暫死,事件仍持久化在 MongoDB,worker 恢復後補送
- **不用排程**:沒有 scanner job,系統更輕
- **多租戶隔離**:每個事件帶 `rootOrgId`,訂閱端可 filter

### 負面
- **架構複雜度**:多了 NATS + outbox worker + webhook worker 三個動件;但現代雲原生標準,團隊熟悉度高
- **送達 ≠ 即時**:NATS 通常 < 100ms,但 outbox 模式下 worker 掃描有 delay(預設 1s)
- **Webhook 訂閱端要做防重複**:server 不保證 exactly-once,可能同 eventId 重複發送 1-N 次
- **逾期不主動通知**:工廠值班團隊若想要「dueAt 前 1 小時提醒」需要外部訂閱 NATS 並自建 scheduler。緩解:這是 use case 限定,可在 Notification 模組(Phase 2)補上「scheduled emit `task.due-soon`」(本期不做)
- **outbox collection 會持續長大**:設定 TTL index(已 dispatched 的 entry 保留 30 天 → 自動清);死信永久保留(供人工處理)

---

## Alternatives Considered

### A. 只做 In-memory Event Bus(v1.1 原方案)
- **優點**:零依賴
- **缺點**:跨 process 不可達;訂閱者必須與 producer 同 process;違背 §FR-Notification.1
- **不採用**

### B. Kafka 取代 NATS
- **優點**:吞吐量更高、更廣泛的工具生態
- **缺點**:
  - Kafka 部署比 NATS 重(Zookeeper 或 KRaft)
  - 工廠規模(估 < 1000 events/s)NATS 已綽綽有餘
  - Q-5 明確指定 NATS
- **不採用**

### C. 只做 Webhook,不做 NATS
- **優點**:更簡單
- **缺點**:
  - 內部訂閱者(本系統內 Notification 模組、Analytics)不適合用 Webhook 通訊
  - Webhook 有 retry 但延遲較高
  - 違背 Q-5
- **不採用**

### D. 主動逾期通知(scheduler emit `due-soon`)
- **優點**:user 友善
- **缺點**:Q-5 明確「不需要定時掃描」
- **本期不採用**;若日後變需求,在 Notification 模組(Phase 2)做

### E. Change Streams(MongoDB)取代 outbox
- **優點**:DB 原生,不用手寫 outbox
- **缺點**:
  - 需要 MongoDB replica set
  - 撈出來還是要轉成 NATS / Webhook,沒省多少
  - Change Stream 要小心 resume token 管理
- **本期不採用**;outbox 模式更顯式可控

---

## Compliance / Validation

### 寫入時
- 每個 aggregate update **必同時** `outbox.insertOne(event)` 在同個 MongoDB transaction
- worker 掃 outbox,排隊送 NATS / Webhook
- NATS 失敗 → 重試;5 次後標記 outbox `FAILED`,進 dead-letter 收集

### 訂閱端
- 必須以 `eventId` 去重(本系統內部 Notification 模組亦同)
- 驗證 Webhook HMAC 簽章
- 驗證 timestamp 差距 ≤ 5 分鐘

### 監控
- Prometheus metrics:
  - `outbox_pending_count`(當前 PENDING 數)
  - `outbox_dispatch_latency_seconds`
  - `webhook_attempt_total{event_type, status}`
  - `webhook_dead_letter_total`
- Alert:`outbox_pending_count > 1000` 持續 5 分鐘 → on-call

---

## Notes for Next Stage

### Collections
- `outbox_entries`:transactional outbox
  - 索引:`{ status: 1, createdAt: 1 }`(worker 掃描)、TTL on `dispatchedAt`(30 天)
- `webhooks`:訂閱註冊
  - 索引:`{ rootOrgId: 1, active: 1 }`、`{ rootOrgId: 1, events: 1 }`(multikey)
- `webhook_dead_letters`:死信
  - 索引:`{ rootOrgId: 1, createdAt: -1 }`,永久保留

### NATS 配置(docker-compose 預留)
```yaml
nats:
  image: nats:2.10-alpine
  command: ["-js", "-m", "8222"]
  ports: ["4222:4222", "8222:8222"]
```

JetStream stream:
- `FACTORY_OPS_EVENTS`:subject `factory-ops.>`,retention 7 天

### Backend 模組
- `EventPublisher`:domain layer 介面
- `OutboxEventPublisher`:寫 MongoDB outbox
- `OutboxRelayWorker`(Quarkus scheduler):每 1s 掃 PENDING 推送
- `NatsPublisher`:NATS JetStream client
- `WebhookDispatcher`:HTTP client + HMAC + retry

### 設定
- `factory-ops.outbox.poll-interval-ms`(預設 1000)
- `factory-ops.outbox.batch-size`(預設 100)
- `factory-ops.webhook.retry-schedule`(預設 `1m,5m,30m,2h,6h`)
- `factory-ops.nats.servers`(env)

---

## v1.4 Amendment(2026-05-08)— ActionRequest leaf reject 不主動推 push(Q-20 拍板)

### 背景

v1.3 引入 Single-hop direct dispatch(ADR-0008 v1.3),衍生 Q-20:**leaf 端 GROUP_MANAGER reject ActionRequest 時,系統是否要對 originator(發起的 manager)做主動推播**(in-app notification 或 email)?2026-05-07 規劃會議拍板:**不主動推**,沿用既有雙通道事件分發機制即可。

### Decision

1. **Leaf reject 觸發時,系統行為**:
   - ActionRequest `status = REJECTED`,`history[]` 寫入 reject reason
   - **`OutboxEntry` 寫入 `factory-ops.action-request.rejected`** 事件(同一 transaction)
   - `OutboxRelayWorker` 將事件推送至 NATS + 已註冊的 Webhook(雙通道,沿用本 ADR §1)
2. **系統不另行做的事**(明確列出避免實作走偏):
   - 不查詢 originating manager 的 user record 並產生 in-app notification 文件
   - 不寄送 email / SMS / push notification 給 originator
   - 不額外 emit 額外的 user-targeted event(例如 `user.notification.action-request-rejected`)
3. **Originator 得知 reject 的途徑**(由訂閱者 / 看板自處理):
   - 訂閱 `factory-ops.action-request.rejected` NATS topic 的下游服務(若未來建 Notification 模組,屬該模組職責)
   - 註冊 webhook 接 `action-request.rejected` 事件(典型整合 Slack / Line / 自建 dashboard)
   - **使用者主動查看**:`GET /action-requests/{id}` 或 originator 的儀表板報表(列出自己 dispatched 的 ActionRequest 與當前狀態)

### 為何不做 push

- **Q-5 / Q-10 既有原則**:本期僅預留 webhook 訂閱,實際 APNs / FCM / in-app push 留待後續 Mobile App / Notification 模組
- **資源邊界清晰**:Factory Ops 是工作管理系統,不是通知中心;若需「派工被退回提醒」,屬 Notification 子模組 / 第三方訂閱
- **冪等容易**:訂閱者收到 event 後自行決定是否 push,避免本系統內建 push 機制日後與 Notification 模組重複

### Consequences

**正面**:
- 通知策略一致:**所有事件都走相同的 NATS + Webhook 雙通道**,無「reject 是特例」的分支邏輯
- Factory Ops 後端不需新增 in-app notification document / push gateway 整合
- Originator 想要「reject 立刻通知」時,可透過 webhook 自建整合

**負面**:
- Originator 若不主動查看,會延遲得知 reject(由 Operations SOP 補:dispatcher 看儀表板「我發出的 ActionRequest」)
- 未來若有 stakeholder 想要 in-app push,需另開 Notification 模組(M6+ 候選主題)

**後續工作**:
- 無 code change(M5.1 不涉 backend)
- domain-model.md §4.12 sequence 補 reject 替代分支(已隨 v1.4 同步)
- requirements.md §FR-Dispatch.7 字句確認(已隨 v1.4 同步)
