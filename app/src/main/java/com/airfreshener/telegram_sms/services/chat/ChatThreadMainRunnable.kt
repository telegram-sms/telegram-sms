package com.airfreshener.telegram_sms.services.chat

import android.content.Context
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.common.data.LogRepository
import com.airfreshener.telegram_sms.common.data.PrefsRepository
import com.airfreshener.telegram_sms.model.PollingJson
import com.airfreshener.telegram_sms.model.Settings
import com.airfreshener.telegram_sms.utils.NetworkUtils
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.PaperUtils
import com.airfreshener.telegram_sms.utils.PaperUtils.tryRead
import com.airfreshener.telegram_sms.utils.ServiceUtils
import com.airfreshener.telegram_sms.utils.SmsUtils
import com.google.gson.JsonParser
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatThreadMainRunnable(
    private val controller: ChatServiceController,
    private val appContext: Context,
    private val prefsRepository: PrefsRepository,
    private val logRepository: LogRepository,
) : Runnable {

    private var firstRequest = true
    private var isStopped = false

    override fun run() {
        Log.d(TAG, "run: thread main start")
        val settings = prefsRepository.getSettings()
        if (OtherUtils.parseStringToLong(settings.chatId) != 0L) {
            controller.botUsername = PaperUtils.getDefaultBook().tryRead("bot_username", "")
            if (controller.botUsername.isEmpty()) {
                while (isStopped.not() && fetchBotUsername().not()) {
                    logRepository.writeLog("Failed to get bot Username, Wait 5 seconds and try again.")
                    try {
                        Thread.sleep(5000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
            Log.i(TAG, "run: The Bot Username is loaded. The Bot Username is: ${controller.botUsername}")
        }
        getCommand(settings)
    }

    fun stopPolling() {
        isStopped = true
    }

    private fun getCommand(settings: Settings) {
        while (isStopped.not()) {
            val timeout = 5 * controller.magnification
            val httpTimeout = timeout + 5
            val okHttpClientNew = NetworkUtils.getOkhttpObj(settings).newBuilder()
                .readTimeout(httpTimeout.toLong(), TimeUnit.SECONDS)
                .writeTimeout(httpTimeout.toLong(), TimeUnit.SECONDS)
                .build()
            Log.d(TAG, "run: Current timeout: " + timeout + "S")
            val requestUri = NetworkUtils.getUrl(settings.botToken, "getUpdates")
            val requestBody = PollingJson()
            requestBody.offset = controller.offset
            requestBody.timeout = timeout
            if (firstRequest) {
                requestBody.timeout = 0
                Log.d(TAG, "run: first_request")
            }
            val body = requestBody.toRequestBody()
            val request: Request = Request.Builder().url(requestUri).post(body).build()
            val call = okHttpClientNew.newCall(request)
            var response: Response
            try {
                response = call.execute()
                controller.errorMagnification = 1
            } catch (e: IOException) {
                e.printStackTrace()
                if (!NetworkUtils.checkNetworkStatus(appContext)) {
                    logRepository.writeLog("No network connections available, Wait for the network to recover.")
                    controller.errorMagnification = 1
                    controller.magnification = 1
                    Log.d(TAG, "run: break loop.")
                    break
                }
                val sleepTime = 5 * controller.errorMagnification
                logRepository.writeLog(
                    "Connection to the Telegram API service failed, try again after $sleepTime seconds."
                )
                controller.magnification = 1
                if (controller.errorMagnification <= 59) {
                    ++controller.errorMagnification
                }
                try {
                    Thread.sleep(sleepTime * 1000L)
                } catch (e1: InterruptedException) {
                    e1.printStackTrace()
                }
                continue
            }
            if (response.code == 200) {
                val responseBody = response.body
                if (responseBody == null) {
                    Log.d(TAG, "No body on 200 response")
                    continue
                }
                val result: String = try {
                    responseBody.string()
                } catch (e: IOException) {
                    e.printStackTrace()
                    continue
                }
                val resultObj = JsonParser.parseString(result).asJsonObject
                if (resultObj["ok"].asBoolean) {
                    val resultArray = resultObj["result"].asJsonArray
                    for (item in resultArray) {
                        controller.receiveHandle(item.asJsonObject, firstRequest)
                    }
                    firstRequest = false
                }
                if (controller.magnification <= 11) {
                    ++controller.magnification
                }
            } else {
                Log.d(TAG, "response code: " + response.code)
                if (response.code == 401) {
                    val responseBody = response.body
                    if (responseBody == null) {
                        Log.d(TAG, "No body on 200 response")
                        continue
                    }
                    val result: String = try {
                        responseBody.string()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        continue
                    }
                    val resultObj = JsonParser.parseString(result).asJsonObject
                    val resultMessage = """
                            ${appContext.getString(R.string.error_stop_message)}
                            ${appContext.getString(R.string.error_message_head)}${resultObj["description"].asString}
                            Code: ${response.code}
                            """.trimIndent()
                    SmsUtils.sendFallbackSms(appContext, resultMessage, -1)
                    ServiceUtils.stopAllServices(appContext)
                    break
                }
            }
        }
    }

    private fun fetchBotUsername(): Boolean {
        val settings = prefsRepository.getSettings()
        val requestUri = NetworkUtils.getUrl(settings.botToken, "getMe")
        val request: Request = Request.Builder().url(requestUri).build()
        val call = NetworkUtils.getOkhttpObj(settings).newCall(request)
        val response: Response = try {
            call.execute()
        } catch (e: IOException) {
            e.printStackTrace()
            logRepository.writeLog("Get username failed:" + e.message)
            return false
        }
        if (response.code == 200) {
            val responseBody = response.body
            if (responseBody == null) {
                Log.d(TAG, "No body on 200 response")
                return false
            }
            val result: String = try {
                responseBody.string()
            } catch (e: IOException) {
                e.printStackTrace()
                return false
            }
            val resultObj = JsonParser.parseString(result).asJsonObject
            if (resultObj["ok"].asBoolean) {
                val botUsername = resultObj["result"].asJsonObject["username"].asString.apply {
                    PaperUtils.getDefaultBook().write("bot_username", this)
                }
                Log.d(TAG, "bot_username: $botUsername")
                logRepository.writeLog("Get the bot username: $botUsername")
                controller.botUsername = botUsername
            }
            return true
        }
        return false
    }

    companion object {
        private val TAG = ChatThreadMainRunnable::class.java.simpleName
    }

}
