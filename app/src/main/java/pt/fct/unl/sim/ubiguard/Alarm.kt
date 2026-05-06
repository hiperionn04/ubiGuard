package pt.fct.unl.sim.ubiguard

data class Alarm(
    val id: String = "",
    val name: String = "",
    val location: String = "",
    val status: String = "Desarmado"
)