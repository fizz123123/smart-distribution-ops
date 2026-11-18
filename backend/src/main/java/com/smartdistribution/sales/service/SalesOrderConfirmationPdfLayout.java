package com.smartdistribution.sales.service;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.smartdistribution.document.pdf.PdfRenderer;
import com.smartdistribution.masterdata.customer.entity.PaymentTerm;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse.Line;
import com.smartdistribution.sales.entity.DeliveryMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

/**
 * 訂單確認單的版面：給客戶確認訂單內容，金額是扣除已核准取消後的有效金額
 * 只印客戶需要知道的內容，不印預留、在途、出貨等內部資訊
 */
@Component
@RequiredArgsConstructor
public class SalesOrderConfirmationPdfLayout {

    private final PdfRenderer pdfRenderer;

    /**
     * @param salesOrder          SO 詳情，已含有效數量與有效金額
     * @param version             版次 ＝ 1 ＋ 已核准的取消申請張數
     * @param cancellationNumbers 造成變更的取消申請單號，依核准順序；第 1 版為空
     */
    public byte[] render(SalesOrderDetailResponse salesOrder, int version, List<String> cancellationNumbers) {
        Font title = pdfRenderer.boldFont(18);
        Font text = pdfRenderer.font(10);
        Font header = pdfRenderer.boldFont(10);

        return pdfRenderer.render(document -> {
            Paragraph heading = pdfRenderer.paragraph("訂單確認單", title);
            heading.setAlignment(Element.ALIGN_CENTER);
            document.add(heading);

            document.add(pdfRenderer.paragraph(
                    "訂單編號：" + salesOrder.soNumber()
                            + "　　版次：第 " + version + " 版"
                            + "　　訂單日期：" + salesOrder.orderDate(),
                    text
            ));
            document.add(pdfRenderer.paragraph(
                    "客戶：" + salesOrder.customer().customerCode() + " " + salesOrder.customer().name()
                            + "　　客戶訂單號：" + orDash(salesOrder.customerOrderNumber()),
                    text
            ));
            document.add(pdfRenderer.paragraph(
                    "交貨方式：" + deliveryMethodName(salesOrder.deliveryMethod())
                            + "　　付款條件：" + paymentTermName(salesOrder.paymentTerm()),
                    text
            ));
            if (salesOrder.deliveryMethod() == DeliveryMethod.DELIVERY) {
                document.add(pdfRenderer.paragraph("交貨地址：" + orDash(salesOrder.deliveryAddress()), text));
            }

            document.add(linesTable(salesOrder.lines(), header, text));

            document.add(totalLine("未稅合計：" + money(salesOrder.effectiveUntaxedAmount()), text));
            document.add(totalLine("稅額（5%）：" + money(salesOrder.effectiveTaxAmount()), text));
            document.add(totalLine("含稅總額：" + money(salesOrder.effectiveTotalAmount()), header));

            if (!cancellationNumbers.isEmpty()) {
                document.add(pdfRenderer.paragraph(
                        "備註：本版依取消申請 " + String.join("、", cancellationNumbers) + " 調整數量，本版取代先前版本。",
                        text
                ));
            }

            document.add(pdfRenderer.paragraph("本確認單由系統產生，內容如有疑問請與承辦業務聯繫。", pdfRenderer.font(8)));
        });
    }

    private PdfPTable linesTable(List<Line> lines, Font header, Font text) {
        PdfPTable table = pdfRenderer.table(new float[]{1.6f, 3f, 0.9f, 0.9f, 0.9f, 0.7f, 1.2f, 1.4f, 1.4f});

        addRow(table, header, "客戶料號", "品名", "訂購", "已取消", "有效", "單位", "未稅單價", "有效小計", "交期");

        for (Line line : lines) {
            addRow(
                    table,
                    text,
                    line.customerItemCode(),
                    line.itemName(),
                    quantity(line.quantity()),
                    quantity(line.cancelledQuantity()),
                    quantity(line.effectiveQuantity()),
                    line.unit().getDisplayName(),
                    price(line.unitPriceExcludingTax()),
                    price(line.effectiveLineAmount()),
                    String.valueOf(line.deliveryDate())
            );
        }

        return table;
    }

    //前兩欄文字靠左，最後一欄日期置中，其他數字靠右
    private void addRow(PdfPTable table, Font font, String... values) {
        for (int index = 0; index < values.length; index++) {
            int alignment = index < 2
                    ? Element.ALIGN_LEFT
                    : index == values.length - 1 ? Element.ALIGN_CENTER : Element.ALIGN_RIGHT;
            table.addCell(pdfRenderer.cell(values[index], font, alignment));
        }
    }

    private Paragraph totalLine(String content, Font font) {
        Paragraph paragraph = pdfRenderer.paragraph(content, font);
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        return paragraph;
    }

    private static String deliveryMethodName(DeliveryMethod method) {
        return method == DeliveryMethod.CUSTOMER_PICKUP ? "客戶自取" : "外送";
    }

    private static String paymentTermName(PaymentTerm paymentTerm) {
        return paymentTerm.getDays() == 0 ? "出貨當天付款" : "出貨後 " + paymentTerm.getDays() + " 天";
    }

    private static String quantity(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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
