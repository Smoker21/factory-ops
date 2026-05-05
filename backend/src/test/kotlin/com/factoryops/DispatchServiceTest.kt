package com.factoryops

import com.factoryops.application.service.DispatchService
import com.factoryops.domain.actionrequest.SeverityLevel
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.ForbiddenException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.OrgSettingsDocument
import com.factoryops.persistence.repository.OrganizationRepository
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

@QuarkusTest
class DispatchServiceTest {

    @Inject
    lateinit var dispatchService: DispatchService

    @Inject
    lateinit var orgRepository: OrganizationRepository

    private fun createLeafOrg(rootId: ObjectId, leaderIds: List<ObjectId>, managerId: ObjectId? = null): OrganizationDocument {
        val now = Instant.now()
        val doc = OrganizationDocument().also { d ->
            d.rootOrgId = rootId
            d.parentId = rootId
            d.type = "SECTION"
            d.name = "Test Leaf Section"
            d.code = "test-leaf-${System.currentTimeMillis()}"
            d.managerId = managerId
            d.leaderIds = leaderIds
            d.ancestorIds = listOf(rootId)
            d.isLeaf = true
            d.depth = 1
            d.createdAt = now
            d.updatedAt = now
        }
        orgRepository.persist(doc)
        return doc
    }

    @Test
    fun `dispatch fails when target is not leaf`() {
        val rootId = ObjectId()
        val now = Instant.now()

        // Create non-leaf org
        val nonLeaf = OrganizationDocument().also { d ->
            d.rootOrgId = rootId
            d.type = "DEPARTMENT"
            d.name = "Dept"
            d.code = "dept-${System.currentTimeMillis()}"
            d.isLeaf = false
            d.depth = 1
            d.ancestorIds = listOf(rootId)
            d.createdAt = now
            d.updatedAt = now
        }
        orgRepository.persist(nonLeaf)

        assertThrows(ValidationException::class.java) {
            dispatchService.dispatchActionRequest(
                targetOrgId = nonLeaf.id.toHexString(),
                title = "Test dispatch",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.LOW,
                severityReason = null,
                ownerId = null,
                actorId = ObjectId().toHexString(),
                actorOrgManagerScopes = listOf(rootId.toHexString()),
                actorRootOrgId = rootId.toHexString()
            )
        }
    }

    @Test
    fun `dispatch fails when actor is not manager of ancestor`() {
        val rootId = ObjectId()
        val actorId = ObjectId()

        val leafOrg = createLeafOrg(rootId, listOf(ObjectId()))

        assertThrows(ForbiddenException::class.java) {
            dispatchService.dispatchActionRequest(
                targetOrgId = leafOrg.id.toHexString(),
                title = "Test dispatch",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.LOW,
                severityReason = null,
                ownerId = null,
                actorId = actorId.toHexString(),
                actorOrgManagerScopes = listOf(ObjectId().toHexString()), // random scope, not ancestor
                actorRootOrgId = rootId.toHexString()
            )
        }
    }

    @Test
    fun `dispatch fails with target_org_no_leader when leaf has no leaders`() {
        val rootId = ObjectId()

        val leafOrg = createLeafOrg(rootId, emptyList())

        assertThrows(ConflictException::class.java) {
            dispatchService.dispatchActionRequest(
                targetOrgId = leafOrg.id.toHexString(),
                title = "Test dispatch",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.LOW,
                severityReason = null,
                ownerId = null,
                actorId = ObjectId().toHexString(),
                actorOrgManagerScopes = listOf(rootId.toHexString()),
                actorRootOrgId = rootId.toHexString()
            )
        }
    }

    @Test
    fun `dispatch auto-assigns owner when leaf has single leader`() {
        val rootId = ObjectId()
        val leaderId = ObjectId()

        val leafOrg = createLeafOrg(rootId, listOf(leaderId))

        val ar = dispatchService.dispatchActionRequest(
            targetOrgId = leafOrg.id.toHexString(),
            title = "Auto-assign dispatch",
            descriptionMarkdown = "Test",
            severityLevel = SeverityLevel.MEDIUM,
            severityReason = "urgent",
            ownerId = null,
            actorId = ObjectId().toHexString(),
            actorOrgManagerScopes = listOf(rootId.toHexString()),
            actorRootOrgId = rootId.toHexString()
        )

        assertNotNull(ar.id)
        assertEquals(leaderId.toHexString(), ar.ownerId)
    }

    @Test
    fun `dispatch fails with owner_must_be_specified when leaf has multiple leaders and no ownerId`() {
        val rootId = ObjectId()
        val leader1 = ObjectId()
        val leader2 = ObjectId()

        val leafOrg = createLeafOrg(rootId, listOf(leader1, leader2))

        assertThrows(ValidationException::class.java) {
            dispatchService.dispatchActionRequest(
                targetOrgId = leafOrg.id.toHexString(),
                title = "Multi-leader dispatch",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.HIGH,
                severityReason = null,
                ownerId = null,
                actorId = ObjectId().toHexString(),
                actorOrgManagerScopes = listOf(rootId.toHexString()),
                actorRootOrgId = rootId.toHexString()
            )
        }
    }
}
