package com.wordapp.pfdview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.wordApp.pdfview.R;
import com.wordapp.pfdview.model.SearchRecord;
import com.wordapp.pfdview.model.SearchRecordItem;
import com.wordapp.pfdview.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * A View to paint PDF selections, [magnifier] and search highlights
 */
public class PDocSelection extends View {
    public boolean supressRecalcInval;
    PDFView pDocView;
    float drawableWidth = 60;
    float drawableHeight = 30;
    float drawableDeltaW = drawableWidth / 4;
    Paint rectPaint;
    Paint rectFramePaint;
    Paint rectHighlightPaint;

    /**
     * Small Canvas for magnifier.
     * {@link Canvas#clipPath ClipPath} fails if the canvas it too high. ( will never happen in this project. )
     * see <a href="https://issuetracker.google.com/issues/132402784">issuetracker</a>)
     */
    Canvas cc;
    Bitmap PageCache;
    BitmapDrawable PageCacheDrawable;

    Path magClipper;
    RectF magClipperR;
    float magFactor = 1.5f;
    int magW = 560;
    int magH = 280;
    /**
     * output image
     */
    Drawable frameDrawable;
    private float framew;
    private final PointF vCursorPos = new PointF();

    private final RectF tmpPosRct = new RectF();

    int currentSearchPointer = 0;
    PointerEvent pointerEvent = PointerEvent.NONE;

    enum PointerEvent {
        NEXT, PREVIOUS, NONE
    }

    //public PDocPageResultsProvider searchCtx;

    public PDocSelection(Context context) {
        super(context);
        init();
    }

