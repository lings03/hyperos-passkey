package io.github.rin.passkey.data

data class CredentialSettings(val enabled: String?, val primary: String?) {
    fun selectedComponents(): Set<String> = components(primary).toSet()

    fun select(services: List<String>, keepOthers: Boolean): CredentialSettings {
        require(services.isNotEmpty()) { "No credential services selected" }
        val selected = services.map(::canonicalComponent).distinct()
        val active = if (keepOthers) (components(enabled) + selected).distinct() else selected
        return CredentialSettings(active.joinToString(":"), selected.joinToString(":"))
    }

    fun validate() {
        listOf(enabled, primary).forEach { value ->
            require(value == null || value.length <= 16_384) { "Credential settings exceed maximum size" }
            components(value)
        }
    }

    companion object {
        const val ENABLED_KEY = "credential_service"
        const val PRIMARY_KEY = "credential_service_primary"
        private val componentPattern = Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+/\\.?[a-zA-Z_][a-zA-Z0-9_.$]*")

        fun canonicalComponent(value: String): String {
            require(componentPattern.matches(value)) { "Invalid credential service component" }
            val (packageName, className) = value.split('/', limit = 2)
            return "$packageName/${if (className.startsWith('.')) packageName + className else className}"
        }

        fun components(value: String?): List<String> =
            if (value.isNullOrEmpty()) emptyList() else value.split(':').map(::canonicalComponent)
    }
}
