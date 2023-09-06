package com.wordapp.pfdview;


import com.wordapp.pfdview.model.SearchRecord;
import com.shockwave.pdfium.PdfiumCore;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class PDocSearchTask implements Runnable {
    private final ArrayList<SearchRecord> arr = new ArrayList<>();

    private final WeakReference<PDFView> pdoc;
    public final AtomicBoolean abort = new AtomicBoolean();
    public final String key;
    private Thread thread;
    public final int flag = 0;
    private long keyStr;

    private boolean finished;

    public PDocSearchTask(PDFView pdoc, String key) {

        this.pdoc = new WeakReference<>(pdoc);
        this.key = key + "\0";
    }

    public long getKeyStr( ) {
        if(keyStr==0) {
            keyStr = PdfiumCore.nativeGetStringChars(key);
        }
        return keyStr;
    }

    @Override
    public void run() {
        PDFView pdfView = this.pdoc.get();
        if (pdfView == null) {
            return;
        }
        if (finished) {
            //a.setSearchResults(arr);
            //a.showT("findAllTest_Time : "+(System.currentTimeMillis()-CMN.ststrt)+" sz="+arr.size());
            pdfView.endSearch(arr);
        } else {
            SearchRecord schRecord;
            for (int pageNumber = 0; pageNumber < pdfView.getPageCount(); pageNumber++) {
                if (abort.get()) {
                    break;
                }
                schRecord =pdfView.findPageCached(key, pageNumber, 0);

                if (schRecord != null) {
                    pdfView.notifyItemAdded(this, arr, schRecord, pageNumber);
                } else {
                  //  a.notifyProgress(i);
                }
            }

            finished = true;
            thread = null;
            pdfView.post(this);
        }
    }

    public void start() {
        if (finished) {
            return;
        }
        if (thread == null) {
            PDFView pdfView = this.pdoc.get();
            if (pdfView != null) {
                pdfView.startSearch(arr, key, flag);
            }
            thread = new Thread(this);
            thread.start();
        }
    }

    public void abort() {
        abort.set(true);
    }

    public boolean isAborted() {
        return abort.get();
    }
}
