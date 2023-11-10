package com.woojun.labeling_app

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import com.otaliastudios.cameraview.BuildConfig
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.VideoResult
import com.otaliastudios.cameraview.controls.Mode
import com.woojun.labeling_app.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.File
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var timer: Timer? = null
    private var sliderValueList = mutableListOf<Int>()
    private var frameList = mutableListOf<Bitmap>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            camera.setLifecycleOwner(this@MainActivity)
            camera.mode = Mode.VIDEO
            slider.value = 0.5f
            camera.addCameraListener(object : CameraListener() {
                override fun onVideoTaken(result: VideoResult) {
                    CoroutineScope(Dispatchers.IO).launch {
                        result.file.apply {
                            withContext(Dispatchers.Main) {
                                camera.visibility = View.GONE
                                loadingProgress.visibility = View.VISIBLE
                            }
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(this.path)

                            val videoDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
                            val frameIntervalInMillis = 500

                            for (timeInMillis in 0 until videoDuration step frameIntervalInMillis.toLong()) {
                                val frame = retriever.getFrameAtTime(timeInMillis * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                frame?.let {
                                    frameList.add(it)
                                }
                            }

                            frameList.forEachIndexed { index, bitmap ->
                                val retrofit = RetrofitClient.getInstance()
                                val apiService = retrofit.create(RetrofitAPI::class.java)

                                val call = bitmapToBase64(bitmap)?.let {
                                    RequestBody(sliderValueList[index].toString(),
                                        it
                                    )
                                }?.let {
                                    apiService.ImagePost(
                                        it
                                    )
                                }

                                call?.enqueue(object : Callback<Void> {
                                    override fun onResponse(call: Call<Void>, response: Response<Void>) {
                                        Toast.makeText(baseContext, "성공", Toast.LENGTH_SHORT).show()
                                    }

                                    override fun onFailure(call: Call<Void>, t: Throwable) {
                                        Toast.makeText(baseContext, "실패", Toast.LENGTH_SHORT).show()
                                    }
                                })

                            }

                            withContext(Dispatchers.Main) {
                                camera.visibility = View.VISIBLE
                                loadingProgress.visibility = View.GONE

                                sliderValueList = mutableListOf<Int>()
                                frameList = mutableListOf<Bitmap>()
                            }
                        }
                    }
                }


                override fun onVideoRecordingStart() {
                    super.onVideoRecordingStart()
                    startTimer()
                }

                override fun onVideoRecordingEnd() {
                    super.onVideoRecordingEnd()
                    stopTimer()
                }
            })

            // 사진 또는 동영상 촬영 버튼 리스너
            captureBtn.setOnClickListener {
                if (camera.isTakingVideo) {
                    camera.stopVideo()
                } else {
                    camera.takeVideo(
                        File(baseContext.filesDir, "video_${System.currentTimeMillis()}.mp4")
                    )
                }
            }
        }
    }

    private fun startTimer() {
        timer = Timer()
        val task = object : TimerTask() {
            override fun run() {
                binding.apply {
                    sliderValueList.add((slider.value*100).toInt())
                }
            }
        }
        timer?.schedule(task, 500, 500)
    }

    private fun stopTimer() {
        timer?.cancel()
        timer?.purge()
        timer = null
    }

    fun bitmapToBase64(bitmap: Bitmap?): String? {
        if (bitmap == null) return null

        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

}


data class RequestBody(
    val slider: String,
    val image : String,
    val extension: String = "png"
)

interface RetrofitAPI {
    @POST("image")
    fun ImagePost(
        @Body requestBody: RequestBody
    ): Call<Void>

}

object RetrofitClient {
    private var instance: Retrofit? = null
    private val gson = GsonBuilder().setLenient().create()

    fun getInstance(): Retrofit {
        if(instance == null) {
            instance = Retrofit.Builder()
                .baseUrl("http://192.168.0.82:10001/api/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS) // 읽기 제한 시간을 조정 (예: 30초)
                    .build())
                .build()
        }
        return instance!!
    }
}