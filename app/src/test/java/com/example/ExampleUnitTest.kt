package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun brandingMetadata_matchesDecantAndZnraznra() {
    assertEquals("znraznra", BuildConfig.DEVELOPER_NAME)
    assertEquals("decant", BuildConfig.PROJECT_NAME)
    assertEquals("com.znraznra.decant", BuildConfig.APPLICATION_ID)
  }
}
