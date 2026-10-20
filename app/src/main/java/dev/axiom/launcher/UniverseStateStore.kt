package dev.axiom.launcher

import android.content.Context
import android.util.AtomicFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * One private, atomic checkpoint, updated periodically and on backgrounding.
 * A power failure mid-write preserves the last FULLY committed checkpoint.
 * Compression operates directly on the grain stream: never allocate a huge
 * second copy of the universe merely to save it.
 */
internal class UniverseStateStore(context: Context) {
    enum class LoadResult { NEW_UNIVERSE, RESTORED, CORRUPTED }
    private val file = AtomicFile(File(context.filesDir, "axiom-universe-v1.gz"))
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diskLock = Any()
    private val pendingSave = AtomicBoolean(false)
    @Volatile private var protectCorruptData = false

    fun restoreInto(world: CosmicWorld): LoadResult {
        val base = file.baseFile
        if (!base.exists() && !File(base.path + ".bak").exists())
            return LoadResult.NEW_UNIVERSE
        return try {
            file.openRead().use { raw ->
                GZIPInputStream(BufferedInputStream(raw,64*1024)).use { zipped ->
                    world.readPersistentState(DataInputStream(zipped))
                }
            }
            LoadResult.RESTORED
        } catch (_: Exception) {
            // Never silently overwrite the user's only save with an empty
            // freshly seeded preset after a truncated/corrupt read.
            protectCorruptData = true
            LoadResult.CORRUPTED
        }
    }

    fun scheduleSave(world: CosmicWorld) {
        if (protectCorruptData || !pendingSave.compareAndSet(false,true)) return
        io.launch {
            try { save(world) }
            finally { pendingSave.set(false) }
        }
    }

    fun save(world: CosmicWorld): Boolean = synchronized(diskLock) {
        if (protectCorruptData) return@synchronized false
        val raw = try { file.startWrite() } catch (_: Exception) {
            return@synchronized false
        }
        try {
            val compressed = GZIPOutputStream(
                BufferedOutputStream(raw, 64*1024), 64*1024)
            val output = DataOutputStream(compressed)
            world.writePersistentState(output)
            output.flush()
            compressed.finish()
            compressed.flush()
            // finishWrite fsyncs and promotes only COMPLETE compressed data.
            // Do not close the underlying FileOutputStream before this call.
            file.finishWrite(raw)
            true
        } catch (_: Exception) {
            file.failWrite(raw)
            false
        }
    }

    /** Called only by an explicit user-requested reset/new scene. */
    fun allowManualReplacement() {
        synchronized(diskLock) {
            if (protectCorruptData) file.delete()
            protectCorruptData = false
        }
    }
}
