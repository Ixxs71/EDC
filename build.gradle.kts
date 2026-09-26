plugins {
    // 8.13.2 (au lieu de 8.5.2) : CameraX 1.6.2 (module Dashcam) exige AGP >= 8.9.1.
    id("com.android.application") version "8.13.2" apply false
    // 2.1.20 (au lieu de 1.9.24) : une dépendance transitive de CameraX (module Dashcam) tire un
    // kotlin-stdlib 2.1.20, illisible par le compilateur 1.9.x ("compiled with an incompatible
    // version of Kotlin").
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}
