package pt.fct.unl.sim.ubiguard.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import pt.fct.unl.sim.ubiguard.R

class AlarmMonitorService : Service() {

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val alarmListeners = mutableMapOf<String, ValueEventListener>()
    private val statusListeners = mutableMapOf<String, ValueEventListener>()
    private val pinListeners = mutableMapOf<String, ValueEventListener>()
    private val heartbeatListeners = mutableMapOf<String, ValueEventListener>()

    private val initialStatusLoad = mutableMapOf<String, Boolean>()
    private val initialPinLoad = mutableMapOf<String, Boolean>()

    private val heartbeatHandlers = mutableMapOf<String, Handler>()
    private val offlineRunnables = mutableMapOf<String, Runnable>()
    private val isOfflineMap = mutableMapOf<String, Boolean>()

    private var connectivityManager: android.net.ConnectivityManager? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var lastConnectedSSID: String = ""

    private var distanceFromHome: Int = 1000 // To Test this change to 1 (means 1 meter and it will trigger the notification 1 minute after disconnecting from the WiFi where ESP is connected)

    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient

    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var isRadarActive = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannels()

        val persistentNotification = NotificationCompat.Builder(this, "UBIGUARD_SERVICE")
            .setContentTitle(getString(R.string.notification_activated))
            .setContentText(getString(R.string.notification_monitoring))
            .setSmallIcon(R.drawable.ic_profile)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, persistentNotification)

        monitorAlarms()

        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
        monitorWiFi()

        return START_STICKY
    }

    private fun monitorAlarms() {
        val uid = auth.currentUser?.uid ?: return

        database.child("users").child(uid).child("alarms")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val alarmId = child.key ?: continue
                        if (!alarmListeners.containsKey(alarmId)) {
                            listenToAlarm(alarmId)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun listenToAlarm(alarmId: String) {

        val fireListener = database.child("alarms").child(alarmId).child("isFired")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isFired = snapshot.getValue(Boolean::class.java) ?: false
                    if (isFired) {
                        triggerNotification(alarmId)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        alarmListeners[alarmId] = fireListener

        initialStatusLoad[alarmId] = true
        val statusListener = database.child("alarms").child(alarmId).child("status")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.getValue(String::class.java) ?: return

                    if (initialStatusLoad[alarmId] == true) {
                        initialStatusLoad[alarmId] = false
                        return
                    }
                    triggerInformativeNotification(alarmId, "status", status)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        statusListeners[alarmId] = statusListener

        initialPinLoad[alarmId] = true
        val pinListener = database.child("alarms").child(alarmId).child("pin")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (initialPinLoad[alarmId] == true) {
                        initialPinLoad[alarmId] = false
                        return
                    }
                    triggerInformativeNotification(alarmId, "pin", "")
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        pinListeners[alarmId] = pinListener

        val handler = Handler(Looper.getMainLooper())
        heartbeatHandlers[alarmId] = handler

        val heartbeatListener = database.child("alarms").child(alarmId).child("heartbeat")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    offlineRunnables[alarmId]?.let { handler.removeCallbacks(it) }

                    if (isOfflineMap[alarmId] == true) {
                        isOfflineMap[alarmId] = false
                        triggerInformativeNotification(alarmId, "online", "")
                    }

                    val timeoutTask = Runnable {
                        isOfflineMap[alarmId] = true
                        triggerInformativeNotification(alarmId, "offline", "")
                    }
                    offlineRunnables[alarmId] = timeoutTask
                    handler.postDelayed(timeoutTask, 65000)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        heartbeatListeners[alarmId] = heartbeatListener
    }

    private fun triggerNotification(alarmId: String) {
        database.child("alarms").child(alarmId).child("name")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val alarmName = snapshot.getValue(String::class.java) ?: getString(R.string.notification_unknown_alarm)
                    val bitmapLogo = android.graphics.BitmapFactory.decodeResource(applicationContext.resources, R.drawable.logo_ubiguard)

                    val notification = NotificationCompat.Builder(this@AlarmMonitorService, "UBIGUARD_ALARM")
                        .setContentTitle(getString(R.string.notification_alarm_triggered))
                        .setContentText(getString(R.string.notification_fire_alarm, alarmName))
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setLargeIcon(bitmapLogo)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .build()

                    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(alarmId.hashCode(), notification)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun triggerInformativeNotification(alarmId: String, type: String, value: String) {
        database.child("alarms").child(alarmId).child("name")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val alarmName = snapshot.getValue(String::class.java) ?: getString(R.string.notification_unknown_alarm)
                    val bitmapLogo = android.graphics.BitmapFactory.decodeResource(applicationContext.resources, R.drawable.logo_ubiguard)

                    val title: String
                    val text: String

                    if (type == "status") {
                        if (value == "Armado") {
                            title = getString(R.string.notification_status_armed_title)
                            text = getString(R.string.notification_status_armed_desc, alarmName)
                        } else {
                            title = getString(R.string.notification_status_disarmed_title)
                            text = getString(R.string.notification_status_disarmed_desc, alarmName)
                        }
                    } else if (type == "pin") {
                        title = getString(R.string.notification_pin_changed_title)
                        text = getString(R.string.notification_pin_changed_desc, alarmName)
                    } else if (type == "offline") {
                        title = getString(R.string.notification_offline_title)
                        text = getString(R.string.notification_offline_desc, alarmName)
                    } else if (type == "online") {
                        title = getString(R.string.notification_online_title)
                        text = getString(R.string.notification_online_desc, alarmName)
                    } else {
                        return
                    }

                    val notification = NotificationCompat.Builder(this@AlarmMonitorService, "UBIGUARD_INFO")
                        .setContentTitle(title)
                        .setContentText(text)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setLargeIcon(bitmapLogo)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build()

                    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                    val notifId = when (type) {
                        "status" -> alarmId.hashCode() + 1
                        "pin" -> alarmId.hashCode() + 2
                        "offline" -> alarmId.hashCode() + 3
                        "online" -> alarmId.hashCode() + 4
                        else -> alarmId.hashCode()
                    }

                    manager.notify(notifId, notification)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun createNotificationChannels() {
        val serviceChannel = NotificationChannel("UBIGUARD_SERVICE", "Monitorização", NotificationManager.IMPORTANCE_LOW)
        val alarmChannel = NotificationChannel("UBIGUARD_ALARM", "Alertas de Disparo", NotificationManager.IMPORTANCE_HIGH)
        val infoChannel = NotificationChannel("UBIGUARD_INFO", getString(R.string.channel_info_name), NotificationManager.IMPORTANCE_DEFAULT)

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(alarmChannel)
        manager.createNotificationChannel(infoChannel)
    }

    private fun monitorWiFi() {
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

        val request = android.net.NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {

            override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                if (networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {

                    val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager

                    @Suppress("DEPRECATION")
                    val wifiInfo = wifiManager.connectionInfo

                    var currentPhoneSSID = wifiInfo.ssid ?: ""
                    currentPhoneSSID = currentPhoneSSID.replace("\"", "")

                    Log.d("UBIGUARD_WIFI", "Detetada ligação. O Android diz que o SSID é: [$currentPhoneSSID]")

                    if (currentPhoneSSID == "UbiGuard_Emergencia") {
                        Log.d("UBIGUARD_WIFI", "Ligado à rede de Emergência Local. O Serviço entra em modo de repouso passivo.")
                        stopRadarMode()
                        return
                    }

                    if (currentPhoneSSID.isNotEmpty() && currentPhoneSSID != "<unknown ssid>") {
                        if (currentPhoneSSID != lastConnectedSSID) {
                            lastConnectedSSID = currentPhoneSSID
                            Log.d("UBIGUARD_WIFI", "Rede válida e nova! A chamar detetive...")
                            autoDisarmAlarms(currentPhoneSSID)
                        } else {
                            Log.d("UBIGUARD_WIFI", "Já estávamos nesta rede, a ignorar para não fazer spam.")
                        }
                    } else {
                        Log.d("UBIGUARD_WIFI", "ERRO: O Android bloqueou a leitura do nome do Wi-Fi!")
                    }
                }
            }

            override fun onLost(network: android.net.Network) {
                super.onLost(network)
                if (lastConnectedSSID.isNotEmpty()) {
                    Log.d("UBIGUARD_WIFI", "Wi-Fi perdido. A iniciar o MODO RADAR para a rede: $lastConnectedSSID")
                    if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        startRadarMode(lastConnectedSSID)
                    }
                }
                lastConnectedSSID = ""
            }
        }

        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    private fun autoDisarmAlarms(phoneSSID: String) {
        val uid = auth.currentUser?.uid ?: return

        database.child("users").child(uid).child("alarms").get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                val alarmId = child.key ?: continue

                database.child("alarms").child(alarmId).child("network").child("ssid").get()
                    .addOnSuccessListener { ssidSnapshot ->
                        val alarmSSID = ssidSnapshot.getValue(String::class.java)

                        Log.d("UBIGUARD_WIFI", "A comparar: Telemóvel=[$phoneSSID] | Firebase=[$alarmSSID]")

                        if (alarmSSID != null && alarmSSID == phoneSSID) {
                            Log.d("UBIGUARD_WIFI", "MATCH PERFEITO! A desarmar o alarme...")
                            stopRadarMode()
                            database.child("alarms").child(alarmId).child("status").setValue("Desarmado")
                            database.child("alarms").child(alarmId).child("isFired").setValue(false)
                        }
                    }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRadarMode(lostSSID: String) {
        if (isRadarActive) return

        val uid = auth.currentUser?.uid ?: return

        database.child("users").child(uid).child("alarms").get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                val alarmId = child.key ?: continue

                database.child("alarms").child(alarmId).child("network").get().addOnSuccessListener { netSnap ->
                    val alarmSSID = netSnap.child("ssid").getValue(String::class.java)
                    val homeLat = netSnap.child("lat").getValue(Double::class.java)
                    val homeLng = netSnap.child("lng").getValue(Double::class.java)

                    if (alarmSSID == lostSSID && homeLat != null && homeLng != null) {

                        Log.d("UBIGUARD_GPS", "Modo Radar ATIVADO! A rastrear movimento de 1 em 1 minuto...")
                        isRadarActive = true

                        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 60000
                        ).setMinUpdateIntervalMillis(30000).build()

                        locationCallback = object : com.google.android.gms.location.LocationCallback() {
                            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                                val location = locationResult.lastLocation ?: return

                                val results = FloatArray(1)
                                Location.distanceBetween(
                                    location.latitude, location.longitude,
                                    homeLat, homeLng, results
                                )

                                val distanceInMeters = results[0]

                                Log.d("UBIGUARD_GPS", "Radar check: Distância atual = ${distanceInMeters.toInt()} metros.")

                                if (distanceInMeters > distanceFromHome) {
                                    Log.d("UBIGUARD_GPS", "Passou o limite de $distanceFromHome metros! A enviar notificação.")

                                    database.child("alarms").child(alarmId).child("status").get().addOnSuccessListener { statusSnap ->
                                        if (statusSnap.getValue(String::class.java) == "Desarmado") {
                                            triggerForgotToArmNotification(alarmId)
                                        }
                                    }
                                    stopRadarMode()
                                }
                            }
                        }

                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
                    }
                }
            }
        }
    }

    private fun stopRadarMode() {
        if (isRadarActive && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback!!)
            isRadarActive = false
            Log.d("UBIGUARD_GPS", "Modo Radar DESATIVADO.")
        }
    }

    private fun triggerForgotToArmNotification(alarmId: String) {
        database.child("alarms").child(alarmId).child("name").get()
            .addOnSuccessListener { snapshot ->
                val alarmName = snapshot.getValue(String::class.java) ?: "Alarme"

                val armIntent = Intent(this, pt.fct.unl.sim.ubiguard.receivers.ArmAlarmReceiver::class.java).apply {
                    putExtra("ALARM_ID", alarmId)
                }

                val pendingArmIntent = android.app.PendingIntent.getBroadcast(
                    this,
                    alarmId.hashCode(),
                    armIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(this, "UBIGUARD_INFO")
                    .setContentTitle(getString(R.string.notification_left_home_title))
                    .setContentText(getString(R.string.notification_left_home_desc, alarmName))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .addAction(android.R.drawable.ic_lock_lock, getString(R.string.action_arm_now), pendingArmIntent)
                    .build()

                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(alarmId.hashCode() + 5, notification)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        alarmListeners.forEach { (id, listener) -> database.child("alarms").child(id).child("isFired").removeEventListener(listener) }
        statusListeners.forEach { (id, listener) -> database.child("alarms").child(id).child("status").removeEventListener(listener) }
        pinListeners.forEach { (id, listener) -> database.child("alarms").child(id).child("pin").removeEventListener(listener) }

        heartbeatListeners.forEach { (id, listener) -> database.child("alarms").child(id).child("heartbeat").removeEventListener(listener) }
        offlineRunnables.forEach { (id, runnable) -> heartbeatHandlers[id]?.removeCallbacks(runnable) }

        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }

        stopRadarMode()
    }
}