package id.nexbiz.pos.data.remote.api

import id.nexbiz.pos.data.remote.dto.DeltaPullResponse
import id.nexbiz.pos.data.remote.dto.OutboxPushBatchRequest
import id.nexbiz.pos.data.remote.dto.OutboxPushBatchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NexbizSyncApi {

    /**
     * Idempotent Outbox Batch Push endpoint.
     * Deduplicates each mutation by `idempotency_key` scoped to `businessId`.
     * Returns 200 OK with acknowledged items, per-item conflict metadata, and
     * authoritative `server_confirmed_stock` snapshots.
     */
    @POST("v1/tenants/{businessId}/sync/outbox")
    suspend fun pushOutboxBatch(
        @Path("businessId") businessId: String,
        @Header("X-Idempotency-Batch-Key") batchIdempotencyKey: String,
        @Header("X-Device-Id") deviceId: String,
        @Body request: OutboxPushBatchRequest
    ): Response<OutboxPushBatchResponse>

    /**
     * Cursor-based Multi-Tenant Delta Pull endpoint.
     * Fetches authoritative server deltas modified after `cursor` / `sinceEpochMs`.
     */
    @GET("v1/tenants/{businessId}/sync/delta")
    suspend fun pullServerDeltas(
        @Path("businessId") businessId: String,
        @Query("cursor") cursor: String?,
        @Query("since_epoch_ms") sinceEpochMs: Long,
        @Query("limit") limit: Int = 200
    ): Response<DeltaPullResponse>
}
