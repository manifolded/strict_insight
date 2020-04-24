package com.example.strict_insight

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

typealias LumiListener = (lumi: Pair<Double, Double>) -> Unit


class LumiCenter(listener: LumiListener? = null) : ImageAnalysis.Analyzer {

    private val listeners = ArrayList<LumiListener>().apply { listener?.let { add(it)} }

    // Helper extension function used to extract a byte array from an image plane buffer
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind() // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data) // Copy the buffer into a ByteArray
        return data // Return the ByteArray
    }


        // Let's keep it even though we never use it.
//    private fun indicesFromIt(it: Int, eta: Int, rho: Int): Pair<Int, Int> {
//        // rho: row stride
//        // eta: pixel stride
//
//        val j = it / rho
//        val i = (it.rem(rho)) / eta
//
//        return Pair(i, j)
//    }

    private fun itFromIndices(indices: Pair<Int, Int>, eta: Int, rho: Int): Int {
        val i = indices.first
        val j = indices.second
        return rho * j + eta * i
    }

    private fun sumOverImage(data: ByteArray, width: Int, height: Int,
                            eta: Int, rho: Int, mult: (Int, Int) -> Int): Double {
        var acc = 0.0
        for (i in 0 until width-1) {
            for (j in 0 until height-1) {
                acc += convertByteToInt(data[itFromIndices(Pair(i,j), eta, rho)]) * mult(i, j)
            }
        }
        return acc
    }

    private fun convertByteToInt(byte: Byte): Int {
        return byte.toInt() and 0xFF
    }

    override fun analyze(image: ImageProxy) {
        val rho: Int = image.planes[0].rowStride
        val eta: Int = image.planes[0].pixelStride
        val width: Int = image.width
        val height: Int = image.height

        // Since format is YUV, image.planes[0] contains the Y (luminance) plane
        val buffer = image.planes[0].buffer
        // Extract image data from callback object
        val data = buffer.toByteArray()
//        val pixels = data.map { it.toInt() and 0xFF }

        val sumOnVals = sumOverImage(data, width, height, eta, rho) { _, _ -> 1 }
        val iEst = sumOverImage(data, width, height, eta, rho) { i: Int, _ -> i } / sumOnVals
        val jEst = sumOverImage(data, width, height, eta, rho) { _, j: Int -> j } / sumOnVals

        // Call all listeners with new value
        listeners.forEach { it(Pair(iEst, jEst)) }

        // don't forget to release the image to keep the pipeline moving
        image.close()
    }
}