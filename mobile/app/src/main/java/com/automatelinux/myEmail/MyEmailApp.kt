package com.automatelinux.myEmail

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Hilt exists for feedback-lib (dev flavor); the app itself injects nothing.
@HiltAndroidApp
class MyEmailApp : Application()
