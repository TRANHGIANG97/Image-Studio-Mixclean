package com.thgiang.image.core.ad

/** Abstraction cho phép mock log trong test. */
interface AdLogger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String)
}
