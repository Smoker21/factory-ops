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
import com.factoryops.persistence.document.QaSettingsDocument
import com.factoryops.persistence.document.UserDocument
import com.factoryops.persistence.repository.GroupMembershipRepository
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Extended unit tests for GroupService — covering addMember, deleteGroup,
 * updateGroup, updateGroupSettings, listMembers operations.
 */
class GroupServiceExtendedUnitTest {

    private lateinit var groupRepository: GroupRepository
    private lateinit var membershipRepository: GroupMembershipRepository
    private lateinit var orgRepository: OrganizationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var groupService: GroupService

    private val rootId = ObjectId()
    private val orgId = ObjectId()
    private val groupId = ObjectId()
    private val actorId = ObjectId()

    @BeforeEach
    fun setup() {
        groupRepository = mock()
        membershipRepository = mock()
        orgRepository = mock()
        userRepository = mock()
        groupService = GroupService(groupRepository, membershipRepository, orgRepository, userRepository)
    }

    // ─── Helper builders ────────────────────────────────────────────────────────

    private fun makeGroupDoc(
        id: ObjectId = groupId,
        leaderId: ObjectId? = null,
        dualSignRequired: Boolean = false
    ): GroupDocument {
        val doc = GroupDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.organizationId = orgId
        doc.type = "SHIFT"
        doc.name = "Night Shift"
        doc.code = "night-shift"
        doc.leaderId = leaderId
        doc.settings = GroupSettingsDocument().also { s ->
            s.qa = QaSettingsDocument().also { q ->
                q.dualSignRequired = dualSignRequired
                q.requiredReviewerRoles = if (dualSignRequired) listOf("QA") else emptyList()
            }
        }
        doc.history = emptyList()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeUserDoc(id: ObjectId, active: Boolean = true): UserDocument {
        val doc = UserDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.accountName = "test.user.${id.toHexString().take(4)}"
        doc.employeeNo = "EMP-TEST"
        doc.displayName = "Test User"
        doc.active = active
        doc.groupIds = emptyList()
        doc.createdAt = Instant.now()
        return doc
    }

    // ─── addMember: success path ─────────────────────────────────────────────────

    @Test
    fun `should add member to group successfully`() {
        // Given
        val userId = ObjectId()
        val groupDoc = makeGroupDoc()
        val userDoc = makeUserDoc(id = userId)
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(userDoc)
        whenever(membershipRepository.findActiveByGroupIdAndUserId(any(), any(), any())).thenReturn(null)
        val capturedMembership = argumentCaptor<GroupMembershipDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<GroupMembershipDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(membershipRepository).persist(capturedMembership.capture())
        whenever(userRepository.findById(eq(userId))).thenReturn(userDoc)
        doNothing().whenever(userRepository).update(any<UserDocument>())

        // When
        val result = groupService.addMember(
            groupId = groupId.toHexString(),
            rootOrgId = rootId.toHexString(),
            userId = userId.toHexString(),
            role = GroupMembershipRole.MEMBER,
            actorId = actorId.toHexString()
        )

        // Then
        assertNotNull(result.id)
        assertEquals(userId.toHexString(), result.userId)
        assertEquals(GroupMembershipRole.MEMBER, result.role)
    }

    @Test
    fun `should reject adding member who is already in group`() {
        // Given
        val userId = ObjectId()
        val groupDoc = makeGroupDoc()
        val userDoc = makeUserDoc(id = userId)
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(userDoc)
        whenever(membershipRepository.findActiveByGroupIdAndUserId(any(), any(), any()))
            .thenReturn(GroupMembershipDocument())  // already exists!

        // When / Then
        org.junit.jupiter.api.assertThrows<ConflictException> {
            groupService.addMember(
                groupId = groupId.toHexString(),
                rootOrgId = rootId.toHexString(),
                userId = userId.toHexString(),
                role = GroupMembershipRole.MEMBER,
                actorId = actorId.toHexString()
            )
        }
    }

    @Test
    fun `should reject adding member when user not found`() {
        // Given
        val userId = ObjectId()
        val groupDoc = makeGroupDoc()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            groupService.addMember(
                groupId = groupId.toHexString(),
                rootOrgId = rootId.toHexString(),
                userId = userId.toHexString(),
                role = GroupMembershipRole.MEMBER,
                actorId = actorId.toHexString()
            )
        }
    }

