package com.example.camerax_x

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

typealias LumaListener = (luma: Double) -> Unit

// this class is from https://codelabs.developers.google.com/codelabs/camerax-getting-started/#7
class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {

    private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }
    private var lastAnalyzedTimestamp = 0L

     // Helper extension function used to extract a byte array from an image plane buffer
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a ByteArray
        return data // Return the ByteArray
    }

    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        // Calculate the average luma no more often than twice every second
        if (currentTimestamp - lastAnalyzedTimestamp >=
            TimeUnit.MILLISECONDS.toMillis(500)) {
            // Since format is YUV, image.planes[0] contains the Y (luminance) plane
            val buffer = image.planes[0].buffer
            // Extract image data from callback object
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()
            // Call all listeners with new value
            listeners.forEach { it(luma) }

            // don't forget to release the image to keep the pipeline moving
            image.close()
            // and lastly update timestamp of last analyzed frame
            lastAnalyzedTimestamp = currentTimestamp
        }
    }
}

