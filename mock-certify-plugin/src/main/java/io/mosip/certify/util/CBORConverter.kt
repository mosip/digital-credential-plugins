package io.mosip.certify.util

import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Map
import java.io.ByteArrayInputStream
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.Long
import kotlin.String


class CBORConverter() {

    companion object {
        fun hexToString(hex: String): String {
            val result = StringBuilder()

            for (i in hex.indices step 2) {
                val hexChar = hex.substring(i, i + 2)
                val byte = hexChar.toInt(16).toByte()
                result.append(byte.toChar())
            }

            return result.toString()
        }

        fun toDataItem(value: Any): DataItem {
            return when (value) {
                is DataItem -> value
                is String -> UnicodeString(value)
                is Int -> UnsignedInteger(value.toLong())
                is Long -> UnsignedInteger(value)
                is Boolean -> {
                    if (value) SimpleValue.TRUE else SimpleValue.FALSE
                }

                is kotlin.collections.Map<*, *> -> {
                    val cborMap = Map()
                    value.forEach { (key, value) ->
                        cborMap.put(UnicodeString(key as String), toDataItem(value!!))
                    }
                    cborMap
                }

                is List<*> -> {
                    val cborArray = Array()
                    value.forEach { item ->
                        cborArray.add(toDataItem(item!!))
                    }
                    cborArray
                }

                is Array<*> -> {
                    val cborArray = Array()
                    value.forEach { item ->
                        cborArray.add(toDataItem(item!!))
                    }
                    cborArray
                }

                is ByteArray -> {
                    val byteArrayInputStream = ByteArrayInputStream(value)
                    val dataItems = CborDecoder(byteArrayInputStream).decode()
                    return dataItems.firstOrNull()!!
                }

                else -> throw IllegalArgumentException("Unsupported value: $value ${value.javaClass.simpleName}")
            }
        }

        fun dataItemToMap(dataItem: DataItem): Any? {
            return when (dataItem.majorType) {
                MajorType.MAP -> {
                    val mapDataItem = dataItem as co.nstant.`in`.cbor.model.Map
                    mapDataItem.keys.associate { key ->
                        val keyString = (key as UnicodeString).string
                        keyString to dataItemToMap(mapDataItem[key])
                    }
                }

                MajorType.ARRAY -> {
                    val arrayDataItem = dataItem as co.nstant.`in`.cbor.model.Array
                    arrayDataItem.dataItems.map { dataItemToMap(it) }
                }

                MajorType.UNICODE_STRING -> {
                    (dataItem as UnicodeString).string
                }

                MajorType.BYTE_STRING -> {
                    (dataItem as ByteString).bytes
                }
                /*MajorType.SIMPLE_VALUE -> {
                    val simpleValue = dataItem as SimpleValue
                    when (simpleValue.simpleValueType) {
                        SimpleValueType.FALSE -> false
                        SimpleValueType.TRUE -> true
                        SimpleValueType.NULL -> null
                        SimpleValueType.UNDEFINED -> "undefined"
                    }
                }*/
                MajorType.NEGATIVE_INTEGER -> {
                    (dataItem as co.nstant.`in`.cbor.model.Number).value
                }

                else -> throw IllegalArgumentException("Unsupported data item type: ${dataItem.majorType}")
            }
        }

    }
}


