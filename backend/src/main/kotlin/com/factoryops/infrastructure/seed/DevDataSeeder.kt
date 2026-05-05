package com.factoryops.infrastructure.seed

import com.factoryops.application.auth.PasswordHasher
import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.document.GroupSettingsDocument
import com.factoryops.persistence.document.OrgSettingsDocument
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.QaSettingsDocument
import com.factoryops.persistence.document.TaskDocument
import com.factoryops.persistence.document.UserCredentialsDocument
import com.factoryops.persistence.document.UserDocument
import com.factoryops.persistence.document.ProjectDocument
import com.factoryops.persistence.document.QaReviewPolicyDocument
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.ProjectRepository
import com.factoryops.persistence.repository.TaskRepository
import com.factoryops.persistence.repository.UserCredentialsRepository
import com.factoryops.persistence.repository.UserRepository
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KotlinLogging
import org.bson.types.ObjectId
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Development seed data loader.
 * Creates initial test data when factory.ops.seed.enabled=true (dev profile).
 * Idempotent: checks for existing data before creating.
 */
@ApplicationScoped
class DevDataSeeder(
    private val orgRepository: OrganizationRepository,
    private val userRepository: UserRepository,
    private val credentialsRepository: UserCredentialsRepository,
    private val groupRepository: GroupRepository,
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val passwordHasher: PasswordHasher
) {

    @ConfigProperty(name = "factory.ops.seed.enabled", defaultValue = "false")
    var seedEnabled: Boolean = false

    fun onStart(@Observes event: StartupEvent) {
        if (!seedEnabled) return
        logger.info { "DevDataSeeder: starting seed data creation" }
        try {
            seed()
            logger.info { "DevDataSeeder: seed data creation completed" }
        } catch (ex: Exception) {
            logger.error(ex) { "DevDataSeeder: failed to create seed data" }
        }
    }

    private fun seed() {
        // Check if already seeded
        val existingRoot = orgRepository.find("code = 'taichung-fab' and deletedAt is null").firstResult()
        if (existingRoot != null) {
            logger.info { "DevDataSeeder: data already seeded, skipping" }
            return
        }

        val now = Instant.now()

        // 1. Create root org (FAB) — pre-generate id so rootOrgId = self in a single persist
        val rootId = ObjectId()
        val rootOrg = OrganizationDocument().also { d ->
            d.id = rootId
            d.rootOrgId = rootId
            d.parentId = null
            d.type = "FAB"
            d.name = "台中廠"
            d.code = "taichung-fab"
            d.leaderIds = emptyList()
            d.ancestorIds = emptyList()
            d.isLeaf = false
            d.depth = 0
            d.timezone = "Asia/Taipei"
            d.locale = "zh-TW"
            d.settings = OrgSettingsDocument().also { s ->
                s.orgMaxDepth = 5
                s.leafTypes = listOf("SECTION")
                s.attachmentMaxBytes = 52428800L
            }
            d.schemaVersion = 1
            d.createdAt = now
            d.updatedAt = now
        }
        orgRepository.persist(rootOrg)
        logger.info { "Seeded root org [$rootId] 台中廠" }

        // 2. Create DIVISION
        val division = OrganizationDocument().also { d ->
            d.rootOrgId = rootId
            d.parentId = rootId
            d.type = "DIVISION"
            d.name = "製造部"
            d.code = "manufacturing-div"
            d.ancestorIds = listOf(rootId)
            d.isLeaf = false
            d.depth = 1
            d.schemaVersion = 1
            d.createdAt = now
            d.updatedAt = now
        }
        orgRepository.persist(division)
        val divisionId: ObjectId = division.id!!

        // 3. Create DEPARTMENT
        val dept = OrganizationDocument().also { d ->
            d.rootOrgId = rootId
            d.parentId = divisionId
            d.type = "DEPARTMENT"
            d.name = "裝配部門"
            d.code = "assembly-dept"
            d.ancestorIds = listOf(rootId, divisionId)
            d.isLeaf = false
            d.depth = 2
            d.schemaVersion = 1
            d.createdAt = now
            d.updatedAt = now
        }
        orgRepository.persist(dept)
        val deptId: ObjectId = dept.id!!

        // 4. Create SECTION (leaf)
        val section = OrganizationDocument().also { d ->
            d.rootOrgId = rootId
            d.parentId = deptId
            d.type = "SECTION"
            d.name = "裝配課"
            d.code = "assembly-section"
            d.ancestorIds = listOf(rootId, divisionId, deptId)
            d.isLeaf = true
            d.depth = 3
            d.schemaVersion = 1
            d.createdAt = now
            d.updatedAt = now
        }
        orgRepository.persist(section)
        val sectionId: ObjectId = section.id!!
        logger.info { "Seeded org tree: FAB -> DIVISION -> DEPARTMENT -> SECTION" }

        // 5. Create users (check for duplicates first)
        val adminUser = createUserIfNotExists(rootId, "admin.system", "EMP-00001", "System Admin", "admin@factory.example.com", listOf("ADMIN"), "Admin@123456789", now)
        val managerUser = createUserIfNotExists(rootId, "manager.wang", "EMP-00002", "王廠長", "manager.wang@factory.example.com", listOf("ORG_ADMIN"), "Manager@123456789", now)
        val leaderUser = createUserIfNotExists(rootId, "leader.chen", "EMP-00003", "陳組長", "leader.chen@factory.example.com", listOf("SHIFT_LEAD", "GROUP_MANAGER"), "Leader@123456789", now)
        val operatorUser = createUserIfNotExists(rootId, "operator.li", "EMP-00004", "李作業員", "operator.li@factory.example.com", listOf("OPERATOR"), "Operator@123456789", now)
        val qaUser = createUserIfNotExists(rootId, "qa.zhang", "EMP-00005", "張品管", "qa.zhang@factory.example.com", listOf("QA", "ENGINEER"), "QA@1234567890", now)

        // Set manager for section
        section.managerId = managerUser
        section.leaderIds = listOf(leaderUser)
        orgRepository.update(section)

        // 6. Create group
        val group = GroupDocument().also { d ->
            d.rootOrgId = rootId
            d.organizationId = sectionId
            d.type = "SHIFT"
            d.name = "裝配課夜班"
            d.code = "assembly-night-shift"
            d.leaderId = leaderUser
            d.settings = GroupSettingsDocument().also { s ->
                s.qa = QaSettingsDocument().also { q ->
                    q.dualSignRequired = true
                    q.requiredReviewerRoles = listOf("QA", "SHIFT_LEAD")
                }
            }
            d.schemaVersion = 1
            d.createdAt = now
            d.updatedAt = now
        }
        groupRepository.persist(group)
        val groupId: ObjectId = group.id!!
        logger.info { "Seeded group [$groupId] 裝配課夜班" }

        // 7. Create project
        val project = ProjectDocument().also { d ->
            d.rootOrgId = rootId
            d.organizationId = sectionId
            d.name = "2026 Q2 設備維護專案"
            d.descriptionMarkdown = "## 目標\n本季度完成所有 A 級設備年保。"
            d.status = "ACTIVE"
            d.ownerId = leaderUser
            d.memberIds = listOf(leaderUser, operatorUser, qaUser)
            d.groupIds = listOf(groupId)
            d.tags = listOf("maintenance", "q2")
            d.schemaVersion = 1
            d.createdAt = now
            d.updatedAt = now
        }
        projectRepository.persist(project)
        val projectId: ObjectId = project.id!!
        logger.info { "Seeded project [$projectId] 2026 Q2 設備維護專案" }

        // 8. Create tasks
        val task1 = TaskDocument().also { d ->
            d.rootOrgId = rootId
            d.projectId = projectId
            d.type = "EQUIPMENT_INSPECTION"
            d.title = "CNC-M3 每週點檢"
            d.descriptionMarkdown = "## 點檢項目\n1. 油壓系統\n2. 主軸"
            d.status = "OPEN"
            d.priority = "HIGH"
            d.ownerId = operatorUser
            d.assignees = listOf(operatorUser)
            d.attributes = mapOf("equipmentId" to "CNC-M3", "checklist" to listOf(mapOf("id" to "chk-001", "label" to "油壓系統", "required" to true)))
            d.qaReviewPolicy = QaReviewPolicyDocument().also { p ->
                p.dualSignRequired = true
                p.requiredReviewerRoles = listOf("QA", "SHIFT_LEAD")
                p.snapshotAt = now
                p.sourceGroupIds = listOf(groupId)
            }
            d.tags = listOf("equipment", "weekly")
            d.schemaVersion = 1
            d.createdAt = now
            d.createdBy = leaderUser
            d.updatedAt = now
        }
        taskRepository.persist(task1)

        val task2 = TaskDocument().also { d ->
            d.rootOrgId = rootId
            d.projectId = projectId
            d.type = "SHIFT_HANDOVER"
            d.title = "2026-05-03 夜班交班"
            d.status = "OPEN"
            d.priority = "NORMAL"
            d.ownerId = leaderUser
            d.assignees = listOf(leaderUser)
            d.attributes = mapOf("outgoingShiftId" to "shift-day-001", "incomingShiftId" to "shift-night-001")
            d.qaReviewPolicy = QaReviewPolicyDocument().also { p ->
                p.dualSignRequired = false
                p.requiredReviewerRoles = emptyList()
                p.snapshotAt = now
            }
            d.schemaVersion = 1
            d.createdAt = now
            d.createdBy = leaderUser
            d.updatedAt = now
        }
        taskRepository.persist(task2)
        logger.info { "Seeded 2 tasks" }
    }

    private fun createUserIfNotExists(
        rootOrgId: ObjectId,
        accountName: String,
        employeeNo: String,
        displayName: String,
        email: String,
        roles: List<String>,
        password: String,
        now: Instant
    ): ObjectId {
        val existing = userRepository.findByAccountName(rootOrgId, accountName)
        if (existing != null) return existing.id!!

        val userDoc = UserDocument().also { d ->
            d.rootOrgId = rootOrgId
            d.accountName = accountName
            d.employeeNo = employeeNo
            d.email = email
            d.displayName = displayName
            d.roles = roles
            d.active = true
            d.hrSyncedAt = now
            d.createdAt = now
        }
        userRepository.persist(userDoc)

        val credDoc = UserCredentialsDocument().also { c ->
            c.userId = userDoc.id!!
            c.rootOrgId = rootOrgId
            c.passwordHash = passwordHasher.hash(password)
            c.algorithm = "BCRYPT"
            c.updatedAt = now
        }
        credentialsRepository.persist(credDoc)
        logger.info { "Seeded user [$accountName] id=${userDoc.id}" }
        return userDoc.id!!
    }
}
