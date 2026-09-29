package com.autotennisclub.app.session

/**
 * The paid session that must survive an app restart. Saved from the moment the
 * customer starts paying until the session ends.
 */
data class ActiveSession(
    /** Our unique payment reference; the provider can be asked about it after a restart. */
    val reference: String,
    /** Null until the provider has confirmed the charge. */
    val transactionId: String?,
    val mode: TrainingMode,
    val minutes: Int,
    val priceCents: Long,
    val config: CustomConfig?,
    val remainingSeconds: Long,
    val savedAtMillis: Long
) {
    val paid: Boolean get() = transactionId != null

    /** Short form shown to customers on S03; the full reference is in the error log. */
    val shortReference: String get() = reference.take(8).uppercase()
}

interface SessionStore {
    fun load(): ActiveSession?
    fun save(session: ActiveSession)
    fun clear()
}

class InMemorySessionStore(private var session: ActiveSession? = null) : SessionStore {
    override fun load(): ActiveSession? = session
    override fun save(session: ActiveSession) {
        this.session = session
    }
    override fun clear() {
        session = null
    }
}
