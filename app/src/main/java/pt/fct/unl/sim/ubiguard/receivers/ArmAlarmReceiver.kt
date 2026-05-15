package pt.fct.unl.sim.ubiguard.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ArmAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("ALARM_ID") ?: return

        Log.d("UBIGUARD_RECEIVER", "Botão pressionado na Notificação! A armar alarme: $alarmId")

        val pendingResult = goAsync()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(alarmId.hashCode() + 5)

        if (isEmergencyNetwork(context)) {

            Log.d("UBIGUARD_RECEIVER", "Modo Emergência detetado: A enviar HTTP para o ESP32...")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL("http://192.168.4.1/armar")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 3000

                    if (connection.responseCode == 200) {
                        Log.d("UBIGUARD_RECEIVER", "Sucesso Local: Alarme Armado!")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Armado via Wi-Fi Local!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UBIGUARD_RECEIVER", "Erro Local HTTP: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro a contactar o alarme offline.", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    pendingResult.finish()
                }
            }

        } else {

            Log.d("UBIGUARD_RECEIVER", "Modo Online: A enviar para a Firebase...")
            val database = FirebaseDatabase.getInstance().reference

            database.child("alarms").child(alarmId).child("status").setValue("Armado")
                .addOnSuccessListener {
                    Log.d("UBIGUARD_RECEIVER", "Sucesso Firebase: Status alterado para Armado.")
                    database.child("alarms").child(alarmId).child("isFired").setValue(false)
                    Toast.makeText(context, "Alarme Armado com Sucesso!", Toast.LENGTH_SHORT).show()
                    pendingResult.finish()
                }
                .addOnFailureListener {
                    Log.e("UBIGUARD_RECEIVER", "Erro ao comunicar com a Firebase: ${it.message}")
                    Toast.makeText(context, "Sem internet. Não foi possível armar.", Toast.LENGTH_SHORT).show()
                    pendingResult.finish()
                }
        }
    }

    private fun isEmergencyNetwork(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val info = wifiManager.connectionInfo
            val ssidAtual = info.ssid.replace("\"", "")
            ssidAtual == "UbiGuard_Emergencia"
        } catch (e: Exception) {
            false
        }
    }
}