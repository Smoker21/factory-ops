package com.factoryops.unit

import com.factoryops.application.service.GroupService
import com.factoryops.domain.group.GroupMembershipRole
import com.factoryops.domain.group.QaSettings
import com.factoryops.domain.shared.enums.Role
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.document.GroupMembershipDocument
import com.factoryops.persistence.document.GroupSettingsDocument
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.OrgSettingsDocument
import com.factoryops.persistence.document.QaSettingsDocument
import com.factoryops.persistence.document.UserDocument
import com.factoryops.persistence.repository.GroupMembershipRepository
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*
import java.time.Instant

/**
 * Pure unit tests for GroupService business rules.
 * Covers: leaf-only constraint, QA settings validation, soft-delete pre-conditions.
 */
class GroupServiceUnitTest {

    private lateinit var groupRepository: GroupRepository
    private lateinit var membershipRepository: GroupMembershipRepository
    private lateinit var orgRepository: OrganizationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var groupService: GroupService

    private val rootId = ObjectId()
    private val groupId = ObjectId()

    @BeforeEach
    fun setup() {
        groupRepository = mock()
        membershipRepository = mock()
        orgRepository = mock()
        userRepository = mock()
        groupService = GroupService(groupRepository, membershipRepository, orgRepository, userRepository)
    }

    // ─── Helper builders ───────────────────────────────────────────────────────

    private fun makeGroupDoc(
        id: ObjectId = groupId,
        isDeleted: Boolean = false,
        leaderId: ObjectId? = null,
        dualSign: Boolean = false
    ): GroupDocument {
        val doc = GroupDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.organizationId = ObjectId()
        doc.type = "SHIFT"
        doc.name = "Test Group"
        doc.code = "test-group"
        doc.leaderId = leaderId
        doc.settings = GroupSettingsDocument().also { s ->
            s.qa = QaSettingsDocument().also { q ->
                q.dualSignRequired = dualSign
                q.requiredReviewerRoles = if (dualSign) listOf("QA") else emptyList()
            }
        }
        doc.history = emptyList()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        if (isDeleted) doc.deletedAt = Instant.now()
        return doc
    }

    private fun makeOrgDoc(isLeaf: Boolean): OrganizationDocument {
        val doc = OrganizationDocument()
        doc.id = ObjectId()
        doc.rootOrgId = rootId
        doc.type = if (isLeaf) "SECTION" else "DEPARTMENT"
        doc.name = "Test Org"
        doc.code = "test-org"
        doc.isLeaf = isLeaf
        doc.depth = 2
        doc.ancestorIds = emptyList()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeUserDoc(id: ObjectId = ObjectId(), active: Boolean = true): UserDocument {
        val doc = UserDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.accountName = "test.user"
        doc.displayName = "Test User"
        doc.employeeNo = "EMP001"
        doc.active = active
        doc.roles = emptyList()
        doc.groupIds = emptyList()
        doc.orgManagerScopes = emptyList()
        doc.createdAt = Instant.now()
        return doc
    }

    private fun makeMembershipDoc(userId: ObjectId): GroupMembershipDocument {
        val doc = GroupMembershipDocument()
        doc.rootOrgId = rootId
        doc.groupId = groupId
        doc.userId = userId
        doc.role = "MEMBER"
        doc.joinedAt = Instant.now()
        doc.createdBy = userId
        return doc
    }

    // ─── INV-22: Group must be created in a leaf org ─────────────────────────

    @Test
    fun `should reject group creation in non-leaf organization`() {
        // Given
        val nonLeafOrg = makeOrgDoc(isLeaf = false)
        whenever(orgRepository.findByIdAndRootOrg(any(), any())).thenReturn(nonLeafOrg)

        // When / Then
        assertThrows(ValidationException::class.java) {
            groupService.createGroup(
                rootOrgId = rootId.toHexString(),
                organizationId = nonLeafOrg.id!!.toHexString(),
                type = "SHIFT",
                name = "Invalid Group",
                code = "invalid-grp",
                leaderId = null,
                attributes = emptyMap(),
                actorId = ObjectId().toHexString()
            )
        }
    }

    @Test
    fun `should create group successfully in leaf organization`() {
        // Given
        val leafOrg = makeOrgDoc(isLeaf = true)
        whenever(orgRepository.findByIdAndRootOrg(any(), any())).thenReturn(leafOrg)
        whenever(groupRepository.findByCode(any(), any())).thenReturn(null)
        val captured = argumentCaptor<GroupDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<GroupDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(groupRepository).persist(captured.capture())

        // When
        groupService.createGroup(
            rootOrgId = rootId.toHexString(),
            organizationId = leafOrg.id!!.toHexString(),
            type = "SHIFT",
            name = "Morning Shift",
            code = "morning-shift",
            leaderId = null,
            attributes = emptyMap(),
            actorId = ObjectId().toHexString()
        )

        // Then
        val persisted = captured.firstValue
        assertEquals("morning-shift", persisted.code)
    }

    // ─── INV-23: Duplicate code within org ───────────────────────────────────

    @Test
    fun `should reject group creation with duplicate code in same root org`() {
        // Given
        val leafOrg = makeOrgDoc(isLeaf = true)
        val existingGroup = makeGroupDoc()
        whenever(orgRepository.findByIdAndRootOrg(any(), any())).thenReturn(leafOrg)
        whenever(groupRepository.findByCode(any(), eq("existing-code"))).thenReturn(existingGroup)

        // When / Then
        assertThrows(ConflictException::class.java) {
            groupService.createGroup(
                rootOrgId = rootId.toHexString(),
                organizationId = leafOrg.id!!.toHexString(),
                type = "SHIFT",
                name = "Duplicate",
                code = "existing-code",
                leaderId = null,
                attributes = emptyMap(),
                actorId = ObjectId().toHexString()
            )
        }
    }

    // ─── QA settings: dualSignRequired requires non-empty roles ─────────────

    @Test
    fun `should reject updateGroupSettings when dualSignRequired is true but roles is empty`() {
        // Given
        val groupDoc = makeGroupDoc()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)

        // When / Then
        assertThrows(ValidationException::class.java) {
            groupService.updateGroupSettings(
                groupId = groupId.toHexString(),
                rootOrgId = rootId.toHexString(),
                qa = QaSettings(dualSignRequired = true, requiredReviewerRoles = emptyList()),
                extras = null,
                actorId = ObjectId().toHexString()
            )
        }
    }

