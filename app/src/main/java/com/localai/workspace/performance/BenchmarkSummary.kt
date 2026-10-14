package com.localai.workspace.performance

import kotlin.math.ceil

data class BenchmarkCohort(val modelIdentity:String,val device:String,val androidApi:Int,
    val runtimeVersion:String,val suiteVersion:String,val testId:String,val mode:BenchmarkMode,
    val configuration:BenchmarkConfiguration,val effectiveBackend:String?,val speculativeActive:Boolean?,val ttftBoundary:String)
data class MetricDistribution(val measuredSamples:Int,val p50:Double?,val p95:Double?)
data class BenchmarkSummary(val cohort:BenchmarkCohort,val records:Int,val successful:Int,val rejectedQuality:Int,
    val qualityChecked:Int,val qualityPassed:Int,val nativeTtftMs:MetricDistribution,val requestTtftMs:MetricDistribution,
    val decodeTokensPerSecond:MetricDistribution,val totalMs:MetricDistribution,val observedWorkerPssBytes:MetricDistribution)

/** Same-device/model/task/config cohorts only. Missing measurements are not zeros.
 * Median interpolates the middle pair; p95 uses nearest rank (exploratory with n<20).
 * Observed PSS is the maximum of two endpoints, not peak memory during generation.
 */
object BenchmarkSummaries {
    fun distribution(values:List<Double?>):MetricDistribution {
        val v=values.filterNotNull().filter{it.isFinite()&&it>=0}.sorted();val n=v.size
        if(n==0)return MetricDistribution(0,null,null)
        val median=if(n%2==1)v[n/2]else v[n/2-1]/2+v[n/2]/2
        return MetricDistribution(n,median,v[(ceil(.95*n).toInt()-1).coerceIn(0,n-1)])
    }
    fun groups(records:List<BenchmarkRecord>):List<BenchmarkSummary> = records.groupBy { r->
        BenchmarkCohort(r.modelSha256 ?: "UNVERIFIED_MODEL:${r.runId}",r.device,r.androidApi,r.runtimeVersion,r.suiteVersion,r.testId,r.mode,
            r.configuration,r.metrics.backendEffective,r.metrics.speculativeActive,r.ttftBoundary)
    }.map{(key,rows)->
        val valid=rows.filter{it.result==BenchmarkResult.SUCCESS&&it.validationPassed!=false}
        fun longs(value:(BenchmarkRecord)->Long?)=distribution(valid.map{value(it)?.toDouble()})
        BenchmarkSummary(key,rows.size,valid.size,rows.count{it.result==BenchmarkResult.SUCCESS&&it.validationPassed==false},
            rows.count{it.validationPassed!=null},rows.count{it.validationPassed==true},
            longs{it.metrics.timeToFirstTokenMs},longs{it.metrics.requestTimeToFirstTokenMs},distribution(valid.map{it.metrics.decodeTokensPerSecond}),
            longs{it.metrics.endToEndTotalMs},longs{listOfNotNull(it.metrics.workerPssBeforeBytes,it.metrics.workerPssAfterBytes).maxOrNull()})
    }
}
