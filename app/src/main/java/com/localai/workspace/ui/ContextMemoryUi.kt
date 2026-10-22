package com.localai.workspace.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.localai.workspace.LocalAiApplication
import com.localai.workspace.R
import com.localai.workspace.context.*
import com.localai.workspace.context.ContextBundle
import com.localai.workspace.semantic.v2.*
import com.localai.workspace.ui.theme.LocalAiSpacing
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.google.gson.GsonBuilder

private fun memoryBundleExportError(context:Context,failure:Exception):String = when(failure) {
    is MemoryBundleV1.InvalidPackage -> when(failure.reason) {
        "SENSITIVE_MEMORY_REQUIRES_REVIEW" -> context.getString(R.string.memory_export_sensitive_error)
        "TOO_MANY_RECORDS" -> context.getString(R.string.memory_export_record_limit_error)
        "EXPORT_SIZE_LIMIT_EXCEEDED", "ARCHIVE_TOO_LARGE" -> context.getString(R.string.memory_export_size_error)
        else -> context.getString(R.string.memory_error_generic)
    }
    else -> context.getString(R.string.memory_error_generic)
}

internal fun memoryActionErrorMessage(context:Context,failure:Exception):String = when(failure.message) {
    "PINNED_PREFERENCE_LIMIT_EXCEEDED" -> context.getString(R.string.memory_pin_limit_error)
    "STRUCTURED_PREFERENCE_CONFLICT" -> context.getString(R.string.memory_structured_conflict_error)
    "STRUCTURED_PREFERENCE_CONFLICT_CHANGED" -> context.getString(R.string.memory_structured_conflict_changed)
    "PIN_APPROVAL_REQUIRES_EXPLICIT_CONFIRMATION" -> context.getString(R.string.memory_pin_approval_confirmation_required)
    "SECRET_CANDIDATE_REJECTED", "SENSITIVE_MEMORY_REQUIRES_REMOVAL" -> context.getString(R.string.memory_sensitive_blocked)
    "MEMORY_PACKAGE_UNREADABLE", "MEMORY_EXPORT_DESTINATION_UNAVAILABLE" -> context.getString(R.string.memory_file_access_error)
    "STRUCTURED_PREFERENCE_FIELDS_REQUIRED" -> context.getString(R.string.memory_structured_fields_required)
    "EXPIRED_MEMORY_CANNOT_APPROVE" -> context.getString(R.string.memory_expired_cannot_approve)
    "MEMORY_SCOPE_NOT_ALLOWED" -> context.getString(R.string.memory_scope_approval_denied)
    else -> context.getString(R.string.memory_error_generic)
}

private fun memoryBundleImportError(context:Context,failure:Exception):String = when(failure) {
    is MemoryBundleV1.InvalidPackage -> context.getString(R.string.memory_invalid_package)
    else -> memoryActionErrorMessage(context,failure)
}

private data class MemoryApprovalReview(
    val ids:List<String>,
    val pinnedCandidateIds:List<String>,
    val approveWithoutPinIds:Set<String> = emptySet(),
    val projectedCharacters:Int,
    val maximumCharacters:Int,
    val stillOverBudget:Boolean = false,
)

@Composable private fun memoryKindLabel(kind:MemoryKind):String=stringResource(when(kind){
    MemoryKind.PREFERENCE->R.string.memory_kind_preference
    MemoryKind.PROCEDURE->R.string.memory_kind_procedure
    MemoryKind.FACT->R.string.memory_kind_fact
    MemoryKind.DECISION->R.string.memory_kind_decision
    MemoryKind.EVENT->R.string.memory_kind_event
    MemoryKind.RELATIONSHIP->R.string.memory_kind_relationship
    MemoryKind.PROJECT_CONTEXT->R.string.memory_kind_project_context
    MemoryKind.SUMMARY->R.string.memory_kind_summary
    MemoryKind.TASK_STATE->R.string.memory_kind_task_state
    MemoryKind.OTHER->R.string.memory_kind_other
})
@Composable private fun memoryScopeLabel(scope:String):String=stringResource(when(scope){
    "PROJECT"->R.string.memory_scope_project
    "GLOBAL"->R.string.memory_scope_global
    "SESSION"->R.string.memory_scope_session
    else->R.string.memory_scope_user
})
@Composable private fun memoryStatusLabel(status:String):String=stringResource(when(status){
    "ACTIVE"->R.string.memory_status_active
    "PENDING"->R.string.memory_status_pending
    "DELETED"->R.string.memory_status_deleted
    "SUPERSEDED"->R.string.memory_status_superseded
    "EXPIRED"->R.string.memory_status_expired
    else->R.string.memory_status_inactive
})

