package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Decant!", appName)
    assertEquals("znraznra", context.getString(R.string.developer_name))
    assertEquals("decant", context.getString(R.string.project_name))
    assertEquals("znraznra", BuildConfig.DEVELOPER_NAME)
    assertEquals("decant", BuildConfig.PROJECT_NAME)
    assertEquals("com.znraznra.decant", BuildConfig.APPLICATION_ID)
  }
}
