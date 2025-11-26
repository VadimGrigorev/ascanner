package com.tsd.ascanner

import android.app.Application
import com.tsd.ascanner.data.net.ApiClient
import com.tsd.ascanner.data.auth.AuthService
import com.tsd.ascanner.data.docs.DocsService
import java.io.OutputStream
import java.io.PrintStream

class AScannerApp : Application() {
    lateinit var apiClient: ApiClient
        private set
    lateinit var authService: AuthService
        private set
    lateinit var docsService: DocsService
        private set

	companion object {
		lateinit var instance: AScannerApp
			private set
	}

    override fun onCreate() {
        super.onCreate()
		// Mute noisy System.out logs from underlying HTTP stack (e.g. OkHttp's internal prints)
		try {
			System.setOut(PrintStream(object : OutputStream() {
				override fun write(b: Int) {
					// no-op
				}
			}))
		} catch (_: Exception) {
			// If redirect fails for any reason, just ignore and keep default behavior
		}
		instance = this
        apiClient = ApiClient()
        authService = AuthService(apiClient)
        docsService = DocsService(apiClient, authService)
    }
}


