package pt.fct.unl.sim.ubiguard

data class SensorItem(
    val name: String,
    val isActivated: Boolean,
    val lastRead: String? = null
)