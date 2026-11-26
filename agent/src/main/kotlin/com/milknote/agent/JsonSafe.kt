package com.milknote.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// =====================================================================
// JSON 安全读取(厂商帧容错的地基)
// kotlinx 的 jsonObject / jsonArray / jsonPrimitive 遇到类型不符会抛
// IllegalArgumentException,`"usage": null` / `"content": null` 这类
// 合法 JSON null 会直接把整条流打断(MiMo 实测:finish 帧 usage:null →
// 流被误判 StreamBroken)。统一走 as? 转换,类型不符一律当缺失。
// =====================================================================

internal fun JsonElement?.asObjOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArrOrNull(): JsonArray? = this as? JsonArray

/** JSON null 的 content 是字面量 "null",必须显式排除 */
internal fun JsonElement?.strOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

internal fun JsonObject.objOrNull(key: String): JsonObject? = this[key].asObjOrNull()

internal fun JsonObject.arrOrNull(key: String): JsonArray? = this[key].asArrOrNull()

internal fun JsonObject.strOrNull(key: String): String? = this[key].strOrNull()

internal fun JsonObject.intOrNull(key: String): Int? = strOrNull(key)?.toIntOrNull()

internal fun JsonObject.boolOrNull(key: String): Boolean? = strOrNull(key)?.toBooleanStrictOrNull()

/** 非空字符串(空串视同缺失;思考/正文增量帧常用 "") */
internal fun JsonObject.nonEmptyStr(key: String): String? = strOrNull(key)?.takeIf { it.isNotEmpty() }
