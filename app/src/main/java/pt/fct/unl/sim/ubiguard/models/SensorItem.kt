package pt.fct.unl.sim.ubiguard.models

data class SensorItem(
    val name: String,
    val isActivated: Boolean,
    val lastRead: String? = null
)