package com.memfault.bort

import javax.inject.Qualifier

@Qualifier
annotation class Main

@Qualifier
annotation class IO

@Qualifier
/*
 * You probably want the IO or Main dispatcher instead of the Default dispatcher. For one-off longer computation
 * tasks, we should use the IO dispatcher, which will run the task on a large pool of threads. For short tasks that
 * might hop between several threads, the Main dispatcher should be preferred
 */
annotation class Default