@Composable fun ContextMemoryEntry(projectId:String?=null,initiallyOpen:Boolean=false) {
    var open by remember(initiallyOpen){mutableStateOf(initiallyOpen)}
    if(!initiallyOpen)TextButton(onClick={open=true}, modifier=Modifier.testTag("context-memory-entry-open")){Text(stringResource(R.string.memory_title))}
    if(open) StructuredMemoryPanel(projectId,fullScreen=initiallyOpen){open=false}
}
@Composable private fun StructuredMemoryPanel(projectId:String?,fullScreen:Boolean=false,close:()->Unit) {
    val context=LocalContext.current;val graph=(context.applicationContext as LocalAiApplication).graph;val foundation=graph.contextFoundation;val scope=rememberCoroutineScope()
    val all by foundation.memory.items.collectAsState(initial=emptyList());val access=ScopeAccess(projectId=projectId)
    var query by remember{mutableStateOf("")};var showInactive by remember{mutableStateOf(false)};var projectOnly by remember{mutableStateOf(projectId!=null)};var kindFilter by remember{mutableStateOf<MemoryKind?>(null)};var filters by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<MemoryRecord?>(null)};var editor by remember{mutableStateOf(false)};var text by remember{mutableStateOf("")};var ttl by remember{mutableStateOf("")};var kind by remember{mutableStateOf(MemoryKind.FACT)};var choosingKind by remember{mutableStateOf(false)};var scopeMenu by remember{mutableStateOf(false)};var writingScope by remember(projectId){mutableStateOf(projectId?.let{SemanticScope(ScopeType.PROJECT,it)} ?: SemanticScope(ScopeType.USER,"local"))}
    var structuredSubject by remember{mutableStateOf("")};var structuredPredicate by remember{mutableStateOf("")};var structuredObject by remember{mutableStateOf("")};var structuredConflict by remember{mutableStateOf<StructuredPreferenceConflictException?>(null)}
    var details by remember{mutableStateOf<MemoryRecord?>(null)};var removal by remember{mutableStateOf<MemoryRecord?>(null)};var permanentRemoval by remember{mutableStateOf<MemoryRecord?>(null)};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)};var brief by remember{mutableStateOf<ProjectBrief?>(null)}
    var implementDialog by remember{mutableStateOf(false)};var bundlePreview by remember{mutableStateOf<MemoryBundleV1.Preview?>(null)};var selectedBundleKeys by remember{mutableStateOf<Set<String>>(emptySet())};var importResult by remember{mutableStateOf<String?>(null)}
    var approvalReview by remember{mutableStateOf<MemoryApprovalReview?>(null)}
    var exportAllWorkspaceMemory by remember{mutableStateOf(false)}
    var bundleReview by remember{mutableStateOf<Map<String,MemoryManager.BundleCandidateReview>>(emptyMap())}
    LaunchedEffect(foundation){
        try { foundation.migrateLegacyMemories() }
        catch(cancel:CancellationException){throw cancel}
        catch(failure:Exception){error=context.getString(R.string.memory_legacy_migration_error,failure.message ?: failure.javaClass.simpleName)}
    }
    fun run(block:suspend()->Unit){scope.launch{busy=true;error=null;try{withContext(Dispatchers.IO){block()}}catch(cancel:CancellationException){throw cancel}catch(e:Exception){error=memoryActionErrorMessage(context,e)}finally{busy=false}}}
    val pendingForScope=all.filter{it.status=="PENDING"&&access.permits(it.scopeType,it.scopeId)}
        .sortedWith(compareByDescending<MemoryRecord>{it.pinned}.thenByDescending{it.importance}.thenByDescending{it.updatedAt}.thenBy{it.id})
    val matching=all.filter{access.permits(it.scopeType,it.scopeId)&&(!projectOnly||it.scopeType=="PROJECT")&&(showInactive||it.status in setOf("ACTIVE","PENDING")&&it.expiresAt?.let{t->t>System.currentTimeMillis()}!=false)&&(kindFilter==null||it.kind==kindFilter?.name)&&it.text.contains(query,true)}
    val visible=matching.sortedWith(
        compareByDescending<MemoryRecord>{it.status=="PENDING"}
            .thenByDescending{it.pinned}
            .thenByDescending{it.updatedAt}
            .thenBy{it.id},
    ).take(100)
    val visiblePending=visible.filter{it.status=="PENDING"}
    val visiblePinned=visible.filter{it.status!="PENDING"&&it.status=="ACTIVE"&&it.pinned}
    val categorized=visible.filterNot{it.status=="PENDING"||it in visiblePinned}
    val visibleKinds=MemoryKind.entries.mapNotNull{memoryKind->
        categorized.filter{record->(MemoryKind.entries.firstOrNull{it.name==record.kind}?:MemoryKind.OTHER)==memoryKind}
            .takeIf{it.isNotEmpty()}?.let{memoryKind to it}
    }
    fun requestApproval(ids:List<String>){scope.launch{
        busy=true;error=null
        try {
            when(val result=withContext(Dispatchers.IO){foundation.memory.approveBatch(ids,access)}){
                is MemoryManager.ApprovalBatchResult.Approved -> importResult=context.getString(R.string.memory_approved_without_pin,result.ids.size,result.approvedWithoutPinIds.size)
                is MemoryManager.ApprovalBatchResult.RequiresPinChoice -> approvalReview=MemoryApprovalReview(
                    ids=result.ids,pinnedCandidateIds=result.pinnedCandidateIds,
                    projectedCharacters=result.projectedCharacters,maximumCharacters=result.maximumCharacters,
                )
            }
        } catch(cancel:CancellationException){throw cancel}
        catch(failure:Exception){error=memoryActionErrorMessage(context,failure)}
        finally{busy=false}
    }}
    fun confirmApprovalReview(){
        val review=approvalReview ?: return
        scope.launch{
            busy=true;error=null
            try {
                when(val result=withContext(Dispatchers.IO){
                    foundation.memory.approveBatch(review.ids,access,review.approveWithoutPinIds,confirmed=true)
                }){
                    is MemoryManager.ApprovalBatchResult.Approved -> {
                        approvalReview=null;details=null
                        importResult=context.getString(R.string.memory_approved_without_pin,result.ids.size,result.approvedWithoutPinIds.size)
                    }
                    is MemoryManager.ApprovalBatchResult.RequiresPinChoice -> approvalReview=review.copy(
                        pinnedCandidateIds=result.pinnedCandidateIds,
                        projectedCharacters=result.projectedCharacters,
                        maximumCharacters=result.maximumCharacters,
                        stillOverBudget=true,
                    )
                }
            } catch(cancel:CancellationException){throw cancel}
            catch(failure:Exception){error=memoryActionErrorMessage(context,failure)}
            finally{busy=false}
        }
    }
    fun saveCurrentMemory(replaceConflictId:String?=null){scope.launch{
        busy=true;error=null
        try {
            val expires=ttl.takeIf{it.isNotBlank()}?.let{val days=it.toLong();require(days in 1..36500);System.currentTimeMillis()+days*86_400_000}
            withContext(Dispatchers.IO){
                val old=editing
                if(old!=null)foundation.memory.update(old.id,text,expires)
                else if(kind==MemoryKind.PREFERENCE&&listOf(structuredSubject,structuredPredicate,structuredObject).any{it.isNotBlank()}){
                    require(listOf(structuredSubject,structuredPredicate,structuredObject).all{it.isNotBlank()}){"STRUCTURED_PREFERENCE_FIELDS_REQUIRED"}
                    foundation.memory.createStructuredPreference(text,writingScope,structuredSubject.trim(),structuredPredicate.trim(),structuredObject.trim(),expiresAt=expires,replaceConflictId=replaceConflictId)
                } else foundation.memory.create(text,writingScope,kind,expiresAt=expires)
            }
            structuredConflict=null;editor=false
        } catch(cancel:CancellationException){throw cancel}
        catch(conflict:StructuredPreferenceConflictException){structuredConflict=conflict}
        catch(failure:Exception){error=memoryActionErrorMessage(context,failure)}
        finally{busy=false}
    }}
    val memoryBundleExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->
        if(uri!=null)scope.launch {
            busy=true;error=null
            try {
                val exportAll=exportAllWorkspaceMemory
                val exported=withContext(Dispatchers.IO){if(exportAll)foundation.memory.exportAllWorkspaceMemories()else foundation.memory.exportBundle(access)}
                withContext(Dispatchers.IO){
                    val output=context.contentResolver.openOutputStream(uri,"w") ?: throw IllegalStateException("MEMORY_EXPORT_DESTINATION_UNAVAILABLE")
                    output.use{it.write(exported.archive)}
                }
                importResult=context.getString(R.string.memory_export_result,exported.exportedRecords,exported.excludedSensitiveRecords)
                implementDialog=false
            } catch(cancel:CancellationException){throw cancel} catch(e:Exception){error=memoryBundleExportError(context,e)}
            finally{busy=false;exportAllWorkspaceMemory=false}
        }
    }
    val memoryJsonExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null)scope.launch {
            busy=true;error=null
            try {
                val exported=withContext(Dispatchers.IO){foundation.memory.exportJson(access)}
                withContext(Dispatchers.IO){
                    val output=context.contentResolver.openOutputStream(uri,"w") ?: throw IllegalStateException("MEMORY_EXPORT_DESTINATION_UNAVAILABLE")
                    output.use{it.write(exported.json.toByteArray(Charsets.UTF_8))}
                }
                importResult=context.getString(R.string.memory_export_json_result,exported.exportedRecords,exported.excludedSensitiveRecords)
            } catch(cancel:CancellationException){throw cancel} catch(e:Exception){error=memoryActionErrorMessage(context,e)}
            finally{busy=false}
        }
    }
    val memoryBundlePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)scope.launch {
            busy=true;error=null
            try {
                val (parsed,review)=withContext(Dispatchers.IO){
                    val preview=context.contentResolver.openInputStream(uri)?.use(MemoryBundleV1::read) ?: throw IllegalStateException("MEMORY_PACKAGE_UNREADABLE")
                    preview to foundation.memory.inspectBundle(preview)
                }
                bundlePreview=parsed
                bundleReview=review
                selectedBundleKeys=emptySet()
                implementDialog=false
                importResult=null
            } catch(cancel:CancellationException){throw cancel} catch(e:Exception){error=memoryBundleImportError(context,e)}
            finally{busy=false}
        }
    }
    SemanticPanel(stringResource(R.string.memory_title),close,fullScreen=fullScreen) {
        if(fullScreen)Text(stringResource(R.string.memory_intro),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query,{query=it},label={Text(stringResource(R.string.memory_search))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("memory-search"))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={editing=null;text="";ttl="";structuredSubject="";structuredPredicate="";structuredObject="";kind=MemoryKind.FACT;editor=true},modifier=Modifier.weight(1f)){Text(stringResource(R.string.common_add))}
            TextButton(onClick={filters=!filters},modifier=Modifier.weight(1f).testTag("memory-filter-toggle")){Text(stringResource(R.string.memory_filter))}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={memoryJsonExporter.launch("LOCAL_AI_WORKSPACE_MEMORY.json")},modifier=Modifier.weight(1f)){Text(stringResource(R.string.memory_export_json))}
            TextButton(onClick={implementDialog=true},modifier=Modifier.weight(1f)){Text(stringResource(R.string.memory_import_export))}
        }
        if(visibleKinds.isNotEmpty())Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("memory-category-filters"),
            horizontalArrangement=Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected=kindFilter==null,onClick={kindFilter=null},label={Text(stringResource(R.string.memory_all_types))},modifier=Modifier.testTag("memory-category-all"))
            visibleKinds.forEach{(category,records)->
                FilterChip(
                    selected=kindFilter==category,onClick={kindFilter=if(kindFilter==category)null else category},
                    label={Text(stringResource(R.string.memory_category_count,memoryKindLabel(category),records.size))},
                    modifier=Modifier.testTag("memory-category-${category.name.lowercase()}"),
                )
            }
        }
        if(filters){
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(showInactive,{showInactive=it});Text(stringResource(R.string.memory_include_inactive))}
            if(projectId!=null)Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(projectOnly,{projectOnly=it});Text(stringResource(R.string.memory_project_only))}
        }
        if(pendingForScope.isNotEmpty())TextButton(
            enabled=!busy,onClick={requestApproval(pendingForScope.map{it.id})},modifier=Modifier.testTag("memory-review-all-pending"),
        ){Text(stringResource(R.string.memory_pending_approval_count,pendingForScope.size))}
        if(visiblePending.isNotEmpty()){
            MemorySectionHeading(stringResource(R.string.memory_pending_section,visiblePending.size),"memory-pending-section")
            visiblePending.forEach{memory->MemoryRecordCard(
                memory=memory,showStatus=true,onDetails={details=memory},
                onApprove={requestApproval(listOf(memory.id))},onDiscard={removal=memory},
            )}
        }
        if(visiblePinned.isNotEmpty()){
            MemorySectionHeading(stringResource(R.string.memory_pinned_section,visiblePinned.size),"memory-pinned-section")
            visiblePinned.forEach{memory->MemoryRecordCard(memory=memory,showStatus=false,onDetails={details=memory})}
        }
        visibleKinds.forEach{(category,records)->
            MemorySectionHeading(stringResource(R.string.memory_category_section,memoryKindLabel(category),records.size),"memory-kind-${category.name.lowercase()}")
            records.forEach{memory->MemoryRecordCard(
                memory=memory,showStatus=true,onDetails={details=memory},
                onApprove=if(memory.status=="PENDING")({requestApproval(listOf(memory.id))})else null,
                onDiscard=if(memory.status=="PENDING")({removal=memory})else null,
            )}
        }
        if(matching.isEmpty())Text(stringResource(R.string.memory_empty),modifier=Modifier.testTag("memory-empty-state"),style=MaterialTheme.typography.bodyMedium)
        if(matching.size>100)Text(stringResource(R.string.memory_showing_limit,matching.size))
        TextButton(onClick={run{foundation.memory.recover()}},modifier=Modifier.testTag("memory-rebuild-embeddings")){Text(stringResource(R.string.memory_rebuild_embeddings))}
        importResult?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
        if(projectId!=null)TextButton(onClick={run{brief=foundation.database.dao().brief(projectId) ?: ProjectBrief(projectId,graph.database.projectDao().get(projectId)?.name.orEmpty())}}){Text(stringResource(R.string.memory_project_brief))}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    if(implementDialog) AlertDialog(
        onDismissRequest={implementDialog=false},
        title={Text(stringResource(R.string.memory_implement))},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(stringResource(R.string.memory_prepare_portable))
            TextButton(onClick={context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("AWS memory import prompt",MemoryBundleV1.copyPrompt()));implementDialog=false;importResult=context.getString(R.string.memory_prompt_copied)}){Text(stringResource(R.string.memory_copy_prompt))}
            TextButton(enabled=!busy,onClick={exportAllWorkspaceMemory=false;memoryBundleExporter.launch("AWS_MEMORY_IMPORT.zip")}){Text(stringResource(R.string.memory_export_scope_zip))}
            if(projectId==null)TextButton(enabled=!busy,onClick={exportAllWorkspaceMemory=true;memoryBundleExporter.launch("AWS_MEMORY_IMPORT_ALL_PROJECTS.zip")}){Text(stringResource(R.string.memory_export_all_zip))}
            Text(stringResource(R.string.memory_zip_scope_note),style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=!busy,onClick={memoryBundlePicker.launch(arrayOf("application/zip","application/octet-stream","*/*"))}){Text(stringResource(R.string.memory_import_zip))}
            Text(stringResource(R.string.memory_import_zip_note),style=MaterialTheme.typography.bodySmall)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }},
        confirmButton={TextButton(onClick={implementDialog=false}){Text(stringResource(R.string.common_close))}}
    )
    bundlePreview?.let{preview->MemoryBundleReviewDialog(
        preview=preview,selectedKeys=selectedBundleKeys,onSelectionChange={selectedBundleKeys=it},review= bundleReview,busy=busy,
        onCandidateEdit={key,content->
            val candidate=preview.candidates.first{it.key==key}
            val updated=candidate.copy(content=content.trim())
            val edited=preview.copy(candidates=preview.candidates.map{if(it.key==key)updated.copy(sensitiveCandidate=MemoryBundleV1.isSensitive(updated))else it})
            bundlePreview=edited
            if(MemoryBundleV1.isSensitive(updated))selectedBundleKeys=selectedBundleKeys-key else selectedBundleKeys=selectedBundleKeys+key
            scope.launch{bundleReview=withContext(Dispatchers.IO){foundation.memory.inspectBundle(edited)}}
        },
        onDismiss={bundlePreview=null},onImport={
            run {
                val availableProjects=graph.database.projectDao().observeActive().first().map{it.id}.toSet()
                val result=foundation.memory.importBundlePending(preview,selectedBundleKeys,availableProjects)
                withContext(Dispatchers.Main){
                    importResult=context.getString(R.string.memory_imported_count,result.imported,result.duplicates,result.missingProjectFallbackCount)
                    if(result.imported>0){showInactive=true;projectOnly=false}
                    bundlePreview=null;bundleReview=emptyMap()
                }
            }
        },
    )}
    if(editor)SemanticPanel(stringResource(if(editing==null)R.string.memory_editor_add else R.string.memory_editor_edit),{editor=false}) {
        OutlinedTextField(text,{text=it},label={Text(stringResource(R.string.memory_content))},modifier=Modifier.fillMaxWidth(),minLines=3)
        if(MemoryContent.secret(text))Text(stringResource(R.string.memory_secret_warning),color=MaterialTheme.colorScheme.error)
        if(editing==null){Box{TextButton(onClick={choosingKind=true}){Text(memoryKindLabel(kind))};DropdownMenu(choosingKind,{choosingKind=false}){MemoryKind.entries.forEach{k->DropdownMenuItem(text={Text(memoryKindLabel(k))},onClick={kind=k;choosingKind=false})}}};if(kind==MemoryKind.PREFERENCE){Text(stringResource(R.string.memory_structured_fields_note),style=MaterialTheme.typography.bodySmall);OutlinedTextField(structuredSubject,{structuredSubject=it},label={Text(stringResource(R.string.memory_subject))},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(structuredPredicate,{structuredPredicate=it},label={Text(stringResource(R.string.memory_rule))},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(structuredObject,{structuredObject=it},label={Text(stringResource(R.string.memory_value))},singleLine=true,modifier=Modifier.fillMaxWidth())}}
        if(editing!=null)Text(stringResource(R.string.memory_scope,memoryScopeLabel(editing!!.scopeType))) else Box {
            TextButton(onClick={scopeMenu=true}){Text(stringResource(R.string.memory_scope,memoryScopeLabel(writingScope.type.name)))}
            DropdownMenu(scopeMenu,{scopeMenu=false}) {
                DropdownMenuItem(text={Text(stringResource(R.string.memory_user_scope))},onClick={writingScope=SemanticScope(ScopeType.USER,"local");scopeMenu=false})
                DropdownMenuItem(text={Text(stringResource(R.string.memory_global_scope))},onClick={writingScope=SemanticScope(ScopeType.GLOBAL);scopeMenu=false})
                if(projectId!=null)DropdownMenuItem(text={Text(stringResource(R.string.memory_this_project))},onClick={writingScope=SemanticScope(ScopeType.PROJECT,projectId);scopeMenu=false})
            }
        }
        OutlinedTextField(ttl,{ttl=it},label={Text(stringResource(R.string.memory_expiration_days))},singleLine=true)
        Button(enabled=text.isNotBlank()&&!busy,onClick={saveCurrentMemory()}){Text(stringResource(R.string.common_save))};error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    structuredConflict?.let{conflict->
        val existing=conflict.conflictingIds.singleOrNull()?.let{id->all.firstOrNull{it.id==id}}
        AlertDialog(
            onDismissRequest={structuredConflict=null},
            title={Text(stringResource(R.string.memory_replace_conflict_title))},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(stringResource(if(existing==null)R.string.memory_conflict_multiple else R.string.memory_conflict_single))
                existing?.let{Text(stringResource(R.string.memory_current_value,it.text))}
                Text(stringResource(R.string.memory_proposed_value,text))
                Text(stringResource(R.string.memory_replace_confirm_note),style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={TextButton(enabled=existing!=null&&!busy,onClick={saveCurrentMemory(requireNotNull(existing).id)}){Text(stringResource(R.string.common_replace))}},
            dismissButton={TextButton(onClick={structuredConflict=null}){Text(stringResource(R.string.memory_keep_current))}},
        )
    }
    details?.let{m->SemanticPanel(stringResource(R.string.memory_details),{details=null}){Text(m.text);Text(stringResource(R.string.memory_detail_metadata,memoryKindLabel(MemoryKind.entries.firstOrNull{it.name==m.kind}?:MemoryKind.OTHER),memoryStatusLabel(m.status),m.version,m.sourceType,m.sourceId?:stringResource(R.string.memory_default_none),m.sourceMessageId?:stringResource(R.string.memory_default_none),m.indexState,m.expiresAt?.let{java.util.Date(it).toString()}?:stringResource(R.string.memory_default_never)))
        if(m.status in setOf("ACTIVE","PENDING")){TextButton(onClick={editing=m;text=m.text;ttl=m.expiresAt?.let{((it-System.currentTimeMillis())/86_400_000).coerceAtLeast(1).toString()}.orEmpty();details=null;editor=true}){Text(stringResource(R.string.common_edit))};TextButton(onClick={run{foundation.memory.pin(m.id,access);details=null}}){Text(stringResource(if(m.pinned)R.string.memory_unpin else R.string.memory_pin))};if(m.status=="PENDING")TextButton(onClick={requestApproval(listOf(m.id))}){Text(stringResource(R.string.memory_approve))}}
        TextButton(onClick={removal=m;details=null}){Text(stringResource(R.string.common_delete))}
        TextButton(onClick={permanentRemoval=m;details=null}){Text(stringResource(R.string.memory_forget_permanently))}
    }}
    approvalReview?.let{review->AlertDialog(
        onDismissRequest={if(!busy)approvalReview=null},
        title={Text(stringResource(R.string.memory_approval_pin_title))},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())){
            Text(stringResource(R.string.memory_approval_pin_over_budget))
            Text(stringResource(R.string.memory_approval_pin_choose),style=MaterialTheme.typography.bodySmall)
            review.pinnedCandidateIds.forEach{id->
                val memory=all.firstOrNull{it.id==id} ?: return@forEach
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    Checkbox(checked=id in review.approveWithoutPinIds,enabled=!busy,modifier=Modifier.testTag("memory-approve-without-pin-$id"),onCheckedChange={checked->
                        approvalReview=review.copy(approveWithoutPinIds=if(checked)review.approveWithoutPinIds+id else review.approveWithoutPinIds-id,stillOverBudget=false)
                    })
                    Text(memory.title?.takeIf{it.isNotBlank()} ?: memory.text,Modifier.weight(1f),maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Text(stringResource(R.string.memory_projected_pin_budget,review.projectedCharacters,review.maximumCharacters),style=MaterialTheme.typography.bodySmall)
            if(review.stillOverBudget)Text(stringResource(R.string.memory_approval_still_over),color=MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.memory_approval_provenance),style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={TextButton(enabled=!busy&&review.approveWithoutPinIds.isNotEmpty(),onClick={confirmApprovalReview()}){Text(stringResource(if(busy)R.string.memory_saving else R.string.memory_confirm_selection))}},
        dismissButton={TextButton(enabled=!busy,onClick={approvalReview=null}){Text(stringResource(R.string.common_cancel))}},
    )}
    removal?.let{m->AlertDialog(onDismissRequest={removal=null},title={Text(stringResource(R.string.memory_delete_title))},text={Text(stringResource(R.string.memory_delete_note))},confirmButton={TextButton(onClick={run{foundation.memory.delete(m.id);removal=null;details=null}}){Text(stringResource(R.string.common_delete))}},dismissButton={TextButton(onClick={removal=null}){Text(stringResource(R.string.common_cancel))}})}
    permanentRemoval?.let{m->AlertDialog(onDismissRequest={permanentRemoval=null},title={Text(stringResource(R.string.memory_forget_title))},text={Text(stringResource(R.string.memory_forget_note))},confirmButton={TextButton(onClick={run{foundation.memory.forgetPermanently(m.id);permanentRemoval=null}}){Text(stringResource(R.string.memory_forget_permanently))}},dismissButton={TextButton(onClick={permanentRemoval=null}){Text(stringResource(R.string.common_cancel))}})}
    brief?.let{initial->var b by remember(initial){mutableStateOf(initial)};SemanticPanel(stringResource(R.string.memory_project_brief),{brief=null}){val labels=listOf(R.string.memory_brief_goal,R.string.memory_brief_state,R.string.memory_brief_constraints,R.string.memory_brief_decisions,R.string.memory_brief_tasks,R.string.memory_brief_architecture);listOf(b.goal,b.currentState,b.constraints,b.keyDecisions,b.activeTasks,b.architecture).forEachIndexed{i,value->OutlinedTextField(value,{v->b=when(i){0->b.copy(goal=v);1->b.copy(currentState=v);2->b.copy(constraints=v);3->b.copy(keyDecisions=v);4->b.copy(activeTasks=v);else->b.copy(architecture=v)}},label={Text(stringResource(labels[i]))},modifier=Modifier.fillMaxWidth())};Button(onClick={run{foundation.saveBrief(b);brief=null}}){Text(stringResource(R.string.common_save))}}}
}

@Composable
private fun MemorySectionHeading(text:String,tag:String) {
    Text(
        text,
        Modifier.fillMaxWidth().semantics{heading()}.testTag(tag),
        style=MaterialTheme.typography.titleMedium,
        color=MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun MemoryRecordCard(
    memory:MemoryRecord,
    showStatus:Boolean,
    onDetails:()->Unit,
    onApprove:(()->Unit)?=null,
    onDiscard:(()->Unit)?=null,
) {
    val kind=MemoryKind.entries.firstOrNull{it.name==memory.kind}?:MemoryKind.OTHER
    Card(
        modifier=Modifier.fillMaxWidth().testTag("memory-record-${memory.id}"),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text(memoryKindLabel(kind),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                if(memory.pinned)Text(stringResource(R.string.memory_pinned_marker).trim(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                if(showStatus)Text(memoryStatusLabel(memory.status),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val title=memory.title?.takeIf{it.isNotBlank()}
            Text(title?:memory.text,style=MaterialTheme.typography.titleSmall,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if(title!=null)Text(memory.text,style=MaterialTheme.typography.bodyMedium,maxLines=4,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                stringResource(
                    R.string.memory_scope_status,
                    memoryKindLabel(kind),
                    memoryScopeLabel(memory.scopeType),
                    if(memory.pinned)stringResource(R.string.memory_pinned_marker)else"",
                    memoryStatusLabel(memory.status),
                ),
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if(onApprove!=null&&onDiscard!=null){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=onDiscard,modifier=Modifier.weight(1f).testTag("memory-discard-${memory.id}")){Text(stringResource(R.string.memory_discard))}
                    Button(onClick=onApprove,modifier=Modifier.weight(1f).testTag("memory-approve-${memory.id}")){Text(stringResource(R.string.memory_approve))}
                }
            }
            TextButton(onClick=onDetails,modifier=Modifier.testTag("memory-details-${memory.id}")){Text(stringResource(R.string.memory_details))}
        }
    }
}

@Composable
private fun MemoryBundleReviewDialog(
    preview:MemoryBundleV1.Preview,
    selectedKeys:Set<String>,
    onSelectionChange:(Set<String>)->Unit,
    review:Map<String,MemoryManager.BundleCandidateReview>,
    busy:Boolean,
    onCandidateEdit:(String,String)->Unit,
    onDismiss:()->Unit,
    onImport:()->Unit,
) {
    var detailKey by remember(preview.bundleId){mutableStateOf<String?>(null)}
    val reviewed= remember(preview.bundleId){mutableStateListOf<String>()}
    AlertDialog(
        onDismissRequest={if(!busy)onDismiss()},
        title={Text(stringResource(R.string.memory_review_package))},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(stringResource(R.string.memory_bundle_source,preview.sourceLabel.ifBlank{stringResource(R.string.memory_default_unknown)},preview.exportedAt),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.memory_bundle_candidate_count,preview.candidates.size,selectedKeys.size))
            Text(stringResource(R.string.memory_bundle_metadata_note),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.memory_package_metadata),style=MaterialTheme.typography.labelLarge)
            Text(preview.metadata.take(1600),maxLines=8,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max=420.dp)) {
                items(preview.candidates,key={it.key}){candidate->
                    Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=androidx.compose.ui.Alignment.Top) {
                            Checkbox(checked=candidate.key in selectedKeys,enabled=!busy&&!candidate.sensitiveCandidate&&candidate.key in reviewed,onCheckedChange={checked->onSelectionChange(if(checked)selectedKeys+candidate.key else selectedKeys-candidate.key)})
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.memory_bundle_kind_trust,memoryKindLabel(candidate.kind),candidate.trust,if(candidate.sensitiveCandidate)stringResource(R.string.memory_possible_secret)else""),style=MaterialTheme.typography.labelLarge)
                                Text(candidate.content,maxLines=3,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                                review[candidate.key]?.let{status->
                                    if(status.exactDuplicate)Text(stringResource(R.string.memory_exact_duplicate),style=MaterialTheme.typography.bodySmall)
                                    if(status.possibleContradiction)Text(stringResource(R.string.memory_possible_overlap),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                                }
                                TextButton(enabled=!busy,onClick={detailKey=candidate.key}){Text(stringResource(if(candidate.key in reviewed)R.string.memory_bundle_review_entry else R.string.memory_bundle_review_full))}
                            }
                        }
                    }
                }
            }
        }},
        confirmButton={TextButton(enabled=!busy&&selectedKeys.isNotEmpty(),onClick=onImport){Text(stringResource(if(busy)R.string.memory_importing else R.string.memory_import_selected))}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text(stringResource(R.string.common_cancel))}},
    )
    detailKey?.let { key ->
        val candidate=preview.candidates.firstOrNull{it.key==key}
        if(candidate!=null){
        var edited by remember(preview.bundleId,key){mutableStateOf(candidate.content)}
        AlertDialog(
            onDismissRequest={detailKey=null},
            title={Text(stringResource(R.string.memory_imported_entry,candidate.category))},
            text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){
                OutlinedTextField(edited,{edited=it},label={Text(stringResource(R.string.memory_review_correct))},modifier=Modifier.fillMaxWidth(),minLines=4,maxLines=12)
                if(MemoryBundleV1.isSensitive(candidate.copy(content=edited)))Text(stringResource(R.string.memory_sensitive_blocked),color=MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.memory_bundle_candidate_details,candidate.trust,candidate.externalId,candidate.originalScope.ifBlank{stringResource(R.string.memory_default_unspecified)},candidate.originalCreatedAt?.toString()?:stringResource(R.string.memory_default_unknown),candidate.originalUpdatedAt?.toString()?:stringResource(R.string.memory_default_unknown),candidate.provenance.ifBlank{stringResource(R.string.memory_default_unspecified)}),style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={TextButton(enabled=edited.isNotBlank()&&!MemoryBundleV1.isSensitive(candidate.copy(content=edited)),onClick={onCandidateEdit(key,edited);reviewed.add(key);detailKey=null}){Text(stringResource(R.string.memory_save_include))}},
            dismissButton={TextButton(onClick={onSelectionChange(selectedKeys-key);detailKey=null}){Text(stringResource(R.string.memory_leave_out))}},
        )
        }
    }
}

