package com.thgiang.image.core.ad

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation thật — dùng Android Log. */
@Singleton
class AndroidAdLogger @Inject constructor() : AdLogger {
    override fun d(tag: String, message: String) { Log.d(tag, message) }
    override fun w(tag: String, message: String) { Log.w(tag, message) }
    override fun e(tag: String, message: String) { Log.e(tag, message) }
}
