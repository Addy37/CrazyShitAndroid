package com.webapp.crazyshit.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class MainFlowsBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupUncompiled() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        launchApp()
    }

    @Test
    fun homeScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = { launchApp() }
    ) {
        scrollHome()
    }

    @Test
    fun chaosScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = { launchApp() }
    ) {
        openAndScrollChaos()
    }

    @Test
    fun searchFlow() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 3,
        setupBlock = { launchApp() }
    ) {
        search()
    }

    @Test
    fun creatorGalleryAndPlayback() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 2,
        setupBlock = { launchApp() }
    ) {
        openCreatorProfileAndGallery()
    }
}
