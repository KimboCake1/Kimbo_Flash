package com.kimboflash

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.kimboflash.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // Register a file picker callback
    private val binFilePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let {
                val filePath = FileUtils.getPath(this, it)
                if (filePath != null) {
                    val intent = Intent(this, TuningActivity::class.java)
                    intent.putExtra("binPath", filePath)
                    startActivity(intent)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detectConnection()

        // Launch file picker when ECU TUNING card is clicked
        binding.cardTuning.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            binFilePicker.launch(Intent.createChooser(intent, "Select ECU .bin file"))
        }
    }

    private fun detectConnection() {
        val statusText = when {
            FlashService.isBluetoothConnected() -> getString(R.string.status_connected_bluetooth)
            FlashService.isUsbConnected()       -> getString(R.string.status_connected_usb)
            FlashService.isWifiConnected()      -> getString(R.string.status_connected_wifi)
            else                                -> getString(R.string.status_disconnected)
        }

        binding.textViewStatus.text = statusText

        val connected = statusText != getString(R.string.status_disconnected)
        binding.cardRead.isEnabled = connected
        binding.cardWrite.isEnabled = connected
        binding.cardErrors.isEnabled = connected

        // Always allow tuning (user selects file)
        binding.cardTuning.isEnabled = true
    }
}
