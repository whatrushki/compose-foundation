package app.what.foundation.ui.animations

import android.os.Build

actual val supportsHardwareBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