    public PDocSelection(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PDocSelection(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        rectPaint = new Paint();
        //rectPaint.setColor(0x66109afe);
        rectPaint.setColor(0x665B75E5);
        //rectPaint.setColor(0xffffff00);
        rectHighlightPaint = new Paint();
        rectHighlightPaint.setColor(getResources().getColor(R.color.heightlight_color));
        rectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
        rectHighlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
        rectFramePaint = new Paint();
        rectFramePaint.setColor(0xccc7ab21);
        rectFramePaint.setStyle(Paint.Style.STROKE);
        rectFramePaint.setStrokeWidth(0.5f);
    }


    private void initMagnifier() {
        //setLayerType(LAYER_TYPE_NONE,null);
        cc = new Canvas(PageCache = Bitmap.createBitmap(magW, magH, Bitmap.Config.ARGB_8888));
        PageCacheDrawable = new BitmapDrawable(getResources(), PageCache);
        frameDrawable = getResources().getDrawable(R.drawable.frame);
        framew = getResources().getDimension(R.dimen.framew);
        magClipper = new Path();
        magClipperR = new RectF(PageCacheDrawable.getBounds());
        magClipper.reset();
        magClipperR.set(0, 0, magW, magH);
        magClipper.addRoundRect(magClipperR, framew + 5, framew + 5, Path.Direction.CW);
    }

    int rectPoolSize = 0;

    ArrayList<ArrayList<RectF>> rectPool = new ArrayList<>();

    ArrayList<RectF> magSelBucket = new ArrayList<>();

    public void resetSel() {
        //  CMN.Log("resetSel", pDocView.selPageSt, pDocView.selPageEd, pDocView.selStart, pDocView.selEnd);

        if (pDocView != null && pDocView.pdfFile != null && pDocView.hasSelection) {
            long tid = pDocView.dragPinchManager.loadText();
            if (pDocView.isNotCurrentPage(tid)) {
                return;
            }

            boolean b1 = pDocView.selPageEd < pDocView.selPageSt;
            if (b1) {
                pDocView.selPageEd = pDocView.selPageSt;
                pDocView.selPageSt = pDocView.selPageEd;
            } else {
                pDocView.selPageEd = pDocView.selPageEd;
                pDocView.selPageSt = pDocView.selPageSt;
            }
            if (b1 || pDocView.selPageEd == pDocView.selPageSt && pDocView.selEnd < pDocView.selStart) {
                pDocView.selStart = pDocView.selEnd;
                pDocView.selEnd = pDocView.selStart;
            } else {
                pDocView.selStart = pDocView.selStart;
                pDocView.selEnd = pDocView.selEnd;
            }
            int pageCount = pDocView.selPageEd - pDocView.selPageSt;
            int sz = rectPool.size();
            ArrayList<RectF> rectPagePool;
            for (int i = 0; i <= pageCount; i++) {
                if (i >= sz) {
                    rectPool.add(rectPagePool = new ArrayList<>());
                } else {
                    rectPagePool = rectPool.get(i);
                }
                int selSt = i == 0 ? pDocView.selStart : 0;
                int selEd = i == pageCount ? pDocView.selEnd : -1;
                // PDocument.PDocPage page = pDocView.pdfFile.mPDocPages[selPageSt + i];

                pDocView.dragPinchManager.getSelRects(rectPagePool, selSt, selEd);//+10
            }
            recalcHandles();
            rectPoolSize = pageCount + 1;
        } else {
            rectPoolSize = 0;
        }
        if (!supressRecalcInval) {
            invalidate();
        }
    }

    public void recalcHandles() {
        PDFView page = pDocView;
        long tid = page.dragPinchManager.prepareText();
        if (pDocView.isNotCurrentPage(tid)) {
            return;
        }
        float mappedX = -pDocView.getCurrentXOffset() + pDocView.dragPinchManager.lastX;
        float mappedY = -pDocView.getCurrentYOffset() + pDocView.dragPinchManager.lastY;
        int pageIndex = pDocView.pdfFile.getPageAtOffset(pDocView.isSwipeVertical() ? mappedY : mappedX, pDocView.getZoom());

        int st = pDocView.selStart;
        int ed = pDocView.selEnd;
        int dir = pDocView.selPageEd - pDocView.selPageSt;
        dir = (int) Math.signum(dir == 0 ? ed - st : dir);
        if (dir != 0) {
            String atext = page.dragPinchManager.allText;
            int len = atext.length();
            if (st >= 0 && st < len) {
                char c;
                while (((c = atext.charAt(st)) == '\r' || c == '\n') && st + dir >= 0 && st + dir < len) {
                    st += dir;
                }
            }
            page.getCharPos(pDocView.handleLeftPos, st);
            pDocView.lineHeightLeft = pDocView.handleLeftPos.height() / 2;
            page.getCharLoosePos(pDocView.handleLeftPos, st);

            page = pDocView;
            page.dragPinchManager.prepareText();
            atext = page.dragPinchManager.allText;
            len = atext.length();
            int delta = -1;
            if (ed >= 0 && ed < len) {
                char c;
                dir *= -1;
                while (((c = atext.charAt(ed)) == '\r' || c == '\n') && ed + dir >= 0 && ed + dir < len) {
                    delta = 0;
                    ed += dir;
                }
            }//"RectF(373.0, 405.0, 556.0, 434.0)"
            //CMN.Log("getCharPos", page.allText.substring(ed+delta, ed+delta+1));
            page.getCharPos(pDocView.handleRightPos, ed + delta);
            pDocView.lineHeightRight = pDocView.handleRightPos.height() / 2;
            page.getCharLoosePos(pDocView.handleRightPos, ed + delta);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (pDocView == null) {
            return;
        }
        try {
            RectF posRct = tmpPosRct;
            Matrix matrix = pDocView.matrix;

            if (pDocView.isSearching && pDocView.pdfFile != null) {
                // SearchRecord record =  pDocView.searchRecords.get(pDocView.getCurrentPage());
                ArrayList<SearchRecord> searchRecordList = getSearchRecords();

                if (searchRecordList.isEmpty()) {
                    Log.d("pointer_check", "search record is empty");
                    if (pointerEvent == PointerEvent.NEXT) {
                        checkNextAppearingSearch();
                    } else if (pointerEvent == PointerEvent.PREVIOUS) {
                        checkPrevAppearingSearch();
                    }
                } else {
                    for (SearchRecord record : searchRecordList) {
                        if (record != null) {
                            pDocView.getAllMatchOnPage(record);
                            record.currentPage = pDocView.currentPage;
                            int page = pDocView.currentPage;
                            ArrayList<SearchRecordItem> data = (ArrayList<SearchRecordItem>) record.data;
                            sortSearchItemsByRectPosition(data);
                            if (pointerEvent != PointerEvent.NONE) updatePage(data, page);
                            ArrayList<RectF> lineRectList = pDocView.mergeLineRects(Arrays.asList(data.get(currentSearchPointer).rects));
                            if (lineRectList != null) {
                                for (RectF lineRect : lineRectList) {
                                    pDocView.sourceToViewRectFFSearch(lineRect, posRct, page);
                                    canvas.drawRect(posRct, rectHighlightPaint);
                                }
                            }
                        } else {
                            Log.d("pointer_check", "search record is null");
                            if (pointerEvent == PointerEvent.NEXT) {
                                checkNextAppearingSearch();
                            } else if (pointerEvent == PointerEvent.PREVIOUS) {
                                checkPrevAppearingSearch();
                            }
                        }
                    }
                }
            }

            if (pDocView.hasSelection && pDocView.pdfFile != null) {
                pDocView.sourceToViewRectFF(pDocView.handleLeftPos, posRct);
                float left = posRct.left + drawableDeltaW;
                pDocView.handleLeft.setBounds((int) (left - drawableWidth), (int) posRct.bottom, (int) left, (int) (posRct.bottom + drawableHeight));
                pDocView.handleLeft.draw(canvas);
                //canvas.drawRect(pDocView.handleLeft.getBounds(), rectPaint);

                pDocView.sourceToViewRectFF(pDocView.handleRightPos, posRct);
                left = posRct.right - drawableDeltaW;
                pDocView.handleRight.setBounds((int) left, (int) posRct.bottom, (int) (left + drawableWidth), (int) (posRct.bottom + drawableHeight));
                pDocView.handleRight.draw(canvas);

                // canvas.drawRect(pDocView.handleRight.getBounds(), rectPaint);
                pDocView.sourceToViewCoord(pDocView.sCursorPos, vCursorPos);

                for (int i = 0; i < rectPoolSize; i++) {

                    ArrayList<RectF> rectPage = rectPool.get(i);
                    for (RectF rI : rectPage) {
                        pDocView.sourceToViewRectFF(rI, posRct);
                        matrix.reset();
                        int bmWidth = (int) rI.width();
                        int bmHeight = (int) rI.height();
                        pDocView.setMatrixArray(pDocView.srcArray, 0, 0, bmWidth, 0, bmWidth, bmHeight, 0, bmHeight);
                        pDocView.setMatrixArray(pDocView.dstArray, posRct.left, posRct.top, posRct.right, posRct.top, posRct.right, posRct.bottom, posRct.left, posRct.bottom);

                        matrix.setPolyToPoly(pDocView.srcArray, 0, pDocView.dstArray, 0, 4);
                        matrix.postRotate(0, pDocView.getScreenWidth(), pDocView.getScreenHeight());

                        canvas.save();
                        canvas.concat(matrix);
                        posRct.set(0, 0, bmWidth, bmHeight);
                        canvas.drawRect(posRct, rectPaint);
                        canvas.restore();
                    }
                }

            }
        } catch (Exception e) {
            Log.e("PDF_TEXT_SELECTION", "onDraw: ", e);
        }
    }


    private ArrayList<SearchRecord> getSearchRecords() {
        ArrayList<SearchRecord> list = new ArrayList<>();
        int currentPage = pDocView.getCurrentPage();
        if (currentPage == pDocView.searchPage) {
            list.add(pDocView.searchRecords.get(pDocView.searchPage));
        }
        return list;
    }

    public void sortSearchItemsByRectPosition(ArrayList<SearchRecordItem> items) {
        Collections.sort(items, (r1, r2) -> {
            float y1 = r1.rects[0].top;
            float y2 = r2.rects[0].top;
            return Float.compare(y1, y2);
        });
    }

    public void moveNext() {
        pointerEvent = PointerEvent.NEXT;
        currentSearchPointer++;
        invalidate();
    }

    public void movePrev() {
        pointerEvent = PointerEvent.PREVIOUS;
        currentSearchPointer--;
        invalidate();
    }

    private void updatePage(ArrayList<SearchRecordItem> data, int currentPage) {
        Log.d("pointer_check", "data size:" + data.size() + " pointer:" + currentSearchPointer + " searchPage:" + pDocView.searchPage);
        if (data.size() <= currentSearchPointer) {
            checkNextAppearingSearch();
        } else if (currentSearchPointer < 0) {
            checkPrevAppearingSearch();
        }
    }

    private void checkNextAppearingSearch() {
        int currentPage = pDocView.getCurrentPage();
        int indexNext = currentPage + 1;
        while (Util.indexExists(pDocView.getPageCount(), indexNext)) {
            if (pDocView.searchRecords.containsKey(indexNext)) {
                SearchRecord searchRecordNext = pDocView.searchRecords.get(indexNext);
                if (searchRecordNext != null) {
                    currentSearchPointer = 0;
                    pDocView.jumpTo(indexNext);
                    pDocView.searchPage = indexNext;
                    invalidate();
                    break;
                }
            }
            ++indexNext;
        }
    }


    private void checkPrevAppearingSearch() {
        int currentPage = pDocView.getCurrentPage();
        int indexPrev = currentPage - 1;
        while (Util.indexExists(pDocView.getPageCount(), indexPrev)) {
            if (pDocView.searchRecords.containsKey(indexPrev)) {
                SearchRecord searchRecordNext = pDocView.searchRecords.get(indexPrev);
                if (searchRecordNext != null) {
                    currentSearchPointer = ((ArrayList<?>) (searchRecordNext.data)).size() - 1;
                    pDocView.jumpTo(indexPrev);
                    pDocView.searchPage = indexPrev;
                    invalidate();
                    break;
                }
            }
            --indexPrev;
        }
    }

    public void resetPointer() {
        currentSearchPointer = 0;
    }
}
