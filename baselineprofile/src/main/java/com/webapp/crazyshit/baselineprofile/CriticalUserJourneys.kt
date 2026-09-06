package com.webapp.crazyshit.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.addy37.crazyshitunofficial"

internal fun MacrobenchmarkScope.launchApp() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.text("I understand")), 2_000)
    device.findObject(By.text("I understand"))?.click()
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.scrollHome() {
    device.wait(Until.hasObject(By.text("Home")), 8_000)
    device.findObject(By.text("Home"))?.click()
    device.waitForIdle()
    repeat(3) {
        swipeUp()
        device.waitForIdle(250)
    }
}

internal fun MacrobenchmarkScope.openAndScrollChaos() {
    device.wait(Until.hasObject(By.text("Chaos")), 8_000)
    device.findObject(By.text("Chaos"))?.click()
    device.waitForIdle()
    device.wait(Until.hasObject(By.desc("Play or pause video")), 12_000)
    repeat(5) {
        swipeUp()
        device.waitForIdle(350)
    }
}

internal fun MacrobenchmarkScope.search() {
    device.findObject(By.desc("Search"))?.click()
    val field = device.wait(Until.findObject(By.desc("Search creators, albums and videos")), 5_000)
    field?.click()
    field?.text = "mia"
    device.pressEnter()
    device.wait(Until.hasObject(By.textContains("Search complete")), 15_000)
    swipeUp()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.swipeUp() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.swipe(
        width / 2,
        (height * 0.78f).toInt(),
        width / 2,
        (height * 0.24f).toInt(),
        18
    )
}

internal fun MacrobenchmarkScope.openCreatorProfileAndGallery() {
    device.wait(Until.hasObject(By.text("Collections")), 5_000)
    device.findObject(By.text("Collections"))?.click()
    device.wait(Until.hasObject(By.desc("Show Fapzone collections")), 5_000)
    device.findObject(By.desc("Show Fapzone collections"))?.click()
    val creator = device.wait(
        Until.findObject(By.descContains("Open pictures and videos")),
        30_000
    )
    creator?.click()
    device.wait(Until.hasObject(By.textStartsWith("All")), 20_000)
    device.findObject(By.textStartsWith("Videos"))?.click()
    val video = device.wait(Until.findObject(By.descStartsWith("Video,")), 20_000)
    video?.click()
    device.wait(Until.hasObject(By.desc("Play or pause video")), 15_000)
}
