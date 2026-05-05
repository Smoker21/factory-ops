package com.factoryops.interfaces.rest

import com.factoryops.interfaces.filter.RequestContext
import com.factoryops.persistence.document.AttachmentDocument
import com.factoryops.persistence.repository.AttachmentRepository
import jakarta.inject.Inject
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.bson.types.ObjectId
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.time.Instant

data class CreateAttachmentRequest(
    @field:NotBlank val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val ownerResourceType: String? = null,
    val ownerResourceId: String? = null,
    val filename: String? = null
)

@Path("/v1/attachments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Attachments")
class AttachmentResource {

    @Inject
    lateinit var attachmentRepository: AttachmentRepository

    @Inject
    lateinit var requestContext: RequestContext

    @ConfigProperty(name = "minio.endpoint", defaultValue = "http://localhost:9000")
    lateinit var minioEndpoint: String

    @ConfigProperty(name = "minio.bucket", defaultValue = "factory-ops-attachments")
    lateinit var minioBucket: String

    @POST
    @Operation(summary = "Create attachment metadata and get upload presigned URL")
    @APIResponse(responseCode = "201", description = "Attachment created with presigned upload URL")
    fun create(@Valid request: CreateAttachmentRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()

        val storageKey = "org/$rootOrgId/${request.ownerResourceType?.lowercase() ?: "uploads"}/${ObjectId()}"
        val now = Instant.now()

        val doc = AttachmentDocument().also { d ->
            d.rootOrgId = ObjectId(rootOrgId)
            d.mimeType = request.mimeType
            d.sizeBytes = request.sizeBytes
            d.storageKey = storageKey
            d.uploaderId = ObjectId(actorId)
            d.ownerResourceType = request.ownerResourceType
            d.ownerResourceId = request.ownerResourceId?.let { ObjectId(it) }
            d.status = "PENDING_UPLOAD"
            d.createdAt = now
        }
        attachmentRepository.persist(doc)

        // DEV: return a synthetic presigned URL (real implementation uses MinIO SDK)
        val presignedUrl = "$minioEndpoint/$minioBucket/$storageKey?X-upload-key=${doc.id!!}"

        val response = mapOf(
            "attachmentId" to doc.id!!.toHexString(),
            "storageKey" to storageKey,
            "uploadUrl" to presignedUrl,
            "expiresInSeconds" to 3600,
            "mimeType" to request.mimeType
        )
        return Response.status(201).entity(response).build()
    }

    @GET
    @Path("/{attachmentId}")
    @Operation(summary = "Get attachment metadata and download URL")
    fun getById(@PathParam("attachmentId") attachmentId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val doc = attachmentRepository.findByIdAndRootOrg(ObjectId(attachmentId), ObjectId(rootOrgId))
            ?: return Response.status(404).build()

        val downloadUrl = "$minioEndpoint/$minioBucket/${doc.storageKey}?X-download-key=${doc.id!!}"

        val response = mapOf(
            "id" to doc.id!!.toHexString(),
            "rootOrgId" to doc.rootOrgId.toHexString(),
            "mimeType" to doc.mimeType,
            "sizeBytes" to doc.sizeBytes,
            "storageKey" to doc.storageKey,
            "uploaderId" to doc.uploaderId.toHexString(),
            "ownerResourceType" to doc.ownerResourceType,
            "ownerResourceId" to doc.ownerResourceId?.toHexString(),
            "status" to doc.status,
            "downloadUrl" to downloadUrl,
            "createdAt" to doc.createdAt.toString()
        )
        return Response.ok(response).build()
    }

    @POST
    @Path("/{attachmentId}/finalize")
    @Operation(summary = "Finalize attachment upload (mark as READY)")
    fun finalize(@PathParam("attachmentId") attachmentId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val doc = attachmentRepository.findByIdAndRootOrg(ObjectId(attachmentId), ObjectId(rootOrgId))
            ?: return Response.status(404).build()

        doc.status = "READY"
        attachmentRepository.update(doc)

        return Response.ok(mapOf("id" to doc.id!!.toHexString(), "status" to "READY")).build()
    }

    @DELETE
    @Path("/{attachmentId}")
    @Operation(summary = "Soft-delete attachment")
    fun delete(@PathParam("attachmentId") attachmentId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val doc = attachmentRepository.findByIdAndRootOrg(ObjectId(attachmentId), ObjectId(rootOrgId))
            ?: return Response.status(404).build()

        doc.deletedAt = Instant.now()
        doc.status = "DELETED"
        attachmentRepository.update(doc)
        return Response.noContent().build()
    }
}
