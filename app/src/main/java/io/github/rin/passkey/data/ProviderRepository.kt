package io.github.rin.passkey.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.service.credentials.CredentialProviderService
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import java.text.Collator

data class PasskeyProvider(
    val packageName: String,
    val label: String,
    val components: List<String>,
    val icon: Bitmap?,
    val isSystem: Boolean,
)

data class ProviderScan(val providers: List<PasskeyProvider>, val skipped: Int)

class ProviderRepository(private val context: Context) {
    private val packageManager = context.packageManager

    fun scan(): ProviderScan {
        val candidates = listOf(
            CredentialProviderService.SERVICE_INTERFACE,
            "android.service.credentials.system.CredentialProviderService",
        ).flatMap { action ->
            packageManager.queryIntentServices(
                Intent(action),
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        }.mapNotNull { it.serviceInfo }.distinctBy { ComponentName(it.packageName, it.name) }

        var skipped = 0
        val services = candidates.filter { service ->
            try {
                service.enabled && service.applicationInfo.enabled && service.exported &&
                    service.permission == Manifest.permission.BIND_CREDENTIAL_PROVIDER_SERVICE &&
                    service.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED == 0 &&
                    supportsPasskeys(service)
            } catch (exception: Exception) {
                skipped++
                Log.w(TAG, "Cannot inspect provider ${service.packageName}/${service.name}", exception)
                false
            }
        }

        val collator = Collator.getInstance()
        val providers = services.groupBy { it.packageName }.map { (packageName, appServices) ->
            val application = appServices.first().applicationInfo
            val label = try {
                packageManager.getApplicationLabel(application).toString()
            } catch (exception: Exception) {
                Log.w(TAG, "Cannot load label for $packageName", exception)
                packageName
            }
            val icon = try {
                packageManager.getApplicationIcon(application).toBitmap(144, 144)
            } catch (exception: Exception) {
                Log.w(TAG, "Cannot load icon for $packageName", exception)
                null
            }
            PasskeyProvider(
                packageName = packageName,
                label = label,
                components = appServices.map { ComponentName(it.packageName, it.name).flattenToString() }.sorted(),
                icon = icon,
                isSystem = application.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            )
        }.sortedWith { left, right -> collator.compare(left.label, right.label) }
        return ProviderScan(providers, skipped)
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
