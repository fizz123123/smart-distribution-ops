package com.smartdistribution.document.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 產生 PDF 的共用工具：A4 版面、內嵌中文字型，以及段落、表格的固定樣式
 * 各種單據的版面（例如 PO）由各自的類別呼叫這裡組出內容
 */
@Component
public class PdfRenderer {

    //Noto Sans TC（SIL Open Font License），授權檔放在同一個資料夾
    private static final String FONT_PATH = "fonts/NotoSansTC-Regular.ttf";

    private final BaseFont baseFont;

    public PdfRenderer() {
        this.baseFont = loadChineseFont();
    }

    /**
     * 產生一份 A4 的 PDF
     *
     * @param content 往文件裡加入段落、表格等內容
     * @return PDF 檔案內容
     */
    public byte[] render(PdfContent content) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 50, 50);

        try {
            PdfWriter.getInstance(document, output);
            document.open();
            content.write(document);
        } catch (Exception exception) {
            throw new IllegalStateException("產生 PDF 失敗", exception);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }

        return output.toByteArray();
    }

    public Font font(float size) {
        return new Font(baseFont, size);
    }

    public Font boldFont(float size) {
        return new Font(baseFont, size, Font.BOLD);
    }

    public Paragraph paragraph(String text, Font font) {
        return new Paragraph(text, font);
    }

    //寬度佔滿版面；relativeWidths 是各欄的相對寬度，例如 {1, 4, 2}
    public PdfPTable table(float[] relativeWidths) {
        PdfPTable table = new PdfPTable(relativeWidths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setSpacingAfter(8);
        return table;
    }

    /**
     * 表格的一格
     *
     * @param alignment 水平對齊，例如 Element.ALIGN_RIGHT
     */
    public PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }

    private BaseFont loadChineseFont() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(FONT_PATH)) {
            if (input == null) {
                throw new IllegalStateException("找不到中文字型：" + FONT_PATH);
            }

            //IDENTITY_H＋EMBEDDED：以 Unicode 寫入文字，PDF 只內嵌用到的字
            return BaseFont.createFont(
                    "NotoSansTC-Regular.ttf",
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED,
                    false,
                    input.readAllBytes(),
                    null
            );
        } catch (IOException exception) {
            throw new IllegalStateException("無法載入中文字型 " + FONT_PATH, exception);
        }
    }

    /**
     * 往 PDF 裡加入內容；允許丟出 OpenPDF 的例外，由 render 統一處理
     */
    @FunctionalInterface
    public interface PdfContent {

        void write(Document document) throws Exception;
    }
}