    @Test
    fun `should update QA settings and append history entry`() {
        // Given
        val groupDoc = makeGroupDoc()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        val captured = argumentCaptor<GroupDocument>()
        doNothing().whenever(groupRepository).update(captured.capture())

        // When
        groupService.updateGroupSettings(
            groupId = groupId.toHexString(),
            rootOrgId = rootId.toHexString(),
            qa = QaSettings(dualSignRequired = true, requiredReviewerRoles = listOf(Role.QA)),
            extras = null,
            actorId = ObjectId().toHexString()
        )

        // Then
        val updated = captured.firstValue
        assertTrue(updated.settings.qa.dualSignRequired)
        assertTrue(updated.history.any { it.action == "GROUP_SETTINGS_QA_UPDATED" })
    }

    // ─── INV-15, INV-35: soft-delete pre-conditions ───────────────────────────

    @Test
    fun `should reject soft-delete when group has active members`() {
        // Given
        val memberId = ObjectId()
        val groupDoc = makeGroupDoc()
        val membership = makeMembershipDoc(memberId)
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        whenever(membershipRepository.findActiveByGroupId(eq(rootId), eq(groupId))).thenReturn(listOf(membership))

        // When / Then
        assertThrows(ConflictException::class.java) {
            groupService.deleteGroup(groupId.toHexString(), rootId.toHexString(), ObjectId().toHexString())
        }
    }

    @Test
    fun `should soft-delete group when no active members`() {
        // Given
        val groupDoc = makeGroupDoc()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        whenever(membershipRepository.findActiveByGroupId(eq(rootId), eq(groupId))).thenReturn(emptyList())
        val captured = argumentCaptor<GroupDocument>()
        doNothing().whenever(groupRepository).update(captured.capture())

        // When
        groupService.deleteGroup(groupId.toHexString(), rootId.toHexString(), ObjectId().toHexString())

        // Then
        assertNotNull(captured.firstValue.deletedAt, "deletedAt must be set on soft-delete")
    }

    // ─── addMember: reject duplicate membership ───────────────────────────────

    @Test
    fun `should reject addMember when user is already active member`() {
        // Given
        val userId = ObjectId()
        val groupDoc = makeGroupDoc()
        val userDoc = makeUserDoc(id = userId)
        val existingMembership = makeMembershipDoc(userId)
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(userDoc)
        whenever(membershipRepository.findActiveByGroupIdAndUserId(eq(rootId), eq(groupId), eq(userId))).thenReturn(existingMembership)

        // When / Then
        assertThrows(ConflictException::class.java) {
            groupService.addMember(groupId.toHexString(), rootId.toHexString(), userId.toHexString(), GroupMembershipRole.MEMBER, ObjectId().toHexString())
        }
    }

    // ─── removeMember: reject removing group leader ────────────────────────────

    @Test
    fun `should reject removing group leader via removeMember`() {
        // Given
        val leaderId = ObjectId()
        val groupDoc = makeGroupDoc(leaderId = leaderId)
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)

        // When / Then
        assertThrows(ConflictException::class.java) {
            groupService.removeMember(groupId.toHexString(), rootId.toHexString(), leaderId.toHexString(), ObjectId().toHexString())
        }
    }

    // ─── transferLeader: new leader must be active member ────────────────────

    @Test
    fun `should reject transferLeader when candidate is not an active member`() {
        // Given
        val groupDoc = makeGroupDoc()
        val newLeaderId = ObjectId()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        whenever(membershipRepository.findActiveByGroupIdAndUserId(eq(rootId), eq(groupId), eq(newLeaderId))).thenReturn(null)

        // When / Then
        assertThrows(ConflictException::class.java) {
            groupService.transferLeader(groupId.toHexString(), rootId.toHexString(), newLeaderId.toHexString(), ObjectId().toHexString())
        }
    }

    @Test
    fun `should append GROUP_LEADER_TRANSFERRED history on successful transfer`() {
        // Given
        val oldLeaderId = ObjectId()
        val newLeaderId = ObjectId()
        val groupDoc = makeGroupDoc(leaderId = oldLeaderId)
        val membership = makeMembershipDoc(newLeaderId)
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        whenever(membershipRepository.findActiveByGroupIdAndUserId(eq(rootId), eq(groupId), eq(newLeaderId))).thenReturn(membership)
        val captured = argumentCaptor<GroupDocument>()
        doNothing().whenever(groupRepository).update(captured.capture())

        // When
        groupService.transferLeader(groupId.toHexString(), rootId.toHexString(), newLeaderId.toHexString(), ObjectId().toHexString())

        // Then
        val updated = captured.firstValue
        assertEquals(newLeaderId, updated.leaderId)
        assertTrue(updated.history.any { it.action == "GROUP_LEADER_TRANSFERRED" })
    }

    // ─── getGroup: throws NotFoundException ──────────────────────────────────

    @Test
    fun `should throw NotFoundException when group not found`() {
        // Given
        whenever(groupRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        assertThrows(NotFoundException::class.java) {
            groupService.getGroup(ObjectId().toHexString(), rootId.toHexString())
        }
    }
}
