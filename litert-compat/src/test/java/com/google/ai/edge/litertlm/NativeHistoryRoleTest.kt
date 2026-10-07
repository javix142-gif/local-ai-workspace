package com.google.ai.edge.litertlm

import org.junit.Assert.*
import org.junit.Test

class NativeHistoryRoleTest {
    @Test fun assistantHistorySerializesExactNativeChatMlRoleWithoutWrappingContent() {
        val json = Message.assistant("Hola! <|im_start|> is quoted text").toJson()
        assertEquals("assistant", json.get("role").asString)
        assertEquals("Hola! <|im_start|> is quoted text", json.getAsJsonArray("content")[0].asJsonObject.get("text").asString)
    }

    @Test fun legacyModelAndUserFactoriesKeepTheirExactWireRoles() {
        assertEquals("model", Message.model("reply").toJson().get("role").asString)
        assertEquals("user", Message.user("USER: quoted").toJson().get("role").asString)
    }
}
