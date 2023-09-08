package jp.co.tscnet.cameracnn

import android.app.Activity
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.graphics.BitmapFactory
import java.io.File
import java.nio.ByteOrder


class Classifier(activity: Activity) {
    private val MODEL_NAME = "predict2.tflite"

    private val IMAGE_SIZE = 170
    private val IMAGE_MEAN = 128
    private val IMAGE_STD = 128.0f

    private var tffile: Interpreter
    private var labelProbArray: Array<FloatArray>

    init {
        tffile = Interpreter(loadModelFile(activity)) // deprecated
        labelProbArray = Array(1){FloatArray(5)}
    }


    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(MODEL_NAME)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classifyImageFromPath(path: String): Int {
        val file = File(path)

        if (!file.exists()) {
            throw Exception("Fail to load image")
        }

        // load image
        val bitmap = BitmapFactory.decodeFile(file.path)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE,true)

        // convert bitmap to bytebuffer
        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)

        // classification with TF Lite
        val pred = classifyImage(byteBuffer)

        return onehotToLabel(pred[0])
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect( IMAGE_SIZE * IMAGE_SIZE * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(IMAGE_SIZE * IMAGE_SIZE)

        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until IMAGE_SIZE) {
            for (j in 0 until IMAGE_SIZE) {
                val v = intValues[pixel++]

                byteBuffer.putFloat((((v.shr(16) and 0xFF) - IMAGE_MEAN) / IMAGE_STD))
                byteBuffer.putFloat((((v.shr(8) and 0xFF) - IMAGE_MEAN) / IMAGE_STD))
                byteBuffer.putFloat((((v and 0xFF) - IMAGE_MEAN) / IMAGE_STD))
            }
        }
        return byteBuffer
    }

    fun classifyImage(bytebuffer: ByteBuffer): Array<FloatArray> {
        tffile.run(bytebuffer, labelProbArray)
        return labelProbArray
    }

    private fun onehotToLabel(floatArray: FloatArray): Int {
        val tmp = floatArray.indices.maxBy { floatArray[it] } ?: -1
        return tmp + 1
    }
}