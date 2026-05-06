package pt.fct.unl.sim.ubiguard

data class AccessItem(
    val uid: String,
    val email: String,
    val isChild: Boolean,
    val expiry: String?
)