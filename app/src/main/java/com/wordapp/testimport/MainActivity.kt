package com.wordapp.testimport

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import com.wordapp.pfdview.scroll.DefaultScrollHandle
import com.wordapp.pfdview.util.FitPolicy
import com.wordapp.testimport.databinding.ActivityMainBinding
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private var deeplinkPdfUri: Uri? = null
    private lateinit var binding: ActivityMainBinding
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        readIntent()
        if (deeplinkPdfUri != null) {
            loadPdf()
            attachSearch()
            attachOnClick()
        }
    }

    private fun readIntent() {
        if (intent.data != null) {
            deeplinkPdfUri = intent.data ?: Uri.parse("")
        }
    }

    private fun loadPdf() {
        val pdfViewer = binding.pdfView.fromUri(deeplinkPdfUri)
            .enableSwipe(true)
            .enableAnnotationRendering(true)
            .spacing(4)
            .fitEachPage(true)
            .pageFitPolicy(FitPolicy.BOTH)
            .scrollHandle(DefaultScrollHandle(this))
            //.swipeHorizontal(true)
            .onPageChange { page, _ ->
                Timber.tag("page_check").d("onPageChange page:" + page)
                currentPage = page
            }
        pdfViewer.load()
        binding.pdfView.setSelectionPaintView(binding.pdDocSelect)

        binding.searchView.setOnCloseListener {
            binding.pdfView.setIsSearching(false)
            binding.pdDocSelect.resetPointer()
            false
        }
    }

    private fun attachSearch() {
        binding.searchView.setOnQueryTextListener(object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query != null) searchPdf(query)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean = false
        })
    }

    private fun attachOnClick() {
        binding.tvNext.setOnClickListener {
            binding.pdDocSelect.moveNext()
        }

        binding.tvPrev.setOnClickListener {
            binding.pdDocSelect.movePrev()
        }
    }

    private fun searchPdf(query: String) {
        binding.pdfView.search(query)
    }
}