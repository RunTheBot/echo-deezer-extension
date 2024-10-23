package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings

class DeezerSession(
    var credentials: DeezerCredentials? = null,
    var settings: Settings? = null,
    var arlExpired: Boolean = false
) {
    private val lock = Any()

    fun updateCredentials(
        arl: String? = null,
        sid: String? = null,
        token: String? = null,
        userId: String? = null,
        licenseToken: String? = null,
        email: String? = null,
        pass: String? = null
    ) {
        synchronized(lock) {
            val current = credentials ?: DeezerCredentials("", "", "", "", "", "", "")
            credentials = current.copy(
                arl = arl ?: current.arl,
                sid = sid ?: current.sid,
                token = token ?: current.token,
                userId = userId ?: current.userId,
                licenseToken = licenseToken ?: current.licenseToken,
                email = email ?: current.email,
                pass = pass ?: current.pass
            )
        }
    }

    fun isArlExpired(expired: Boolean) {
        arlExpired = expired
    }

    companion object {
        @Volatile
        private var instance: DeezerSession? = null

        fun getInstance(): DeezerSession {
            return instance ?: synchronized(this) {
                instance ?: DeezerSession().also { instance = it }
            }
        }
    }
}

data class DeezerCredentials(
    val arl: String,
    val sid: String,
    val token: String,
    val userId: String,
    val licenseToken: String,
    val email: String,
    val pass: String
)