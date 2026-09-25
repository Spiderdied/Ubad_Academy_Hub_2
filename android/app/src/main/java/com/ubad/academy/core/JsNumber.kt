package com.ubad.academy.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Serializes a Double the way JSON.stringify does: `3` not `3.0`. */
object JsNumberSerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("JsNumber", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Double) {
        if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) encoder.encodeLong(value.toLong())
        else encoder.encodeDouble(value)
    }
    override fun deserialize(decoder: Decoder): Double = decoder.decodeDouble()
}

fun Double.jsString(): String =
    if (this % 1.0 == 0.0 && kotlin.math.abs(this) < 1e15) toLong().toString() else toString()
