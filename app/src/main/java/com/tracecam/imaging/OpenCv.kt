package com.tracecam.imaging

import android.util.Log
import org.opencv.android.OpenCVLoader

/** Loads the OpenCV native library once, on first use. */
object OpenCv {
    val isLoaded: Boolean by lazy {
        OpenCVLoader.initLocal().also { if (!it) Log.e("OpenCv", "Failed to load OpenCV") }
    }
}
