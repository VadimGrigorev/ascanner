package com.tsd.ascanner

import android.app.Application
import com.tsd.ascanner.data.net.ApiClient
import com.tsd.ascanner.data.auth.AuthService
import com.tsd.ascanner.data.docs.DocsService
import com.tsd.ascanner.data.printer.TscPrinterService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AScannerApp : Application() {
    lateinit var apiClient: ApiClient
        private set
    lateinit var authService: AuthService
        private set
    lateinit var docsService: DocsService
        private set
    lateinit var printerService: TscPrinterService
        private set

	/**
	 * App-level coroutine scope.
	 * Used for best-effort operations during Activity shutdown, where lifecycleScope may already be cancelled.
	 */
	val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	companion object {
		lateinit var instance: AScannerApp
			private set
	}

    override fun onCreate() {
        super.onCreate()
		instance = this
        apiClient = ApiClient()
        authService = AuthService(apiClient)
        docsService = DocsService(apiClient, authService)
        printerService = TscPrinterService(this)
    }
}


