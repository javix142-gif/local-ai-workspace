package com.localai.workspace.semantic.v2

import android.content.Context
import com.google.gson.JsonParser
import java.io.File

data class SemanticValidationSummary(val status:String,val passed:Int,val total:Int,val savedAt:Long) {
    companion object {
        fun read(context:Context,modelHash:String?):SemanticValidationSummary? = runCatching {
            val file=File(context.filesDir,"semantic-v2-reports/latest.json");if(!file.isFile || modelHash==null)return null
            val report=JsonParser.parseString(file.readText()).asJsonObject;val cases=report.getAsJsonArray("cases")
            val hash=cases.firstOrNull{it.asJsonObject.get("id").asString=="eg2_import"}?.asJsonObject?.getAsJsonObject("metrics")?.get("sha256")?.asString
            if(hash!=modelHash)return null
            val passed=cases.count{it.asJsonObject.get("status").asString=="PASS"}
            SemanticValidationSummary(report.get("overall").asString,passed,cases.size(),file.lastModified())
        }.getOrNull()
    }
}
