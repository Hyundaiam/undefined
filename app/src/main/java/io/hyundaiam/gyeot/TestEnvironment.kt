package io.hyundaiam.gyeot

import android.os.Build

object TestEnvironment {
    val available get() = BuildConfig.DEBUG && Build.HARDWARE in setOf("ranchu", "goldfish")
}
