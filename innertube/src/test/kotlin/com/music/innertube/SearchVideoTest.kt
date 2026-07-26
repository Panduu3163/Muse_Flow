package com.music.innertube

import com.music.innertube.YouTube.SearchFilter
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SearchVideoTest {
    @Test
    // Explicit Unit: the trailing `?.forEach` makes the block's inferred type Unit?, which compiles
    // to an Object return and JUnit4 rejects as "should be void" before the test ever runs.
    fun testVideoSearch(): Unit = runBlocking {
        val result = YouTube.search("fakira", SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"))
        println("Result: $result")
        if (result.isSuccess) {
            println("Items size: ${result.getOrNull()?.items?.size}")
            result.getOrNull()?.items?.forEach {
                println("Item: $it")
            }
        } else {
            println("Error: ${result.exceptionOrNull()}")
        }
    }
}
