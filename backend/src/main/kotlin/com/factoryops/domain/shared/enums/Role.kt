package com.factoryops.domain.shared.enums

/**
 * 系統角色枚舉。
 *
 * 對應 openapi.yaml components/schemas/Role。
 * 共 9 個角色:
 * - 第一線執行:OPERATOR / SHIFT_LEAD / ENGINEER / QA
 * - Group 管理:GROUP_ADMIN / GROUP_MANAGER
 * - Org 管理:ORG_MANAGER(衍生角色,由 Organization.managerId 反查;不直接寫入 User.roles[])
 * - 全 root 管理:ORG_ADMIN
 * - 系統管理:ADMIN
 *
 * 注意(v1.3):ORG_MANAGER 為衍生角色,不儲存在 User.roles[];
 * 其 scope 由 User.orgManagerScopes[] cache 反映。
 */
enum class Role {
    /** 操作員(第一線執行) */
    OPERATOR,
    /** 班長(第一線) */
    SHIFT_LEAD,
    /** 工程師(第一線) */
    ENGINEER,
    /** 品保人員(第一線) */
    QA,
    /** Group 管理員 */
    GROUP_ADMIN,
    /** Group 經理 */
    GROUP_MANAGER,
    /** 組織節點經理(衍生角色;不存於 User.roles[]) */
    ORG_MANAGER,
    /** Root org 最高管理員 */
    ORG_ADMIN,
    /** 系統管理員(SaaS 維運) */
    ADMIN
}
