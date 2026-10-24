package com.anitec.platform.livestock.domain

/** How the animals of a batch are named: the base name the rancher typed, a dash and a three-digit number. */
object BatchNaming {
    /** `nameFor("polloComercio", 7)` is `polloComercio-007`. The server numbers the real animals; this is the preview. */
    fun nameFor(base: String, number: Int): String = "${base.trim()}-${number.toString().padStart(3, '0')}"
}