@Composable fun ContextDiagnosticsEntry() {
    val context=LocalContext.current;val graph=(context.applicationContext as LocalAiApplication).graph;val f=graph.contextFoundation
    val enabled by f.enabled.collectAsState();var open by remember{mutableStateOf(false)}
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(stringResource(R.string.memory_context_builder_experimental),Modifier.weight(1f));Switch(enabled,{f.enable(it)})}
    TextButton(onClick={open=true}){Text(stringResource(R.string.memory_context_validation))}
    if(open)ContextInspector{open=false}
}

@Composable
internal fun ContextBundleSummary(
    includedCount: Int,
    omittedCount: Int,
    estimatedInputTokens: Int,
    inputBudget: Int,
    modifier: Modifier = Modifier,
) {
    val progress = if (inputBudget > 0) (estimatedInputTokens.toFloat() / inputBudget).coerceIn(0f, 1f) else 0f
    Card(
        modifier = modifier.fillMaxWidth().testTag("context-inspector-summary"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(LocalAiSpacing.x4),
            verticalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.memory_context_budget), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${(progress * 100).toInt()}%",
                    Modifier.testTag("context-budget-percent"),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                stringResource(R.string.memory_estimated_input_tokens,estimatedInputTokens,inputBudget),
                Modifier.testTag("context-budget-estimate"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                ContextCountPill(stringResource(R.string.memory_included_count,includedCount), warning = false)
                if (omittedCount > 0) ContextCountPill(stringResource(R.string.memory_omitted_count,omittedCount), warning = true)
            }
        }
    }
}

