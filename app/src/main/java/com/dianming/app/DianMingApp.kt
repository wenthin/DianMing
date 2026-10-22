package com.dianming.app

import android.app.Application
import com.dianming.app.data.Repository

class DianMingApp : Application() {
    val repository: Repository by lazy { Repository.get(this) }
}
