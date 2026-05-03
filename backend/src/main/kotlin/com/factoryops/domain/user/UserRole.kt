package com.factoryops.domain.user

import com.factoryops.domain.shared.enums.Role

/**
 * User 角色指派 Value Object。
 * 包裝 Role enum,供 User.roles 使用。
 * 注意:ORG_MANAGER 為衍生角色(由 Organization.managerId 反查),
 *       不直接儲存於 User.roles[](v1.3 規範)。
 */
typealias UserRole = Role