@Composable
private fun ContextCountPill(label: String, warning: Boolean) {
    val status = LocalAiStatusColors.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (warning) status.warningContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (warning) status.onWarningContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(label, Modifier.padding(horizontal = LocalAiSpacing.x2, vertical = LocalAiSpacing.x1),
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ContextInspectorSection(title: String, count: Int, omitted: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = LocalAiSpacing.x2),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalAiSpacing.x2),
    ) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        ContextCountPill(count.toString(), warning = omitted)
    }
}

@Composable
internal fun ContextInspectorEntry(
    title: String,
    detail: String,
    omitted: Boolean,
    modifier: Modifier = Modifier,
) {
    val status = LocalAiStatusColors.current
    Card(
        modifier = modifier.fillMaxWidth().testTag(if (omitted) "context-inspector-omitted-entry" else "context-inspector-included-entry"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (omitted) status.warningContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (omitted) status.onWarningContainer else MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            1.dp,
            if (omitted) status.warning.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = LocalAiSpacing.x3, vertical = LocalAiSpacing.x2),
            verticalArrangement = Arrangement.spacedBy(LocalAiSpacing.x1),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun ContextInspector(close:()->Unit) {
    val context=LocalContext.current;val graph=(context.applicationContext as LocalAiApplication).graph;val f=graph.contextFoundation;val scope=rememberCoroutineScope()
    val projects by graph.workspace.activeProjects.collectAsState(initial=emptyList())
    var projectId by remember{mutableStateOf<String?>(null)};var picking by remember{mutableStateOf(false)};var query by remember{mutableStateOf("")};var conversationId by remember{mutableStateOf("")};var bundle by remember{mutableStateOf<ContextBundle?>(null)};var full by remember{mutableStateOf(false)};var report by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var job by remember{mutableStateOf<Job?>(null)}
    fun run(block:suspend()->Unit){job=scope.launch{busy=true;error=null;try{withContext(Dispatchers.IO){block()}}catch(cancel:CancellationException){throw cancel}catch(e:Exception){error=e.message ?: e.javaClass.simpleName}finally{busy=false}}}
    SemanticPanel(stringResource(R.string.memory_context_inspector),close) {
        Box{TextButton(onClick={picking=true}){Text(projects.firstOrNull{it.id==projectId}?.name ?: stringResource(R.string.memory_user_global))};DropdownMenu(picking,{picking=false}){DropdownMenuItem(text={Text(stringResource(R.string.memory_user_global))},onClick={projectId=null;picking=false});projects.filter{it.workspaceKind=="PROJECT"}.forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={projectId=p.id;picking=false})}}}
        OutlinedTextField(query,{query=it},label={Text(stringResource(R.string.memory_query))},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(conversationId,{conversationId=it},label={Text(stringResource(R.string.memory_conversation_id_optional))},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(enabled=!busy&&query.isNotBlank(),onClick={run{bundle=f.build(ContextRequest(query,ScopeAccess(projectId=projectId,sessionId=conversationId.takeIf{it.isNotBlank()}),conversationId=conversationId.takeIf{it.isNotBlank()}))}}){Text(stringResource(R.string.memory_build_context_no_generation))}
        if(conversationId.isNotBlank())TextButton(enabled=!busy,onClick={run{val owner=graph.database.conversationDao().get(conversationId);check(owner!=null&&(owner.projectId==projectId||(projectId==null&&owner.projectId?.let{graph.database.projectDao().get(it)?.workspaceKind}=="CHAT"))){context.getString(R.string.memory_select_conversation_project)};report=context.getString(R.string.memory_indexed_source_messages,f.indexConversation(conversationId))}}){Text(stringResource(R.string.memory_index_conversation))}
        bundle?.let{b->
            Column(verticalArrangement=Arrangement.spacedBy(LocalAiSpacing.x2)) {
                ContextBundleSummary(b.included.size,b.dropped.size,b.estimatedInputTokens,b.inputBudget)
                Text(stringResource(R.string.memory_context_metrics,ContextTokenEstimator.METHOD,b.timings.totalMs,b.request.reservedOutput,b.safetyMargin), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(b.blockCosts.isNotEmpty()) {
                    Text(stringResource(R.string.memory_budget_by_section), style=MaterialTheme.typography.titleSmall, modifier=Modifier.semantics { heading() })
                    b.blockCosts.forEach{(section,tokens)->Text(stringResource(R.string.memory_estimated_tokens,section,tokens),style=MaterialTheme.typography.bodySmall)}
                }
                if(b.included.isNotEmpty()) {
                    ContextInspectorSection(stringResource(R.string.memory_included_context),b.included.size)
                    b.included.forEach{ContextInspectorEntry("${it.id} · ${it.kind}",stringResource(R.string.memory_item_scope_tokens,memoryScopeLabel(it.scope.type.name),it.estimatedTokens),omitted=false)}
                }
                if(b.dropped.isNotEmpty()) {
                    ContextInspectorSection(stringResource(R.string.memory_omitted_context),b.dropped.size,omitted=true)
                    b.dropped.forEach{ContextInspectorEntry(it.item.id,it.reason,omitted=true)}
                }
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(full,{full=it});Text(stringResource(R.string.memory_expand_private_context))}
                if(full)b.included.forEach{Text(it.text)}
                TextButton(enabled=!busy,onClick={run{report=ContextInterop(graph).answer(b).answer}}){Text(stringResource(R.string.memory_run_gemma_explicit))}
                TextButton(onClick={report=GsonBuilder().setPrettyPrinting().create().toJson(b.safeReport())}){Text(stringResource(R.string.memory_metadata_report))}
            }
        }
        TextButton(enabled=!busy,onClick={run{report=ContextMemoryValidation(graph).run()}}){Text(stringResource(R.string.memory_run_validation_suite))}
        TextButton(enabled=!busy,onClick={run{report=ContextCapacityDiagnostics(graph).run()}}){Text(stringResource(R.string.memory_probe_context_capacity))}
        if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={job?.cancel()}){Text(stringResource(R.string.common_cancel))}}
        if(report.isNotEmpty()){Text(report.take(12000));TextButton(onClick={context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText(context.getString(R.string.memory_context_inspector),report))}){Text(stringResource(R.string.memory_copy_report))}}
        error?.let{Text(memoryActionErrorMessage(context,IllegalStateException(it)),color=MaterialTheme.colorScheme.error)}
    }
}
