package com.anggrayudi.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anggrayudi.storage.transfer.TransferEvent
import com.anggrayudi.storage.transfer.TransferResult
import com.anggrayudi.storage.transfer.TransferSpec
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group 5 - Flow forms & cancellation (V3_TEST_CASES.md TC-40, TC-41).
 */
@RunWith(AndroidJUnit4::class)
class FlowFormsTest {

  private val context = targetContext()
  private lateinit var playground: File

  @Before
  fun setUp() {
    playground = newPlaygroundDir("tc40_41")
  }

  @After
  fun tearDown() {
    playground.deleteRecursivelyOrThrow()
  }

  private fun storageFile(file: File) = StorageFile.from(context, file)

  // TC-40: Event stream shape
  @Test
  fun tc40_eventStreamShape() = runBlocking {
    val source = File(playground, "source").apply { mkdirs() }
    val target = File(playground, "target").apply { mkdirs() }
    val src = File(source, "a.txt").apply { writeText("hello") }

    val events = mutableListOf<TransferEvent>()
    storageFile(src).copyToAsFlow(storageFile(target)).collect { events.add(it) }

    val completedEvents = events.filterIsInstance<TransferEvent.Completed<*>>()
    assertEquals("expected exactly one Completed event, got: $events", 1, completedEvents.size)
    assertTrue(
      "the terminal event must be the last one emitted, got: $events",
      events.last() is TransferEvent.Completed<*>,
    )
    val result = completedEvents.single().result
    assertTrue("Completed.result should be Success but was $result", result is TransferResult.Success<*>)
  }

  // TC-41: Cancellation
  @Test
  fun tc41_cancellation() = runBlocking {
    val source = File(playground, "source").apply { mkdirs() }
    val target = File(playground, "target").apply { mkdirs() }
    val bigFile = File(source, "big.bin").apply { writeRandomBytes(50 * 1024 * 1024) }

    val progressSeen = CompletableDeferred<Unit>()
    var crashed: Throwable? = null

    val job: Job =
      launch(Dispatchers.Default) {
        try {
          storageFile(bigFile)
            .copyToAsFlow(storageFile(target), TransferSpec().apply { updateInterval = 20 })
            .collect { event ->
              if (event is TransferEvent.Progress) {
                progressSeen.complete(Unit)
              }
            }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Throwable) {
          crashed = e
        }
      }

    // Wait for the collector to observe the first Progress event, then cancel it externally -
    // this is the scenario TC-41 specifies ("cancel the job at first Progress").
    withTimeout(15_000) { progressSeen.await() }

    val cancelStart = System.currentTimeMillis()
    job.cancelAndJoin()
    val cancelElapsed = System.currentTimeMillis() - cancelStart

    assertTrue("collector must not crash, but got: $crashed", crashed == null)
    assertTrue("cancellation join should be prompt, took ${cancelElapsed}ms", cancelElapsed < 2000)

    // Documented behavior: what does the target look like after a mid-copy cancellation?
    val leftover = File(target, "big.bin")
    val leftoverDescription =
      if (!leftover.exists()) "no target file"
      else "target file present, size=${leftover.length()} of ${bigFile.length()}"
    println("TC-41: leftover target state after cancellation = $leftoverDescription")
  }
}
