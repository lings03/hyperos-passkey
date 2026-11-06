package io.github.rin.passkey.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.service.autofill.AutofillService
import android.service.credentials.CredentialProviderService
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import java.text.Collator

/** An installed app with the credential provider and autofill services it offers, grouped like Android Settings does. */
data class ProviderApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isSystem: Boolean,
    /** Every usable credential provider service, including password-only ones. */
    val credentialComponents: List<String>,
    /** The credential provider services that declare passkey support. */
    val passkeyComponents: List<String>,
    val autofillComponents: List<String>,
) {
    val providesPasskeys: Boolean get() = passkeyComponents.isNotEmpty()
    val providesCredentials: Boolean get() = credentialComponents.isNotEmpty()
    val providesAutofill: Boolean get() = autofillComponents.isNotEmpty()
}

data class ProviderScan(
    val apps: List<ProviderApp>,
    val skipped: Int,
    /** Every service the system resolves for the credential provider actions, usable or not. */
    val resolvableCredentialServices: Set<String>,
    /** The internal Credential Manager autofill service declared by the system, if any. */
    val credentialAutofillService: String?,
    /** What Android Settings writes as the autofill service when the preferred provider has none. */
    val noAppAutofillValue: String,
) {
    /**
     * Configured credential services the system can no longer resolve, because their app was
     * removed, disabled, or no longer declares the service. The system ignores these entries.
     */
    fun staleComponents(settings: CredentialSettings): List<String> = try {
        (settings.enabledComponents() + settings.selectedComponents()).distinct().filterNot { it in resolvableCredentialServices }
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    /** Whether an autofill value means "no autofill app": unset, empty, or a Credential Manager placeholder. */
    fun isNoAppAutofill(value: String?): Boolean =
        value.isNullOrEmpty() || value == CredentialSettings.AUTOFILL_PLACEHOLDER ||
            (credentialAutofillService != null && CredentialSettings.autofillComponent(value) == credentialAutofillService)
}

class ProviderRepository(private val context: Context) {
    private val packageManager = context.packageManager

    // queryIntentServices already leaves out components disabled at runtime. ServiceInfo.enabled only
    // reflects the manifest, so it is not checked: Google Play services ships its autofill service
    // disabled in the manifest and enables it at runtime.
    fun scan(): ProviderScan {
        var skipped = 0
        val credentialCandidates = queryServices(
            CredentialProviderService.SERVICE_INTERFACE,
            "android.service.credentials.system.CredentialProviderService",
        )
        val credentialServices = credentialCandidates.mapNotNull { service ->
            try {
                if (!service.applicationInfo.enabled || !service.exported ||
                    service.permission != Manifest.permission.BIND_CREDENTIAL_PROVIDER_SERVICE ||
                    service.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0
                ) return@mapNotNull null
                service to supportsPasskeys(service)
            } catch (exception: Exception) {
                skipped++
                Log.w(TAG, "Cannot inspect provider ${service.packageName}/${service.name}", exception)
                null
            }
        }

        val credentialAutofillService = credentialAutofillService()
        val autofillCandidates = queryServices(AutofillService.SERVICE_INTERFACE)
        val autofillServices = autofillCandidates.filter { service ->
            try {
                // The Credential Manager autofill service is internal; Android Settings hides it too.
                component(service) != credentialAutofillService && service.applicationInfo.enabled &&
                    service.permission == Manifest.permission.BIND_AUTOFILL_SERVICE &&
                    service.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED == 0
            } catch (exception: Exception) {
                skipped++
                Log.w(TAG, "Cannot inspect autofill service ${service.packageName}/${service.name}", exception)
                false
            }
        }

        val credentialsByPackage = credentialServices.groupBy { it.first.packageName }
        val autofillByPackage = autofillServices.groupBy { it.packageName }
        val collator = Collator.getInstance()
        val apps = (credentialsByPackage.keys + autofillByPackage.keys).map { packageName ->
            val credentials = credentialsByPackage[packageName].orEmpty()
            val autofill = autofillByPackage[packageName].orEmpty()
            val application = (credentials.firstOrNull()?.first ?: autofill.first()).applicationInfo
            ProviderApp(
                packageName = packageName,
                label = label(application),
                icon = icon(application),
                isSystem = application.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                credentialComponents = credentials.map { component(it.first) }.sorted(),
                passkeyComponents = credentials.filter { it.second }.map { component(it.first) }.sorted(),
                autofillComponents = autofill.map(::component).sorted(),
            )
        }.sortedWith { left, right -> collator.compare(left.label, right.label) }

        return ProviderScan(
            apps = apps,
            skipped = skipped,
            resolvableCredentialServices = credentialCandidates.map(::component).toSet(),
            credentialAutofillService = credentialAutofillService,
            noAppAutofillValue = credentialAutofillService?.takeIf { declared -> autofillCandidates.any { component(it) == declared } }
                ?: CredentialSettings.AUTOFILL_PLACEHOLDER,
        )
    }

    private fun queryServices(vararg actions: String): List<ServiceInfo> = actions.flatMap { action ->
        packageManager.queryIntentServices(
            Intent(action),
            PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )
    }.mapNotNull { it.serviceInfo }.distinctBy(::component)

    private fun component(service: ServiceInfo) = ComponentName(service.packageName, service.name).flattenToString()

    private fun label(application: ApplicationInfo): String = try {
        packageManager.getApplicationLabel(application).toString()
    } catch (exception: Exception) {
        Log.w(TAG, "Cannot load label for ${application.packageName}", exception)
        application.packageName
    }

    private fun icon(application: ApplicationInfo): Bitmap? = try {
        packageManager.getApplicationIcon(application).toBitmap(144, 144)
    } catch (exception: Exception) {
        Log.w(TAG, "Cannot load icon for ${application.packageName}", exception)
        null
    }

    /** Reads the framework's config_defaultCredentialManagerAutofillService (Android 15+); absent on Android 14. */
    @SuppressLint("DiscouragedApi")
    private fun credentialAutofillService(): String? = try {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier("config_defaultCredentialManagerAutofillService", "string", "android")
        if (id == 0) null else ComponentName.unflattenFromString(resources.getString(id))?.flattenToString()
    } catch (exception: Exception) {
        Log.w(TAG, "Cannot read the Credential Manager autofill service", exception)
        null
    }

    private fun supportsPasskeys(service: ServiceInfo): Boolean {
        val parser = service.loadXmlMetaData(packageManager, CredentialProviderService.SERVICE_META_DATA) ?: return false
        return parser.use {
            while (parser.eventType != XmlPullParser.START_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) parser.next()
            if (parser.name != "credential-provider") return@use false
            var capabilitiesDepth = -1
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (parser.name == "capabilities" && parser.depth == 2) capabilitiesDepth = parser.depth
                    if (capabilitiesDepth == 2 && parser.depth == 3 && parser.name == "capability") {
                        if (parser.getAttributeValue(null, "name") == PUBLIC_KEY_TYPE) return@use true
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.depth == capabilitiesDepth) {
                    capabilitiesDepth = -1
                }
            }
            false
        }
    }

    private companion object {
        const val TAG = "PasskeyProviders"
        const val PUBLIC_KEY_TYPE = "androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL"
    }
}
