package jp.co.tscnet.cameracnn

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import android.media.MediaPlayer


/**
 * カメラ画面アクティビティ
 */
@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class MainActivity : AppCompatActivity() {
    companion object {
        const val PERMISSION_CAMERA        = 200
        const val PERMISSION_WRITE_STORAGE = 1000
    }

    /**
     * 各レイアウトオブジェクト変数を生成
     */
    private lateinit var shutterButton : ImageButton
    private lateinit var previewView   : TextureView
    private lateinit var imageReader   : ImageReader
    private lateinit var shutterSoundPlayer: MediaPlayer //added


    /**
     * 各種変数初期化
     */
    private lateinit var previewRequestBuilder : CaptureRequest.Builder
    private lateinit var previewRequest        : CaptureRequest
    private var backgroundHandler              : Handler?                = null
    private var backgroundThread               : HandlerThread?          = null
    private var cameraDevice                   : CameraDevice?           = null
    private lateinit var captureSession        : CameraCaptureSession


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /**
         * 画面部品取得
         */
        previewView = findViewById(R.id.mySurfaceView)
        shutterButton = findViewById(R.id.Shutter)


        /**
         * アクティビティ開始
         */
        initActivity()
    }
    /**
     * アクティビティ開始処理
     */
    private fun initActivity() {
        shutterSoundPlayer = MediaPlayer.create(this, R.raw.camerasound)
        shutterSoundPlayer.isLooping = false


        /**
         * ストレージ読み書きパーミッションの確認
         */
        val writePermission  = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if ( (writePermission != PackageManager.PERMISSION_GRANTED) ) {
            requestStoragePermission()
            return
        }

        /**
         * カメラ起動パーミッションの確認
         */
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }

        // カメラ実行
        previewView.surfaceTextureListener = surfaceTextureListener
        startBackgroundThread()

        /**
         * シャッターボタンにイベント生成
         */
        shutterButton.setOnClickListener {
            playShutterSound()


            // フォルダーを使用する場合、あるかを確認
            val appDir = File(Environment.getExternalStorageDirectory(), "CameraCNN")

            if (!appDir.exists()) {
                // なければ、フォルダーを作る
                appDir.mkdirs()
            }

            try {
                val filename = "picture.jpg"
                var savefile : File? = null

                /**
                 * プレビューの更新を止める
                 */
                captureSession.stopRepeating()

                if (previewView.isAvailable) {
                    savefile = File(appDir, filename)
                    val fos = FileOutputStream(savefile)
                    val bitmap: Bitmap = previewView.bitmap
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                    fos.close()
                }

                if (savefile != null) {
                    Log.d("edulog", "Image Saved On: $savefile")
                }

                val intent2 = Intent(this, ResultActivity::class.java)
                intent2.putExtra(ResultActivity.IMAGE_PATH, savefile.toString())
                startActivity(intent2)

            } catch (e: CameraAccessException) {
                Log.d("edulog", "CameraAccessException_Error: $e")
            } catch (e: FileNotFoundException) {
                Log.d("edulog", "FileNotFoundException_Error: $e")
            } catch (e: IOException) {
                Log.d("edulog", "IOException_Error: $e")
            }

            /**
             * プレビューを再開
             */
            captureSession.setRepeatingRequest(previewRequest, null, null)
        }

    }
    private fun playShutterSound() {
        shutterSoundPlayer.start()


    }


    /**
     * ストレージ利用許可取得ダイアログを表示
     */
    private fun requestStoragePermission() {
        /**
         * 書き込み権限
         */
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(baseContext)
                .setMessage("Permission Here")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_WRITE_STORAGE)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    finish()
                }
                .create()
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_WRITE_STORAGE)
        }
    }

    /**
     * カメラ利用許可取得ダイアログを表示
     */
    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(baseContext)
                .setMessage("Permission Check")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CAMERA)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    finish()
                }
                .show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CAMERA)
        }
    }

    /**
     * 権限要求コールバック
     */
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {

        // カメラ権限の場合
        when (requestCode) {
            PERMISSION_CAMERA -> {
                if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // 許可されない場合はアプリ終了
                    finish()
                }

                // 許可された場合は、一度アプリを再起動する
                val intent = getIntent()
                val appStarter = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT)
                val alarmManager =  getSystemService(ALARM_SERVICE) as AlarmManager
                alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), appStarter)
                finish()
            } // 書き込み権限の場合
            PERMISSION_WRITE_STORAGE -> {
                if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // 許可されない場合はアプリ終了
                    finish()
                }
                initActivity()
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }


    /**
     * カメラをバックグラウンドで実行
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * TextureView Listener
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener
    {
        // TextureViewが有効になった
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int)
        {
            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG,2)
            openCamera()
        }

        // TextureViewのサイズが変わった
        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, p1: Int, p2: Int) { }

        // TextureViewが更新された
        override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) { }

        // TextureViewが破棄された
        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean
        {
            return false
        }
    }

    /**
     * カメラ起動処理関数
     */
    private fun openCamera() {
        /**
         * カメラマネジャーの取得
         */
        val manager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            /**
             * カメラIDの取得
             */
            val camerId: String = manager.cameraIdList[0]

            /**
             * カメラ起動パーミッションの確認
             */
            val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission()
                return
            }

            /**
             * カメラ起動
             */
            manager.openCamera(camerId, stateCallback, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * カメラ状態取得コールバック関数
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        /**
         * カメラ接続完了
         */
        override fun onOpened(cameraDevice: CameraDevice) {
            this@MainActivity.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        /**
         * カメラ切断
         */
        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraDevice.close()
            this@MainActivity.cameraDevice = null
        }

        /**
         * カメラエラー
         */
        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            finish()
        }
    }

    /**
     * カメラ画像生成許可取得ダイアログを表示
     */
    private fun createCameraPreviewSession()
    {
        try
        {
            val texture = previewView.surfaceTexture
            texture.setDefaultBufferSize(previewView.width, previewView.height)

            val surface = Surface(texture)
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            cameraDevice?.createCaptureSession(Arrays.asList(surface, imageReader.surface),
                @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
                object : CameraCaptureSession.StateCallback()
                {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession)
                    {
                        if (cameraDevice == null) return
                        try
                        {
                            captureSession = cameraCaptureSession
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            previewRequest = previewRequestBuilder.build()
                            cameraCaptureSession.setRepeatingRequest(previewRequest, null, Handler(backgroundThread?.looper))
                        } catch (e: CameraAccessException) {
                            Log.e("erfs", e.toString())
                        }

                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        //Tools.makeToast(baseContext, "Failed")
                    }
                }, null)
        } catch (e: CameraAccessException) {
            Log.e("erf", e.toString())
        }
    }


}
