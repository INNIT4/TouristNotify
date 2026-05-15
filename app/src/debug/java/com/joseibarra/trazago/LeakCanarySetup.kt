package com.joseibarra.trazago

import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers
import shark.IgnoredReferenceMatcher
import shark.ReferencePattern

object LeakCanarySetup {

    fun configure() {
        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = AndroidReferenceMatchers.appDefaults + listOf(
                // Firebase Transport: JobInfoSchedulerService retenido por un thread de pool
                // brevemente después de onDestroy. Bug conocido del SDK de Google.
                IgnoredReferenceMatcher(
                    pattern = ReferencePattern.InstanceFieldPattern(
                        className = "com.google.android.datatransport.runtime.scheduling" +
                            ".jobscheduling.JobInfoSchedulerService\$\$ExternalSyntheticLambda0",
                        fieldName = "f\$0"
                    )
                ),
                // Google Maps SDK: campo estático `a` en la clase interna ax retiene
                // una cadena de objetos internos del SDK que a veces incluye views de
                // MapsActivity ya destruida. Bug conocido del Maps SDK.
                IgnoredReferenceMatcher(
                    pattern = ReferencePattern.StaticFieldPattern(
                        className = "com.google.maps.api.android.lib6.phoenix.ax",
                        fieldName = "a"
                    )
                )
            )
        )
    }
}
