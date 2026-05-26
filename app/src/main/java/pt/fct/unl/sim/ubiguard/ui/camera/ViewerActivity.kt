package pt.fct.unl.sim.ubiguard.ui.camera

import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ImageView
import androidx.drawerlayout.widget.DrawerLayout
import com.android.identity.util.UUID
import com.google.firebase.database.FirebaseDatabase
import pt.fct.unl.sim.ubiguard.R
import pt.fct.unl.sim.ubiguard.ui.base.BaseActivity

class ViewerActivity : BaseActivity() {

    private var alarmId: String = ""
    private var tempToken: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        alarmId = intent.getStringExtra("ALARM_ID") ?: ""

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        if (drawerLayout != null && ivMenuIcon != null) {
            ativarSliderComponent(drawerLayout, ivMenuIcon)
        }

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
        tempToken = UUID.randomUUID().toString()


        val dbRef =
            FirebaseDatabase.getInstance().getReference("alarms/$alarmId/webrtc/access_token")
        dbRef.setValue(tempToken).addOnCompleteListener {
            val urlComSeguranca =
                "https://xisquinha.github.io/testUbiguard/viewer.html?alarmId=$alarmId&token=$tempToken"
            webView.loadUrl(urlComSeguranca)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (alarmId.isNotEmpty()) {
            val db = FirebaseDatabase.getInstance()
            db.getReference("alarms/$alarmId/webrtc/streamRequested").setValue(false)
            db.getReference("alarms/$alarmId/webrtc/access_token").removeValue()
        }
    }
}
