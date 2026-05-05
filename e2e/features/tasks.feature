# language: zh-TW
功能: Task 狀態流轉
  Task 必須遵守允許的狀態轉移圖:
    OPEN → IN_PROGRESS → IN_REVIEW → DONE
    任意狀態 → CANCELLED
    DONE 與 CANCELLED 為終態

  背景:
    假設 我以 admin.system 登入
    並且 已存在一個 status="OPEN" 的 Task "T-DEMO-1",owner 為 admin.system

  情境大綱: 合法狀態轉移會更新 Task 與 history
    假設 Task "T-DEMO-1" 目前狀態為 "<from>"
    當 我點擊轉到 "<to>" 的按鈕
    那麼 Task 狀態應為 "<to>"
    並且 history 應 append 一筆 action="TASK_STATUS_CHANGED" payload from="<from>" to="<to>"

    例子:
      | from        | to          |
      | OPEN        | IN_PROGRESS |
      | IN_PROGRESS | IN_REVIEW   |
      | IN_REVIEW   | DONE        |
      | OPEN        | CANCELLED   |

  情境: 終態 Task 不顯示狀態變更按鈕
    假設 Task "T-DEMO-1" 狀態為 "DONE"
    當 我打開該 Task 詳情頁
    那麼 我應該看不到任何狀態轉換按鈕

  情境: dual-sign Task 在 IN_REVIEW 必須兩名指定角色簽核才能 DONE
    假設 Task "T-QA-1" 屬於設定 dualSignRequired=true、requiredReviewerRoles=["QA","SHIFT_LEAD"] 的 Group,狀態為 IN_REVIEW
    當 我以 qa.zhang(QA) 簽核 APPROVED
    那麼 Task 狀態仍應為 "IN_REVIEW"
    當 我以 leader.chen(SHIFT_LEAD) 簽核 APPROVED
    那麼 Task 狀態應變為 "DONE"
    並且 qaReviews 陣列長度應為 2
