package com.dohex.hyperrose.core.reflection

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Java 反射工具，替代旧版 XposedHelpers。
 * libxposed API 101 不再提供 XposedHelpers，需使用原生反射。
 */
object ReflectionHelper {

    /**
     * 调用对象的实例方法。
     */
    fun callMethod(obj: Any, name: String, vararg args: Any?): Any? {
        val method = findMethod(obj.javaClass, name, args)
        method.isAccessible = true
        return method.invoke(obj, *args)
    }

    /**
     * 调用静态方法。
     */
    fun callStaticMethod(clazz: Class<*>, name: String, vararg args: Any?): Any? {
        val method = findMethod(clazz, name, args)
        method.isAccessible = true
        return method.invoke(null, *args)
    }

    /**
     * 获取实例字段值。
     */
    fun getField(obj: Any, name: String): Any? {
        val field = findField(obj.javaClass, name)
        field.isAccessible = true
        return field.get(obj)
    }

    /**
     * 设置实例字段值。
     */
    fun setField(obj: Any, name: String, value: Any?) {
        val field = findField(obj.javaClass, name)
        field.isAccessible = true
        field.set(obj, value)
    }

    /**
     * 获取静态字段值。
     */
    fun getStaticField(clazz: Class<*>, name: String): Any? {
        val field = findField(clazz, name)
        field.isAccessible = true
        return field.get(null)
    }

    /**
     * 在类层级中查找字段（包括父类）。
     */
    fun findField(clazz: Class<*>, name: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("Field '$name' not found in ${clazz.name} hierarchy")
    }

    /**
     * 在类层级中查找方法，通过名称和参数数量匹配。
     */
    private fun findMethod(clazz: Class<*>, name: String, args: Array<out Any?>): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            for (method in current.declaredMethods) {
                if (method.name == name && method.parameterCount == args.size) {
                    if (argsMatch(method.parameterTypes, args)) {
                        return method
                    }
                }
            }
            current = current.superclass
        }
        throw NoSuchMethodException("Method '$name' with ${args.size} args not found in ${clazz.name} hierarchy")
    }

    /**
     * 检查实参是否可以匹配形参类型。
     */
    private fun argsMatch(paramTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
        if (paramTypes.size != args.size) return false
        for (i in paramTypes.indices) {
            val arg = args[i] ?: continue // null 可以匹配任何引用类型
            val paramType = paramTypes[i]
            if (!boxType(paramType).isAssignableFrom(arg.javaClass)) {
                return false
            }
        }
        return true
    }

    /**
     * 将基本类型包装为对应的包装类型。
     */
    private fun boxType(type: Class<*>): Class<*> = when (type) {
        Boolean::class.javaPrimitiveType -> Boolean::class.java
        Byte::class.javaPrimitiveType -> Byte::class.java
        Char::class.javaPrimitiveType -> Char::class.java
        Short::class.javaPrimitiveType -> Short::class.java
        Int::class.javaPrimitiveType -> Int::class.java
        Long::class.javaPrimitiveType -> Long::class.java
        Float::class.javaPrimitiveType -> Float::class.java
        Double::class.javaPrimitiveType -> Double::class.java
        else -> type
    }
}
