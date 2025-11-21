package com.tsd.ascanner

import android.app.Application
import com.tsd.ascanner.data.net.ApiClient
import com.tsd.ascanner.data.auth.AuthService
import com.tsd.ascanner.data.docs.DocsService

class AScannerApp : Application() {
    lateinit var apiClient: ApiClient
        private set
    lateinit var authService: AuthService
        private set
    lateinit var docsService: DocsService
        private set

    override fun onCreate() {
        super.onCreate()
        apiClient = ApiClient()
        authService = AuthService(apiClient)
        docsService = DocsService(apiClient, authService)
    }
}


