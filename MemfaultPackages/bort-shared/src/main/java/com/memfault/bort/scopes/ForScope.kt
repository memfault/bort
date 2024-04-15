package com.memfault.bort.scopes

import kotlin.reflect.KClass

annotation class ForScope(val value: KClass<*>)
