package it.eja.wikilite

import android.app.Dialog
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.io.File

class DownloadDialog(
    private val filePath: String,
    private val listener: (String, String) -> Unit
) : DialogFragment() {

    private var selectedPath: String = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_download, null)

        val tvFileInfo: TextView = view.findViewById(R.id.tvFileInfo)
        val spinnerLocation: Spinner = view.findViewById(R.id.spinnerLocation)

        val fileName = filePath.substringAfterLast("/")
        tvFileInfo.text = "Download: $fileName"

        val storageOptions = getAvailableStorageLocations()
        val locations = storageOptions.first
        val paths = storageOptions.second

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, locations)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLocation.adapter = adapter

        spinnerLocation.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPath = paths[position]
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                selectedPath = paths[0]
            }
        }

        selectedPath = paths[0]

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setTitle("Download Database")
            .setPositiveButton("Download") { dialog, _ ->
                listener(filePath, selectedPath)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }

    private fun getAvailableStorageLocations(): Pair<List<String>, List<String>> {
        val locationNames = mutableListOf<String>()
        val locationPaths = mutableListOf<String>()

        locationNames.add("Internal Storage")
        locationPaths.add(Environment.getExternalStorageDirectory().absolutePath)

        locationNames.add("Downloads")
        locationPaths.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)

        locationNames.add("Documents")
        locationPaths.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath)

        val externalSdPath = getExternalSdCardPath()
        if (externalSdPath != null) {
            locationNames.add("External SD Card")
            locationPaths.add(externalSdPath)
        }

        return Pair(locationNames, locationPaths)
    }

    private fun getExternalSdCardPath(): String? {
        val storageDirs = requireContext().getExternalFilesDirs(null)
        if (storageDirs.size > 1) {
            for (i in 1 until storageDirs.size) {
                val path = storageDirs[i]?.absolutePath
                if (path != null && !path.contains("/storage/emulated/") && path.contains("/storage/")) {
                    return File(path).parentFile?.parentFile?.absolutePath
                }
            }
        }
        return null
    }
}
