package com.anggrayudi.storage.sample

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Base for every sample screen. The UI is built in code on purpose: each screen is then a single
 * file whose content is the API being demonstrated, with no layout boilerplate in the way.
 */
abstract class SampleScreen : AppCompatActivity() {

  private lateinit var buttons: LinearLayout
  private lateinit var output: TextView

  /** Screen title, and the one-line explanation shown above the buttons. */
  abstract val screenTitle: String

  abstract val screenSummary: String

  abstract fun SampleScreen.buildScreen()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    title = screenTitle
    // The home screen is the root of the stack, so it gets no up arrow.
    supportActionBar?.setDisplayHomeAsUpEnabled(!isTaskRoot)

    val root =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
      }
    root.addView(
      TextView(this).apply {
        text = screenSummary
        setPadding(0, 0, 0, dp(8))
      }
    )
    buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.addView(buttons)
    output =
      TextView(this).apply {
        setPadding(0, dp(16), 0, 0)
        text = ""
      }
    root.addView(output)
    setContentView(ScrollView(this).apply { addView(root, MATCH_PARENT, WRAP_CONTENT) })
    applyEdgeToEdgeContentInsets()

    buildScreen()
  }

  override fun onSupportNavigateUp(): Boolean {
    finish()
    return true
  }

  /** Adds a button that runs [action] in the activity's scope, so suspend APIs can be called. */
  protected fun button(label: String, action: suspend () -> Unit) {
    buttons.addView(
      Button(this).apply {
        text = label
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener {
          lifecycleScope.launch { runCatching { action() }.onFailure { log("Failed: $it") } }
        }
      },
      MATCH_PARENT,
      WRAP_CONTENT,
    )
  }

  /** Appends a line to the on-screen log; newest first so long output stays readable. */
  protected fun log(message: String) {
    output.text = buildString {
      append(message)
      if (output.text.isNotEmpty()) {
        append("\n\n")
        append(output.text)
      }
    }
  }

  protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
