package org.openaustria.googletv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/** Einstiegspunkt der TV-App, gestartet über den LEANBACK_LAUNCHER. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
