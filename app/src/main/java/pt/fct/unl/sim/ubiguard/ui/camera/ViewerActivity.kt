package pt.fct.unl.sim.ubiguard.ui.camera

import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import pt.fct.unl.sim.ubiguard.R

class ViewerActivity : AppCompatActivity() {

    private var alarmId: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        alarmId = intent.getStringExtra("ALARM_ID") ?: ""

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }
        webView.loadUrl("https://xisquinha.github.io/testUbiguard/viewer.html?alarmId=$alarmId")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Tell Firebase the stream is no longer needed
        if (alarmId.isNotEmpty()) {
            FirebaseDatabase.getInstance()
                .getReference("alarms/$alarmId/webrtc/streamRequested")
                .setValue(false)
        }
    }
}
