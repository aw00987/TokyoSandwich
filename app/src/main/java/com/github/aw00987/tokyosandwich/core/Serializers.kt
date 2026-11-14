package com.github.aw00987.tokyosandwich.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.YearMonth

/** `java.time.LocalDate` as an ISO-8601 string. */
internal object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** `java.time.YearMonth` as an ISO-8601 string (`2026-05`). */
internal object YearMonthSerializer : KSerializer<YearMonth> {
    override val descriptor = PrimitiveSerialDescriptor("java.time.YearMonth", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: YearMonth) =
        encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): YearMonth = YearMonth.parse(decoder.decodeString())
}
