package com.example.blind3

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage.fromMediaImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


//typealias LumaListener = (luma: Double) -> Unit
typealias MyListener = (text: String) -> Unit/*lambda 표현식 사용*/

//스플래시 화면, 앱 시작시 보여지는 엑티비티
class SplashActivity : AppCompatActivity() {

    val SPLASH_VIEW_TIME: Long = 1000 //2초간 스플래시 화면을 보여줌 (ms)

    override fun onCreate(savedInstanceState: Bundle?) {/*엑티비티가 처음 생성될 때 호출되는 함수*/
        super.onCreate(savedInstanceState)

        Handler().postDelayed({ //delay를 위한 handler
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_VIEW_TIME)
    }
}

//메인 화면, 중심이 되는 메인 엑티비티
@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var mtts:TextToSpeech/*tts*/
    private val key = ""/*API key*/
    override fun onCreate(savedInstanceState: Bundle?) {/*엑티비티가 처음 생성될 때 호출되는 함수*/
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btn_event = findViewById<Button>(R.id.button)/*button event 처리*/

        btn_event.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        //tts 언어 설정
        mtts = TextToSpeech(applicationContext) { status ->
            if (status != TextToSpeech.ERROR) {
                //if there is no error then set language
                mtts.language = Locale.KOREAN /*Languge Select*/
            }
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        // Set up the listener for take photo button
        //camera_capture_button.setOnClickListener { takePhoto() }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }



    //번역
    private fun translation(q: String): String {
        //kakao translate
        var jsonString = ""
        var textko = ""
        try {
            jsonString = Jsoup.connect("https://dapi.kakao.com/v2/translation/translate")
                .header("Authorization", "KakaoAK " + key)
                .data("query", q)
                .data("src_lang", "en")
                .data("target_lang", "kr")
                .ignoreContentType(true)
                .post()
                .body()
                .text()
            val jobject = JSONObject(jsonString)
            val translatedarray = jobject.getJSONArray("translated_text")
            val jarray = translatedarray.getJSONArray(0)
            textko = jarray.getString(0)
        } catch (e: IOException) {
            e.printStackTrace()
            textko="번역 도중 오류가 발생하였습니다."
        }
        return textko
    }
    /*
        papago
        var jsonString: String? = ""
        var textko: String? = ""
        try {
            jsonString = Jsoup.connect("https://openapi.naver.com/v1/papago/n2mt")
                    .header("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("x-naver-client-id", "") // naver client id
                    .header("x-naver-client-secret", "") // naver client pw
                    .data("source", "en")
                    .data("target", "ko")
                    .data("text", q)
                    .ignoreContentType(true)
                    .post()
                    .body()
                    .text()
            val jobject = JSONObject(jsonString)
            val messageobject = jobject.getJSONObject("message")
            val resultobject = messageobject.getJSONObject("result")
            textko = resultobject.getString("translatedText")
        } catch (e: IOException) {
            e.printStackTrace()
            textko="에러"
        }
    */

    //tts함수
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun ttsGreater21(text: String) {
        var textko: String? = ""
        Thread {
            if(!text.startsWith("//")) {
                val arrtext = text.split("/").toTypedArray()
                textko = /*"신뢰도 " + arrtext[1] +"%인 "*/ "앞에 " + translation("There is a " + arrtext[0]).replace("있다", "있어요").replace("있습니다", "있어요")
            } else textko = translation(text.replace("//",""))
            runOnUiThread { //Toast.makeText(applicationContext, textko, Toast.LENGTH_LONG).show()
                val utteranceId = this.hashCode().toString() + ""
                mtts.speak(textko, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                Thread.sleep(2000)
            }
        }.start()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                //카메라 권한을 얻지 못하였을 경우
                ttsGreater21("//Permissions not granted by the user.")
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                //finish()
            }
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        //tts 종료
        if (mtts != null) {
            mtts.stop()
            mtts.shutdown()
        }
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        //private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private var REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
//카메라 켜는 시작함수로 생각
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                    }
            imageCapture = ImageCapture.Builder().build()

            val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, YourImageAnalyzer { textall: String ->
                            Log.d(TAG, "TEST Image Labeling: $textall")
                            ttsGreater21(textall)
                            //Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
                        })
                    }
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private class YourImageAnalyzer(private val listener: MyListener) : ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                // Pass image to an ML Kit Vision API
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        for (label in labels) {
                            val text = label.text
                            val confidence = Math.round((label.confidence) * 10000)/100
                            //val index = label.index
                            //var textall = text + "/" + confidence
                            if(confidence>70)
                                listener(text)
                        }
                    }
                    /*.addOnFailureListener {  ->
                        // Task failed with an exception
                        // ...
                    }*/
            }
        }
    }
}