    // ─── deleteGroup: rejects when has active members ────────────────────────────

    @Test
    fun `should reject group deletion when group has active members`() {
        // Given
        val groupDoc = makeGroupDoc()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        val memberDoc = GroupMembershipDocument()
        memberDoc.id = ObjectId()
        memberDoc.rootOrgId = rootId
        memberDoc.groupId = groupId
        memberDoc.userId = ObjectId()
        whenever(membershipRepository.findActiveByGroupId(eq(rootId), eq(groupId))).thenReturn(listOf(memberDoc))

        // When / Then
        org.junit.jupiter.api.assertThrows<ConflictException> {
            groupService.deleteGroup(groupId.toHexString(), rootId.toHexString(), actorId.toHexString())
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
        groupService.deleteGroup(groupId.toHexString(), rootId.toHexString(), actorId.toHexString())

        // Then
        verify(groupRepository, never()).delete(any<GroupDocument>())
        assertNotNull(captured.firstValue.deletedAt)
    }

    // ─── updateGroup ─────────────────────────────────────────────────────────────

    @Test
    fun `should update group name and append history`() {
        // Given
        val groupDoc = makeGroupDoc()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        val captured = argumentCaptor<GroupDocument>()
        doNothing().whenever(groupRepository).update(captured.capture())

        // When
        groupService.updateGroup(
            groupId = groupId.toHexString(),
            rootOrgId = rootId.toHexString(),
            name = "Updated Night Shift",
            type = null,
            attributes = null,
            actorId = actorId.toHexString()
        )

        // Then
        val updated = captured.firstValue
        assertEquals("Updated Night Shift", updated.name)
        assertTrue(updated.history.any { it.action == "GROUP_UPDATED" })
    }

    @Test
    fun `should throw NotFoundException when updating non-existent group`() {
        // Given
        whenever(groupRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            groupService.updateGroup(groupId.toHexString(), rootId.toHexString(), "New Name", null, null, actorId.toHexString())
        }
    }

    // ─── updateGroupSettings: QA validation ─────────────────────────────────────

    @Test
    fun `should reject dualSignRequired=true with empty requiredReviewerRoles`() {
        // Given
        val groupDoc = makeGroupDoc()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)

        val invalidQa = QaSettings(dualSignRequired = true, requiredReviewerRoles = emptyList())

        // When / Then
        org.junit.jupiter.api.assertThrows<ValidationException> {
            groupService.updateGroupSettings(
                groupId = groupId.toHexString(),
                rootOrgId = rootId.toHexString(),
                qa = invalidQa,
                extras = null,
                actorId = actorId.toHexString()
            )
        }
    }

    @Test
    fun `should update QA settings successfully when dualSignRequired with roles`() {
        // Given
        val groupDoc = makeGroupDoc()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        doNothing().whenever(groupRepository).update(any<GroupDocument>())

        val newQa = QaSettings(
            dualSignRequired = true,
            requiredReviewerRoles = listOf(Role.QA, Role.SHIFT_LEAD)
        )

        // When
        val result = groupService.updateGroupSettings(
            groupId = groupId.toHexString(),
            rootOrgId = rootId.toHexString(),
            qa = newQa,
            extras = mapOf("note" to "updated"),
            actorId = actorId.toHexString()
        )

        // Then
        assertTrue(result.qa.dualSignRequired)
        assertEquals(2, result.qa.requiredReviewerRoles.size)
        assertEquals(mapOf("note" to "updated"), result.extras)
    }

    @Test
    fun `should reject invalid reviewer roles in updateGroupSettings`() {
        // Given
        val groupDoc = makeGroupDoc()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)

        // ADMIN is not an allowed reviewer role per INV-35
        val invalidQa = QaSettings(dualSignRequired = true, requiredReviewerRoles = listOf(Role.ADMIN))

