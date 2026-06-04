package com.example.yourswelnes.core.location

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

@Singleton
class LocationUploadLock @Inject constructor() {
    val mutex = Mutex()
}
