package com.dianming.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object PhoneHelper {

    /**
     * 调起系统拨号盘并填入号码（不需要任何权限）。
     */
    fun dial(context: Context, phone: String) {
        val number = phone.filter { it.isDigit() || it == '+' }
        if (number.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // 设备没有拨号应用，忽略
        }
    }

    /**
     * 打开系统"文字转语音输出"设置页，让用户安装/启用语音引擎。
     * 该 action 不是公开常量（Settings 里没有 ACTION_TTS_SETTINGS），但 AOSP 与各定制系统均支持。
     */
    fun openTtsSettings(context: Context) {
        val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // 部分定制系统没有该页面，退回到系统设置首页
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }
}
