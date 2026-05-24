package com.qwe7002.telegram_sms.static_class

import com.tencent.mmkv.MMKV

/**
 * Single source of truth for every JobScheduler id the app uses.
 *
 * JobScheduler identifies a job by `(packageName, id)` only — the target [android.app.job.JobService]
 * component is *not* part of its identity. So two unrelated jobs sharing an id silently replace each
 * other on `schedule()`. Allocating ids ad-hoc (e.g. from a small 1..9999 counter) risks landing on a
 * fixed id below and clobbering a long-lived job. Keep all id allocation here so the ranges stay disjoint.
 */
object JobIds {
    // --- Fixed, singleton jobs: one persistent job each, always scheduled under its own id. ---
    const val KEEP_ALIVE = 10
    const val RESEND = 20

    // --- Carbon-copy delivery jobs ---
    // These are one-shot and each carries its own payload in extras, so every enqueue needs a *distinct*
    // id (reusing one id would replace — and thereby drop — a still-pending delivery). They are allocated
    // from a high band that can never overlap the fixed ids above. The counter is persisted in MMKV: an
    // in-memory counter resets to 0 on process restart and would then collide with persisted CC jobs that
    // were scheduled before the restart, silently overwriting them.
    private const val CC_BASE = 100_000
    private const val CC_RANGE = 100_000 // ids span [100000, 199999]
    private const val CC_COUNTER_KEY = "cc_job_id_counter"

    /** Returns a fresh, persisted, collision-free job id for a carbon-copy delivery job. */
    @JvmStatic
    fun nextCarbonCopyId(): Int = synchronized(this) {
        // MMKV has no atomic read-modify-write, so guard the increment with the monitor lock.
        // The app is single-process, so an intra-process lock is sufficient.
        val mmkv = MMKV.defaultMMKV()
        val next = (mmkv.decodeInt(CC_COUNTER_KEY, 0) + 1) % CC_RANGE
        mmkv.encode(CC_COUNTER_KEY, next)
        CC_BASE + next
    }
}
