package com.smartdistribution.document;

import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.smartdistribution.document.pdf.PdfRenderer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不需要啟動 Spring：直接產生一份含中文的 PDF，再讀回來確認內容
 */
class PdfRendererTest {

    private final PdfRenderer renderer = new PdfRenderer();

    @Test
    void rendersChineseTextWithEmbeddedFont() throws Exception {
        byte[] pdf = renderer.render(document -> {
            document.add(renderer.paragraph("採購單 PO-000001", renderer.boldFont(16)));

            PdfPTable table = renderer.table(new float[]{3, 1});
            table.addCell(renderer.cell("品名", renderer.boldFont(10), Element.ALIGN_LEFT));
            table.addCell(renderer.cell("數量", renderer.boldFont(10), Element.ALIGN_RIGHT));
            table.addCell(renderer.cell("PE 保溫管", renderer.font(10), Element.ALIGN_LEFT));
            table.addCell(renderer.cell("10", renderer.font(10), Element.ALIGN_RIGHT));
            document.add(table);
        });

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        //字型有內嵌進 PDF，對方電腦沒有這個字型也能正確顯示
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("NotoSansTC");

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);

            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("採購單", "PO-000001", "保溫管");
        } finally {
            reader.close();
        }
    }
}
