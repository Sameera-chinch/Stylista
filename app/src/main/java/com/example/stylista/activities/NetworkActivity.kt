package com.example.stylista.activities

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.example.stylista.BuildConfig
import com.example.stylista.R
import com.example.stylista.StylistaApp
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*
import com.example.stylista.utils.*

class NetworkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_network)

        val fetchButton = findViewById<Button>(R.id.btnFetchProducts)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val resultText = findViewById<TextView>(R.id.txtResult)

        fetchButton.setOnClickListener {

            progressBar.visibility = View.VISIBLE
            resultText.text = "Fetching products..."

            Thread {

                try {

                    val url =
                        URL("https://dummyjson.com/products?limit=5")

                    val connection =
                        url.openConnection() as HttpURLConnection

                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val responseCode = connection.responseCode

                    if (responseCode == HttpURLConnection.HTTP_OK) {

                        val response =
                            connection.inputStream
                                .bufferedReader()
                                .use { it.readText() }

                        val jsonObject =
                            JSONObject(response)

                        val products =
                            jsonObject.getJSONArray("products")

                        val result = StringBuilder()

                        for (i in 0 until products.length()) {

                            val product =
                                products.getJSONObject(i)

                            val title =
                                product.getString("title")

                            val price =
                                product.getDouble("price")

                            result.append(
                                "${i + 1}. $title\n"
                            )

                            result.append(
                                "Price: ₹$price\n\n"
                            )
                        }

                        runOnUiThread {

                            progressBar.visibility = View.GONE

                            resultText.text =
                                result.toString()
                        }

                    } else {

                        runOnUiThread {

                            progressBar.visibility = View.GONE

                            resultText.text =
                                "Error: HTTP $responseCode"
                        }
                    }

                    connection.disconnect()

                } catch (e: Exception) {

                    runOnUiThread {

                        progressBar.visibility = View.GONE

                        resultText.text =
                            "Network Error:\n${e.message}"
                    }
                }

            }.start()
        }
    }
}
