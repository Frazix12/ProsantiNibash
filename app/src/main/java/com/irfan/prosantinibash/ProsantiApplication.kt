package com.irfan.prosantinibash

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/** Unlock state belongs to this process and is never restored from disk. */
open class ProsantiApplication : Application(), DefaultLifecycleObserver {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super<Application>.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) { container.session.onBackground() }
    override fun onStart(owner: LifecycleOwner) { container.session.onForeground() }
}
