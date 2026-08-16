package com.example.acousticguard

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class AudioClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    
    // Classes based on the PDF description
    val labels = listOf("Normal speech", "Background noise", "Loud shouting", "Scream-like sound")

    init {
        try {
            // Attempt to load TFLite model from assets (if it exists)
            val modelBuffer = loadModelFile("model.tflite")
            interpreter = Interpreter(modelBuffer)
            Log.i("AudioClassifier", "TensorFlow Lite model loaded successfully")
        } catch (e: Exception) {
            Log.w("AudioClassifier", "TFLite model not found in assets. Using mock classifier for prototype.")
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classifyAudio(audioFeatures: FloatArray, dbLoudness: Double, threshold: Int): Pair<String, Float> {
        if (interpreter != null) {
            // Real TFLite inference logic goes here
            // val outputBuffer = Array(1) { FloatArray(labels.size) }
            // interpreter?.run(audioFeatures, outputBuffer)
            return Pair("Unknown", 0f)
        } else {
            // Mock behavior for the student prototype based on Loudness thresholds
            return when {
                dbLoudness > threshold -> Pair("Scream-like sound", 0.92f)
                dbLoudness > threshold - 10 -> Pair("Loud shouting", 0.85f)
                dbLoudness > 40 -> Pair("Normal speech", 0.70f)
                else -> Pair("Background noise", 0.99f)
            }
        }
    }
}
