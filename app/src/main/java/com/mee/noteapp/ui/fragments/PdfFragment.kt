package com.mee.noteapp.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.mee.noteapp.R
import com.mee.noteapp.ui.PdfViewerActivity

/** Pestaña "PDF": botón para elegir un documento del dispositivo y abrirlo en el lector propio. */
class PdfFragment : Fragment(R.layout.fragment_pdf) {

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            startActivity(
                Intent(requireContext(), PdfViewerActivity::class.java)
                    .putExtra(PdfViewerActivity.EXTRA_URI, uri.toString())
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btnOpenPdf).setOnClickListener {
            openPdf.launch(arrayOf("application/pdf"))
        }
    }
}
