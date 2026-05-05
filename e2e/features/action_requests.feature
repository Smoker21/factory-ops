# language: zh-TW
功能: ActionRequest single-hop dispatch (ADR-0008)
  manager 將需求 dispatch 給一個 leaf Organization,
  target org 的 leader 在收件夾應看到此需求,
  triage 後可建出 Task。

  情境: dispatch 成功後出現在 target org 的收件列表
    假設 我以 manager.wang(ORG_ADMIN) 登入
    當 我建立一個 ActionRequest
      | 標題      | 嚴重程度 | targetOrg(leaf)  | 描述      |
      | 待測 AR-1 | HIGH     | assembly-section | 機台異常  |
    並且 我送出 dispatch
    那麼 該 AR 狀態應為 "SUBMITTED"
    並且 originatingOrgId 應為 manager.wang 所屬節點
    當 我以 leader.chen(SHIFT_LEAD) 切換登入
    並且 我打開「動作需求」收件夾
    那麼 我應該看到「待測 AR-1」

  情境: triage 並建出對應 Task,雙向連結正確
    假設 已存在一個 status="SUBMITTED" 的 AR "待測 AR-2",targetOrg 為 leader.chen 所屬節點
    並且 我以 leader.chen 登入
    當 我打開「待測 AR-2」並點擊「triage 並建立 Task」
    並且 我選擇 Task 類型 "EQUIPMENT_INSPECTION"、owner 為自己
    並且 我送出
    那麼 應該建出新 Task "T-FROM-AR-2"
    並且 Task.originActionRequestId 應為 "待測 AR-2" 的 ID
    並且 AR.linkedTaskId 應為 "T-FROM-AR-2" 的 ID
    並且 AR 狀態應為 "TRIAGED"
