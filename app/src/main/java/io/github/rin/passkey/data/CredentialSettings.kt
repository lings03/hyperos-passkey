package io.github.rin.passkey.data

/**
 * The three secure settings this app manages, exactly as read from or written to the system.
 * A null value means the setting is absent; an empty string is a distinct, stored value.
 */
data class CredentialSettings(val enabled: String?, val primary: String?, val autofill: String?) {
    fun enabledComponents(): List<String> = components(enabled)

    fun selectedComponents(): Set<String> = components(primary).toSet()

    fun select(services: List<String>, keepOthers: Boolean): CredentialSettings {
        require(services.isNotEmpty()) { "No credential services selected" }
        val selected = services.map(::canonicalComponent).distinct()
        val active = if (keepOthers) (components(enabled) + selected).distinct() else selected
        return copy(enabled = active.joinToString(":"), primary = selected.joinToString(":"))
    }

    /** Adds services to the enabled list without changing the preferred providers. */
    fun withEnabled(services: List<String>): CredentialSettings {
        require(services.isNotEmpty()) { "No credential services selected" }
        val current = components(enabled)
        val next = (current + services.map(::canonicalComponent)).distinct()
        return if (next == current) this else copy(enabled = next.joinToString(":"))
    }

    /** Removes every enabled service of a package. Preferred providers cannot be removed this way. */
    fun withoutPackage(packageName: String): CredentialSettings {
        require(selectedComponents().none { packageOf(it) == packageName }) { "Cannot disable a preferred provider" }
        val current = components(enabled)
        val next = current.filterNot { packageOf(it) == packageName }
        return if (next == current) this else copy(enabled = next.joinToString(":"))
    }

    /** Removes the given services from both the enabled and the preferred lists. */
    fun withoutComponents(services: Set<String>): CredentialSettings {
        val removed = services.map(::canonicalComponent).toSet()
        fun prune(value: String?): String? {
            val current = components(value)
            return if (current.none { it in removed }) value else current.filterNot { it in removed }.joinToString(":")
        }
        return copy(enabled = prune(enabled), primary = prune(primary))
    }

    fun value(key: String): String? = when (key) {
        ENABLED_KEY -> enabled
        PRIMARY_KEY -> primary
        AUTOFILL_KEY -> autofill
        else -> throw IllegalArgumentException("Unknown settings key")
    }

    fun values(): List<Pair<String, String?>> = KEYS.map { it to value(it) }

    /**
     * Checks the credential lists strictly. The autofill value is only checked for size here because
     * the system may hold values this app would never write; [validateAutofill] checks a value before it is written.
     */
    fun validate() {
        listOf(enabled, primary, autofill).forEach { value ->
            require(value == null || value.length <= MAX_VALUE_LENGTH) { "Settings exceed maximum size" }
        }
        components(enabled)
        components(primary)
    }

    fun validateAutofill() {
        require(isValidAutofill(autofill)) { "Invalid autofill service" }
    }

    companion object {
        const val ENABLED_KEY = "credential_service"
        const val PRIMARY_KEY = "credential_service_primary"
        const val AUTOFILL_KEY = "autofill_service"
        val KEYS = listOf(ENABLED_KEY, PRIMARY_KEY, AUTOFILL_KEY)

        /** Written by Android Settings when the preferred credential provider has no autofill service. */
        const val AUTOFILL_PLACEHOLDER = "credential-provider"

        private const val MAX_VALUE_LENGTH = 16_384
        private val componentPattern = Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+/\\.?[a-zA-Z_][a-zA-Z0-9_.$]*")

        fun canonicalComponent(value: String): String {
            require(componentPattern.matches(value)) { "Invalid credential service component" }
            val (packageName, className) = value.split('/', limit = 2)
            return "$packageName/${if (className.startsWith('.')) packageName + className else className}"
        }

        fun components(value: String?): List<String> =
            if (value.isNullOrEmpty()) emptyList() else value.split(':').map(::canonicalComponent)

        fun packageOf(component: String): String = component.substringBefore('/')

        fun isValidAutofill(value: String?): Boolean =
            value.isNullOrEmpty() || value == AUTOFILL_PLACEHOLDER || componentPattern.matches(value)

        /** Returns the canonical component of an autofill value, or null for empty and placeholder values. */
        fun autofillComponent(value: String?): String? =
            if (value != null && componentPattern.matches(value)) canonicalComponent(value) else null
    }
}
