package com.trading.account.application

import org.springframework.stereotype.Service

@Service
class AlertService {
    
    fun sendCriticalAlert(title: String, message: String) {
        println("🚨 CRITICAL ALERT: $title - $message")
    }
    
    fun sendWarningAlert(title: String, message: String) {
        println("⚠️ WARNING ALERT: $title - $message")
    }
    
    fun sendInfoAlert(title: String, message: String) {
        println("ℹ️ INFO ALERT: $title - $message")
    }
}