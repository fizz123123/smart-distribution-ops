package com.smartdistribution.purchasing.service;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.smartdistribution.document.pdf.PdfRenderer;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.purchasing.entity.PurchaseOrder;
import com.smartdistribution.purchasing.entity.PurchaseOrderLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PO 正式 PDF 的版面：表頭、供應商、明細表與金額
 * 稅額與含稅總額只在這裡依 5% 算出來顯示，資料庫只存未稅金額
 */
@Component
@RequiredArgsConstructor
public class PurchaseOrderPdfLayout {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Taipei"));

    private final PdfRenderer pdfRenderer;
    private final PurchaseOrderMasterData purchaseOrderMasterData;

    public byte[] render(PurchaseOrder purchaseOrder, Supplier supplier, List<PurchaseOrderLine> lines) {
        Font title = pdfRenderer.boldFont(18);
        Font text = pdfRenderer.font(10);
        Font header = pdfRenderer.boldFont(10);

        return pdfRenderer.render(document -> {
            Paragraph heading = pdfRenderer.paragraph("採購單", title);
            heading.setAlignment(Element.ALIGN_CENTER);
            document.add(heading);

            document.add(pdfRenderer.paragraph(
                    "採購單號：" + purchaseOrder.getPoNumber()
                            + "　　核准日期：" + DATE_FORMAT.format(purchaseOrder.getApprovedAt()),
                    text
            ));
            document.add(pdfRenderer.paragraph(
                    "供應商：" + supplier.getSupplierCode() + " " + supplier.getName(),
                    text
            ));
            document.add(pdfRenderer.paragraph(
                    "聯絡人：" + orDash(supplier.getContactName()) + "　　電話：" + orDash(supplier.getPhone()),
                    text
            ));
            document.add(pdfRenderer.paragraph("地址：" + orDash(supplier.getAddress()), text));
            document.add(pdfRenderer.paragraph(
                    "預計到貨日：" + purchaseOrder.getExpectedArrivalDate(),
                    text
            ));

            document.add(linesTable(lines, header, text));

            BigDecimal untaxed = purchaseOrder.getUntaxedAmount();
            BigDecimal tax = PurchaseOrderAmounts.tax(untaxed);
            document.add(totalLine("未稅合計：" + money(untaxed), text));
            document.add(totalLine("稅額（5%）：" + money(tax), text));
            document.add(totalLine("含稅總額：" + money(untaxed.add(tax)), header));

            if (purchaseOrder.getRemark() != null) {
                document.add(pdfRenderer.paragraph("備註：" + purchaseOrder.getRemark(), text));
            }

            document.add(pdfRenderer.paragraph("本採購單由系統產生，經核准後生效。", pdfRenderer.font(8)));
        });
    }

    private PdfPTable linesTable(List<PurchaseOrderLine> lines, Font header, Font text) {
        PdfPTable table = pdfRenderer.table(new float[]{0.6f, 1.6f, 3f, 1.1f, 0.8f, 1.3f, 1.5f});

        addRow(table, header, "項次", "料號", "品名", "數量", "單位", "未稅單價", "未稅小計");

        for (PurchaseOrderLine line : lines) {
            //單張 PO 的明細有限，直接逐筆查料號
            CompanyItem item = purchaseOrderMasterData.findCompanyItem(line.getCompanyItemId());

            addRow(
                    table,
                    text,
                    String.valueOf(line.getLineNo()),
                    item.getItemCode(),
                    item.getName(),
                    line.getQuantity().stripTrailingZeros().toPlainString(),
                    line.getUnit().getDisplayName(),
                    price(line.getUnitPriceExcludingTax()),
                    price(line.getLineAmountExcludingTax())
            );
        }

        return table;
    }

    //前三欄文字靠左，其他數字靠右
    private void addRow(PdfPTable table, Font font, String... values) {
        for (int index = 0; index < values.length; index++) {
            int alignment = index < 3 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT;
            PdfPCell cell = pdfRenderer.cell(values[index], font, alignment);
            table.addCell(cell);
        }
    }

    private Paragraph totalLine(String content, Font font) {
        Paragraph paragraph = pdfRenderer.paragraph(content, font);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        return paragraph;
    }

    //單價與小計保留到小數 2 位，整數時不顯示小數
    private static String price(BigDecimal value) {
        return new DecimalFormat("#,##0.##").format(value);
    }

    private static String money(BigDecimal value) {
        return new DecimalFormat("#,##0").format(value);
    }

    private static String orDash(String value) {
        return value == null ? "－" : value;
    }
}