        // When / Then
        org.junit.jupiter.api.assertThrows<ValidationException> {
            groupService.updateGroupSettings(
                groupId = groupId.toHexString(),
                rootOrgId = rootId.toHexString(),
                qa = invalidQa,
                extras = null,
                actorId = actorId.toHexString()
            )
        }
    }

    // ─── listMembers ─────────────────────────────────────────────────────────────

    @Test
    fun `should return only active members when includeInactive is false`() {
        // Given
        val memberDoc = GroupMembershipDocument()
        memberDoc.id = ObjectId()
        memberDoc.rootOrgId = rootId
        memberDoc.groupId = groupId
        memberDoc.userId = ObjectId()
        memberDoc.role = "MEMBER"
        memberDoc.joinedAt = Instant.now()
        memberDoc.createdBy = actorId

        whenever(membershipRepository.findActiveByGroupId(eq(rootId), eq(groupId))).thenReturn(listOf(memberDoc))

        // When
        val result = groupService.listMembers(groupId.toHexString(), rootId.toHexString(), includeInactive = false)

        // Then
        assertEquals(1, result.size)
        verify(membershipRepository).findActiveByGroupId(any(), any())
        verify(membershipRepository, never()).findByGroupIdIncludingInactive(any(), any())
    }

    @Test
    fun `should return all members when includeInactive is true`() {
        // Given
        whenever(membershipRepository.findByGroupIdIncludingInactive(eq(rootId), eq(groupId))).thenReturn(emptyList())

        // When
        val result = groupService.listMembers(groupId.toHexString(), rootId.toHexString(), includeInactive = true)

        // Then
        assertTrue(result.isEmpty())
        verify(membershipRepository).findByGroupIdIncludingInactive(any(), any())
    }

    // ─── getGroupSettings ────────────────────────────────────────────────────────

    @Test
    fun `should return group settings when group exists`() {
        // Given
        val groupDoc = makeGroupDoc(dualSignRequired = true)
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)

        // When
        val result = groupService.getGroupSettings(groupId.toHexString(), rootId.toHexString())

        // Then
        assertTrue(result.qa.dualSignRequired)
    }

    @Test
    fun `should throw NotFoundException when getting settings for non-existent group`() {
        // Given
        whenever(groupRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            groupService.getGroupSettings(groupId.toHexString(), rootId.toHexString())
        }
    }

    // ─── createGroup: validations ─────────────────────────────────────────────────

    @Test
    fun `should reject group creation when org is not leaf`() {
        // Given
        val nonLeafOrg = OrganizationDocument().also { d ->
            d.id = orgId
            d.rootOrgId = rootId
            d.type = "DIVISION"
            d.name = "Division"
            d.code = "div-001"
            d.isLeaf = false
            d.createdAt = Instant.now()
            d.updatedAt = Instant.now()
        }
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(nonLeafOrg)

        // When / Then
        org.junit.jupiter.api.assertThrows<ValidationException> {
            groupService.createGroup(
                rootOrgId = rootId.toHexString(),
                organizationId = orgId.toHexString(),
                type = "SHIFT",
                name = "Test Group",
                code = "test-grp",
                leaderId = null,
                attributes = emptyMap(),
                actorId = actorId.toHexString()
            )
        }
    }

    @Test
    fun `should reject group creation when code is duplicate`() {
        // Given
        val leafOrg = OrganizationDocument().also { d ->
            d.id = orgId
            d.rootOrgId = rootId
            d.type = "SECTION"
            d.name = "Section"
            d.code = "section-001"
            d.isLeaf = true
            d.createdAt = Instant.now()
            d.updatedAt = Instant.now()
        }
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(leafOrg)
        whenever(groupRepository.findByCode(eq(rootId), eq("existing-code"))).thenReturn(makeGroupDoc())

        // When / Then
        org.junit.jupiter.api.assertThrows<ConflictException> {
            groupService.createGroup(
                rootOrgId = rootId.toHexString(),
                organizationId = orgId.toHexString(),
                type = "SHIFT",
                name = "Test Group",
                code = "existing-code",
                leaderId = null,
                attributes = emptyMap(),
                actorId = actorId.toHexString()
            )
        }
    }
}
