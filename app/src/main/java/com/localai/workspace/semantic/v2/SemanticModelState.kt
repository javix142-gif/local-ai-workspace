package com.localai.workspace.semantic.v2

import android.content.Context
import java.io.File

enum class SemanticProviderChoice { EG1, EG2 }
/** Uses the existing preferences and keys; no database migration or vector changes. */
class SemanticModelSelection(context:Context, name:String="semantic_v2") {
    private val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
    val dimension get()=prefs.getInt("dimension",768)
    val selectedId get()=prefs.getString("selected",null)
    val choice get()=runCatching { SemanticProviderChoice.valueOf(prefs.getString("provider",if(selectedId==null)"EG1"else"EG2")!!) }.getOrDefault(SemanticProviderChoice.EG1)
    fun select(model:SemanticModelRecord){
        val keys=listOf("selected","provider","backend");val previous=keys.associateWith{prefs.getString(it,null)}
        if(!prefs.edit().putString("selected",model.id).putString("provider","EG2").putString("backend","CPU").commit()){
            prefs.edit().apply{previous.forEach{(key,value)->if(value==null)remove(key)else putString(key,value)}}.commit()
            error("Semantic configuration commit failed")
        }
    }
    fun choose(choice:SemanticProviderChoice){check(prefs.edit().putString("provider",choice.name).commit())}
    fun find(models:List<SemanticModelRecord>):SemanticModelRecord? {
        fun valid(m:SemanticModelRecord)=m.validationStatus in setOf("PROBED_TEXT_NOT_DEVICE_VALIDATED","TEXT_PROBE_PASSED") && File(m.privatePath).isFile
        return models.firstOrNull { it.id==selectedId && valid(it) } ?: models.firstOrNull(::valid)?.also {
            // Repair 0.3.0's registered-without-selected state without overriding explicit EG1 choice.
            check(prefs.edit().putString("selected",it.id).apply { if(!prefs.contains("provider"))putString("provider","EG2") }.commit())
        }
    }
}
object SemanticModelPresentation {
    fun status(installed:Boolean, probePassed:Boolean, engine:ProviderState, indexing:Boolean=false):String = when {
        !installed -> "Not installed"
        indexing -> "Indexing"
        engine==ProviderState.ERROR -> "Error"
        engine in setOf(ProviderState.LOADING,ProviderState.UNLOADING) -> "Loading"
        engine==ProviderState.BUSY -> "Ready"
        probePassed || engine==ProviderState.READY -> "Ready"
        else -> "Installed"
    }
    /** An import failure for another file is history, not health of the already installed model. */
    fun legacyStatus(configured:Boolean,filePresent:Boolean,lastImportFailed:Boolean):String = when {
        configured && filePresent -> "Installed"
        configured || lastImportFailed -> "Error"
        else -> "Not installed"
    }
}
