package com.kimboflash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.kimboflash.databinding.ActivityTuningBinding
import com.kimboflash.patch.PatchManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class TuningActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTuningBinding
    private var binFile: File? = null

    private val binPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri: Uri ->
                contentResolver.openInputStream(uri).use { input ->
                    val tmp = File(cacheDir, "selected.bin")
                    FileOutputStream(tmp).use { output -> input?.copyTo(output) }
                    binFile = tmp
                    Toast.makeText(
                        this,
                        getString(R.string.file_selected, tmp.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTuningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize labels
        binding.textIgnitionAdvance.text = getString(
            R.string.ignition_advance,
            binding.seekIgnitionAdvance.progress
        )
        binding.textFuelMixture.text = getString(
            R.string.fuel_mixture,
            binding.seekFuelMixture.progress
        )

        // SeekBar listeners
        binding.seekIgnitionAdvance.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, prog: Int, fromUser: Boolean) {
                    binding.textIgnitionAdvance.text =
                        getString(R.string.ignition_advance, prog)
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            }
        )
        binding.seekFuelMixture.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, prog: Int, fromUser: Boolean) {
                    binding.textFuelMixture.text =
                        getString(R.string.fuel_mixture, prog)
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            }
        )

        // File Select
        binding.btnSelect.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
                .apply { type = "application/octet-stream" }
            binPickerLauncher.launch(
                Intent.createChooser(intent, getString(R.string.select_bin))
            )
        }

        // Apply patches
        binding.btnApply.setOnClickListener {
            val bin = binFile
            if (bin == null) {
                Toast.makeText(this, getString(R.string.error_no_file), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pm = PatchManager().apply {
                FileInputStream(bin).use(::loadBin)
                applyPopsBangs(binding.cbPopsBangs.isChecked)
                applyIgnitionCutMs42(binding.cbIgnitionCut42.isChecked)
                applyIgnitionCutMs43(binding.cbIgnitionCut43.isChecked)
                applyLaunchControl(binding.cbLaunchControl.isChecked)
                applyNoLiftShift(binding.cbNoLiftShift.isChecked)
                applyRollingAntiLag(binding.cbRollingAntiLag.isChecked)
                applyIgnitionAdvance(binding.seekIgnitionAdvance.progress)
                applyFuelMixture(binding.seekFuelMixture.progress)
            }
            FileOutputStream(bin).use(pm::saveBin)
            Toast.makeText(this, getString(R.string.patches_applied), Toast.LENGTH_LONG).show()
        }
    }

    private fun showExplanation(titleId: Int, messageId: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(titleId))
            .setMessage(getString(messageId))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun onInfoIgnitionAdvanceClicked() =
        showExplanation(R.string.explanation_ignition_advance_title, R.string.explanation_ignition_advance)

    fun onInfoFuelMixtureClicked() =
        showExplanation(R.string.explanation_fuel_mixture_title, R.string.explanation_fuel_mixture)

    fun onInfoPopsBangsClicked() =
        showExplanation(R.string.explanation_pops_bangs_title, R.string.explanation_pops_bangs)

    fun onInfoIgnitionCut42Clicked() =
        showExplanation(R.string.explanation_ignition_cut_ms42_title, R.string.explanation_ignition_cut_ms42)

    fun onInfoIgnitionCut43Clicked() =
        showExplanation(R.string.explanation_ignition_cut_ms43_title, R.string.explanation_ignition_cut_ms43)

    fun onInfoLaunchControlClicked() =
        showExplanation(R.string.explanation_launch_control_title, R.string.explanation_launch_control)

    fun onInfoNoLiftShiftClicked() =
        showExplanation(R.string.explanation_no_lift_shift_title, R.string.explanation_no_lift_shift)

    fun onInfoRollingAntiLagClicked() =
        showExplanation(R.string.explanation_rolling_anti_lag_title, R.string.explanation_rolling_anti_lag)
}
