package com.example.roomkit
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30], manifest=Config.NONE)
class Phase3DatabaseTest : Phase3Checks()
