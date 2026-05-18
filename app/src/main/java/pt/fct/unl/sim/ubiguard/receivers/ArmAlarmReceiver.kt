package pt.fct.unl.sim.ubiguard.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.database.FirebaseDatabase

class ArmAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("ALARM_ID") ?: return

        Log.d("UBIGUARD_RECEIVER", "Botão pressionado! A armar alarme: $alarmId")

        val pendingResult = goAsync()

        val database = FirebaseDatabase.getInstance().reference

        database.child("alarms").child(alarmId).child("status").setValue("Armado")
            .addOnSuccessListener {
                Log.d("UBIGUARD_RECEIVER", "Sucesso! Status alterado para Armado na Nuvem.")

                database.child("alarms").child(alarmId).child("isFired").setValue(false)

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(alarmId.hashCode() + 5)

                pendingResult.finish()
            }
            .addOnFailureListener {
                Log.e("UBIGUARD_RECEIVER", "Erro ao comunicar com a Firebase: ${it.message}")
                pendingResult.finish()
            }
    }
}