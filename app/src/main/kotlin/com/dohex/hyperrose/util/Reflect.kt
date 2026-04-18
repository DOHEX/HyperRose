package com.dohex.hyperrose.util

import com.dohex.hyperrose.core.reflection.ReflectionHelper

object Reflect {
    fun callMethod(obj: Any, name: String, vararg args: Any?): Any? =
        ReflectionHelper.callMethod(obj, name, *args)

    fun callStaticMethod(clazz: Class<*>, name: String, vararg args: Any?): Any? =
        ReflectionHelper.callStaticMethod(clazz, name, *args)

    fun getField(obj: Any, name: String): Any? = ReflectionHelper.getField(obj, name)

    fun setField(obj: Any, name: String, value: Any?) = ReflectionHelper.setField(obj, name, value)

    fun getStaticField(clazz: Class<*>, name: String): Any? = ReflectionHelper.getStaticField(clazz, name)
}
