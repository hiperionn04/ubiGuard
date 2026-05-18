package pt.fct.unl.sim.ubiguard

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class UbiGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseDatabase.getInstance(DATABASE_URL).setPersistenceEnabled(true)
    }

    companion object {
        const val DATABASE_URL = "https://ubiguard-1a3d2-default-rtdb.europe-west1.firebasedatabase.app"
    }
}
