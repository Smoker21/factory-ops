package com.factoryops.application.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import jakarta.enterprise.context.ApplicationScoped

/**
 * Password hashing service using BCrypt.
 * IMPORTANT: Hashed passwords must never be logged or included in API responses.
 */
@ApplicationScoped
class PasswordHasher {

    private val cost = 12

    fun hash(plaintext: String): String =
        BCrypt.withDefaults().hashToString(cost, plaintext.toCharArray())

    fun verify(plaintext: String, hash: String): Boolean =
        BCrypt.verifyer().verify(plaintext.toCharArray(), hash.toCharArray()).verified
}
