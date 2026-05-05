package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import java.time.Instant

@MongoEntity(collection = "revoked_tokens")
class RevokedTokenDocument : PanacheMongoEntity() {
    lateinit var jti: String
    var revokedAt: Instant = Instant.now()
    /** Token expiry — TTL index uses this field to auto-expire documents after the token lifetime */
    var expiresAt: Instant = Instant.now()
}
